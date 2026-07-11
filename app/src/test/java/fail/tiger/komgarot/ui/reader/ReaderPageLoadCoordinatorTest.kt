package fail.tiger.komgarot.ui.reader

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderPageLoadCoordinatorTest {
    @Test
    fun concurrentCallersShareOneLoader() = runBlocking {
        val singleFlight = ReaderSingleFlight<String, Int>()
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()
        val loaderCalls = AtomicInteger(0)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.run("page") {
                loaderCalls.incrementAndGet()
                loaderStarted.complete(Unit)
                releaseLoader.await()
                7
            }
        }
        loaderStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.run("page") {
                loaderCalls.incrementAndGet()
                9
            }
        }
        releaseLoader.complete(Unit)

        assertEquals(7, first.await())
        assertEquals(7, second.await())
        assertEquals(1, loaderCalls.get())
    }

    @Test
    fun completedLoadsCanRunAgain() = runBlocking {
        val singleFlight = ReaderSingleFlight<String, Int>()
        val loaderCalls = AtomicInteger(0)

        assertEquals(1, singleFlight.run("page") { loaderCalls.incrementAndGet() })
        assertEquals(2, singleFlight.run("page") { loaderCalls.incrementAndGet() })
    }

    @Test
    fun cancellingFirstCallerKeepsSharedLoaderAliveForFollower() = runBlocking {
        val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val singleFlight = ReaderSingleFlight<String, Int>(loaderScope)
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()

        val firstCaller = launch(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.run("page") {
                loaderStarted.complete(Unit)
                releaseLoader.await()
                11
            }
        }
        loaderStarted.await()
        val follower = async(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.run("page") { 13 }
        }

        firstCaller.cancelAndJoin()
        releaseLoader.complete(Unit)

        assertEquals(11, follower.await())
        loaderScope.cancel()
    }

    @Test
    fun displayLoadStartsWhilePrefetchChannelIsOccupied() = runBlocking {
        val permits = ReaderLoadPermitCoordinator()
        val prefetchStarted = CompletableDeferred<Unit>()
        val releasePrefetch = CompletableDeferred<Unit>()
        val displayStarted = CompletableDeferred<Unit>()

        val prefetch = launch {
            permits.withPermit(ReaderPageLoadPriority.PREFETCH) {
                prefetchStarted.complete(Unit)
                releasePrefetch.await()
            }
        }
        prefetchStarted.await()
        val display = launch {
            permits.withPermit(ReaderPageLoadPriority.DISPLAY) {
                displayStarted.complete(Unit)
            }
        }

        withTimeout(1_000) { displayStarted.await() }
        releasePrefetch.complete(Unit)
        prefetch.join()
        display.join()
    }

    @Test
    fun cancelledQueuedPrefetchNeverStarts() = runBlocking {
        val permits = ReaderLoadPermitCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val queuedCalls = AtomicInteger(0)

        val first = launch {
            permits.withPermit(ReaderPageLoadPriority.PREFETCH) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()
        val queued = launch(start = CoroutineStart.UNDISPATCHED) {
            permits.withPermit(ReaderPageLoadPriority.PREFETCH) {
                queuedCalls.incrementAndGet()
            }
        }
        queued.cancelAndJoin()
        releaseFirst.complete(Unit)
        first.join()

        assertEquals(0, queuedCalls.get())
    }

    @Test
    fun singleFlightCancelsQueuedLoaderAfterItsLastWaiterLeaves() = runBlocking {
        val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val singleFlight = ReaderSingleFlight<String, Int>(loaderScope)
        val enteredQueue = CompletableDeferred<Unit>()
        val releaseQueue = CompletableDeferred<Unit>()
        val loaderCalls = AtomicInteger(0)

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.runQueued(
                key = "queued-page",
                executor = { work ->
                    enteredQueue.complete(Unit)
                    releaseQueue.await()
                    work()
                },
                loader = { loaderCalls.incrementAndGet() }
            )
        }
        enteredQueue.await()
        caller.cancelAndJoin()
        releaseQueue.complete(Unit)
        yield()

        assertEquals(0, loaderCalls.get())
        loaderScope.cancel()
    }

    @Test
    fun startedLoaderKeepsPrefetchPermitAfterCallerCancellation() = runBlocking {
        val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val singleFlight = ReaderSingleFlight<String, Int>(loaderScope)
        val permits = ReaderLoadPermitCoordinator()
        val firstLoaderStarted = CompletableDeferred<Unit>()
        val releaseFirstLoader = CompletableDeferred<Unit>()
        val secondLoaderStarted = CompletableDeferred<Unit>()

        val firstCaller = launch(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.runQueued(
                key = "first",
                executor = { work -> permits.withPermit(ReaderPageLoadPriority.PREFETCH, work) },
                loader = {
                    firstLoaderStarted.complete(Unit)
                    releaseFirstLoader.await()
                    1
                }
            )
        }
        firstLoaderStarted.await()
        firstCaller.cancelAndJoin()
        val secondCaller = async {
            singleFlight.runQueued(
                key = "second",
                executor = { work -> permits.withPermit(ReaderPageLoadPriority.PREFETCH, work) },
                loader = {
                    secondLoaderStarted.complete(Unit)
                    2
                }
            )
        }
        yield()

        assertFalse(secondLoaderStarted.isCompleted)
        releaseFirstLoader.complete(Unit)
        secondLoaderStarted.await()
        assertEquals(2, secondCaller.await())
        loaderScope.cancel()
    }

    @Test
    fun displayCallerPromotesQueuedPrefetchFlight() = runBlocking {
        val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val singleFlight = ReaderSingleFlight<String, Int>(loaderScope)
        val permits = ReaderLoadPermitCoordinator()
        val prefetchPermitOccupied = CompletableDeferred<Unit>()
        val releasePrefetchPermit = CompletableDeferred<Unit>()
        val displayLoaderStarted = CompletableDeferred<Unit>()

        val blocker = launch {
            permits.withPermit(ReaderPageLoadPriority.PREFETCH) {
                prefetchPermitOccupied.complete(Unit)
                releasePrefetchPermit.await()
            }
        }
        prefetchPermitOccupied.await()
        val prefetchCaller = async(start = CoroutineStart.UNDISPATCHED) {
            singleFlight.runQueued(
                key = "target-page",
                queuePriority = ReaderPageLoadPriority.PREFETCH,
                executor = { work -> permits.withPermit(ReaderPageLoadPriority.PREFETCH, work) },
                loader = { 1 }
            )
        }
        val displayCaller = async {
            singleFlight.runQueued(
                key = "target-page",
                queuePriority = ReaderPageLoadPriority.DISPLAY,
                executor = { work -> permits.withPermit(ReaderPageLoadPriority.DISPLAY, work) },
                loader = {
                    displayLoaderStarted.complete(Unit)
                    2
                }
            )
        }

        withTimeout(1_000) { displayLoaderStarted.await() }
        assertEquals(2, prefetchCaller.await())
        assertEquals(2, displayCaller.await())
        releasePrefetchPermit.complete(Unit)
        blocker.join()
        loaderScope.cancel()
    }
}
