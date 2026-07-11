package fail.tiger.komgarot.ui.reader

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class ReaderSingleFlight<K, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private class Flight<V>(
        val deferred: CompletableDeferred<Result<V>>,
        var queuePriority: ReaderPageLoadPriority?
    ) {
        var waiters: Int = 0
        @Volatile var started: Boolean = false
        var jobGeneration: Int = 0
        var job: Job? = null
    }

    private val lock = Any()
    private val inFlight = ConcurrentHashMap<K, Flight<V>>()

    suspend fun run(key: K, loader: suspend () -> V): V =
        runQueued(key, executor = { work -> work() }, loader = loader)

    suspend fun runQueued(
        key: K,
        queuePriority: ReaderPageLoadPriority? = null,
        executor: suspend (work: suspend () -> V) -> V,
        loader: suspend () -> V
    ): V {
        var jobToStart: Job? = null
        val flight = synchronized(lock) {
            inFlight[key]?.also { existing ->
                existing.waiters += 1
                if (
                    queuePriority == ReaderPageLoadPriority.DISPLAY &&
                    existing.queuePriority == ReaderPageLoadPriority.PREFETCH &&
                    !existing.started
                ) {
                    existing.job?.cancel()
                    existing.queuePriority = queuePriority
                    existing.jobGeneration += 1
                    existing.job = createJob(
                        key,
                        existing,
                        existing.jobGeneration,
                        executor,
                        loader
                    ).also { jobToStart = it }
                }
            } ?: run {
                val deferred = CompletableDeferred<Result<V>>()
                val newFlight = Flight(deferred, queuePriority).apply {
                    waiters = 1
                    jobGeneration = 1
                }
                newFlight.job = createJob(
                    key,
                    newFlight,
                    newFlight.jobGeneration,
                    executor,
                    loader
                ).also { jobToStart = it }
                inFlight[key] = newFlight
                newFlight
            }
        }
        jobToStart?.start()
        return try {
            flight.deferred.await().getOrThrow()
        } finally {
            synchronized(lock) {
                flight.waiters -= 1
                if (flight.waiters == 0 && !flight.started && inFlight.remove(key, flight)) {
                    flight.job?.cancel()
                }
            }
        }
    }

    private fun createJob(
        key: K,
        flight: Flight<V>,
        generation: Int,
        executor: suspend (work: suspend () -> V) -> V,
        loader: suspend () -> V
    ): Job = scope.launch(start = CoroutineStart.LAZY) {
        val result = runCatching {
            executor {
                synchronized(lock) {
                    if (flight.jobGeneration != generation) throw CancellationException()
                    flight.started = true
                }
                loader()
            }
        }
        synchronized(lock) {
            if (flight.jobGeneration == generation) {
                flight.deferred.complete(result)
                inFlight.remove(key, flight)
            }
        }
    }
}

internal enum class ReaderPageLoadPriority { DISPLAY, PREFETCH }

internal class ReaderLoadPermitCoordinator(
    private val displayPermits: Semaphore = Semaphore(permits = 1),
    private val prefetchPermits: Semaphore = Semaphore(permits = 1)
) {
    suspend fun <T> withPermit(priority: ReaderPageLoadPriority, block: suspend () -> T): T {
        val permits = when (priority) {
            ReaderPageLoadPriority.DISPLAY -> displayPermits
            ReaderPageLoadPriority.PREFETCH -> prefetchPermits
        }
        return permits.withPermit { block() }
    }
}

private class ReaderPrioritizedExecutor {
    private val sequence = AtomicLong(0L)
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>()
    )

    fun execute(priority: ReaderPageLoadPriority, block: () -> Unit) {
        executor.execute(ReaderPrioritizedTask(priority, sequence.getAndIncrement(), block))
    }
}

private class ReaderPrioritizedTask(
    private val priority: ReaderPageLoadPriority,
    private val sequence: Long,
    private val block: () -> Unit
) : Runnable, Comparable<ReaderPrioritizedTask> {
    override fun run() = block()

    override fun compareTo(other: ReaderPrioritizedTask): Int {
        val priorityComparison = priority.ordinal.compareTo(other.priority.ordinal)
        return if (priorityComparison != 0) priorityComparison else sequence.compareTo(other.sequence)
    }
}

internal object ReaderPageLoadCoordinator {
    private val fileSingleFlight = ReaderSingleFlight<String, File?>()
    private val loadPermits = ReaderLoadPermitCoordinator()
    private val previewExecutor = ReaderPrioritizedExecutor()
    private val tileExecutor = Executors.newSingleThreadExecutor()

    suspend fun loadFile(
        key: String,
        priority: ReaderPageLoadPriority,
        loader: suspend () -> File?
    ): File? =
        fileSingleFlight.runQueued(
            key = key,
            queuePriority = priority,
            executor = { work -> loadPermits.withPermit(priority, work) },
            loader = loader
        )

    fun executePreview(priority: ReaderPageLoadPriority, block: () -> Unit) {
        previewExecutor.execute(priority, block)
    }

    fun executeTile(block: () -> Unit) {
        tileExecutor.execute(block)
    }
}
