package fail.tiger.komgarot.data.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AiTranslationOperationGate {
    private data class BookState(
        val generation: AtomicLong = AtomicLong(0),
        val operationLock: Mutex = Mutex(),
        val operationJobs: MutableSet<Job> = linkedSetOf(),
        val trackedJobs: MutableSet<Job> = linkedSetOf()
    )

    private val monitor = Any()
    private val states = ConcurrentHashMap<String, BookState>()
    private val bookDeletionBarriers = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var allDeletionBarrier: CompletableDeferred<Unit>? = null

    suspend fun <T> runBookOperation(bookId: String, block: suspend (generation: Long) -> T): T {
        val state = states.getOrPut(bookId) { BookState() }
        while (true) {
            awaitDeletionBarrier(bookId)
            try {
                return state.operationLock.withLock {
                    synchronized(monitor) {
                        if (allDeletionBarrier != null || bookDeletionBarriers[bookId] != null) {
                            throw RetryAfterDeletionException
                        }
                    }
                    val job = currentCoroutineContext()[Job]
                    if (job != null) synchronized(monitor) { state.operationJobs += job }
                    try {
                        block(state.generation.get())
                    } finally {
                        if (job != null) synchronized(monitor) { state.operationJobs -= job }
                    }
                }
            } catch (_: RetryAfterDeletionException) {
                continue
            }
        }
    }

    suspend fun clearBook(bookId: String, delete: () -> Unit) {
        awaitGlobalDeletion()
        val state = states.getOrPut(bookId) { BookState() }
        val barrier = CompletableDeferred<Unit>()
        val existingBarrier: CompletableDeferred<Unit>? = synchronized(monitor) {
            bookDeletionBarriers[bookId]?.also { return@synchronized it }
            bookDeletionBarriers[bookId] = barrier
            state.generation.incrementAndGet()
            null
        }
        if (existingBarrier != null) {
            existingBarrier.await()
            return
        }
        try {
            cancelActiveJobs(state)
            state.operationLock.withLock { delete() }
        } finally {
            synchronized(monitor) { bookDeletionBarriers.remove(bookId) }
            barrier.complete(Unit)
        }
    }

    suspend fun clearAll(delete: () -> Unit) {
        val barrier = CompletableDeferred<Unit>()
        val existingBarrier: CompletableDeferred<Unit>? = synchronized(monitor) {
            allDeletionBarrier?.also { return@synchronized it }
            allDeletionBarrier = barrier
            states.values.forEach { it.generation.incrementAndGet() }
            null
        }
        if (existingBarrier != null) {
            existingBarrier.await()
            return
        }
        try {
            states.values.forEach { cancelActiveJobs(it) }
            delete()
        } finally {
            synchronized(monitor) { allDeletionBarrier = null }
            barrier.complete(Unit)
        }
    }

    fun isCurrent(bookId: String, generation: Long): Boolean =
        states.getOrPut(bookId) { BookState() }.generation.get() == generation

    fun hasActiveJobs(bookId: String): Boolean {
        val state = states[bookId] ?: return false
        return synchronized(monitor) {
            state.operationJobs.any { it.isActive } || state.trackedJobs.any { it.isActive }
        }
    }

    fun trackBookJob(bookId: String, job: Job) {
        val state = states.getOrPut(bookId) { BookState() }
        val blocked = synchronized(monitor) {
            if (allDeletionBarrier != null || bookDeletionBarriers[bookId] != null) {
                true
            } else {
                state.trackedJobs += job
                false
            }
        }
        if (blocked) {
            job.cancel()
            return
        }
        job.invokeOnCompletion {
            synchronized(monitor) { state.trackedJobs -= job }
        }
    }

    private suspend fun awaitDeletionBarrier(bookId: String) {
        while (true) {
            val barrier = synchronized(monitor) { allDeletionBarrier ?: bookDeletionBarriers[bookId] } ?: return
            barrier.await()
        }
    }

    private suspend fun awaitGlobalDeletion() {
        while (true) {
            val barrier = synchronized(monitor) { allDeletionBarrier } ?: return
            barrier.await()
        }
    }

    private suspend fun cancelActiveJobs(state: BookState) {
        val caller = currentCoroutineContext()[Job]
        val jobs = synchronized(monitor) {
            (state.operationJobs + state.trackedJobs).filter { it !== caller }.distinct()
        }
        jobs.forEach { it.cancelAndJoin() }
    }

    private data object RetryAfterDeletionException : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }
}
