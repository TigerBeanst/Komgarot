package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import fail.tiger.komgarot.data.remote.AiTranslationRequestResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationWindowSchedulerTest {
    @Test
    fun memoryLimitUsesHeapHeadroomAndTransportCost() {
        val highMemory = AiRuntimeHeapSnapshot(
            maxBytes = 768L * MIB,
            totalBytes = 256L * MIB,
            freeBytes = 128L * MIB
        )
        val lowHeadroom = AiRuntimeHeapSnapshot(
            maxBytes = 256L * MIB,
            totalBytes = 240L * MIB,
            freeBytes = 16L * MIB
        )
        val veryHighMemory = AiRuntimeHeapSnapshot(
            maxBytes = 2L * 1024L * MIB,
            totalBytes = 512L * MIB,
            freeBytes = 256L * MIB
        )

        assertEquals(
            8,
            effectiveAiTranslationMemoryConcurrency(
                configuredLimit = 8,
                imageTransport = AiImageTransport.BASE64,
                pageContextPayloadBytes = 512L * 1024L,
                heap = highMemory
            )
        )
        assertEquals(
            1,
            effectiveAiTranslationMemoryConcurrency(
                configuredLimit = 32,
                imageTransport = AiImageTransport.BASE64,
                pageContextPayloadBytes = 1024L * 1024L,
                heap = lowHeadroom
            )
        )
        assertEquals(
            32,
            effectiveAiTranslationMemoryConcurrency(
                configuredLimit = 32,
                imageTransport = AiImageTransport.IMAGE_URL,
                pageContextPayloadBytes = 128L,
                heap = veryHighMemory
            )
        )
    }

    @Test
    fun rateLimitHalvesWindowConcurrencyAndSuccessRecoversIt() = runBlocking {
        var nowMs = 0L
        val scheduler = AiTranslationWindowScheduler(
            pageIndexes = listOf(0),
            configuredLimit = 8,
            clockMs = { nowMs }
        )
        scheduler.markPageReady(pageIndex = 0, memoryLimit = 8)

        scheduler.recordFeedback(
            AiTranslationRequestResult.Failure(
                category = AiTranslationErrorCategory.RATE_LIMITED,
                summary = "rate limited",
                httpStatusCode = 429,
                retryAfterMs = 1_000L
            )
        )
        val downshifted = scheduler.snapshot()
        assertEquals(4, downshifted.currentLimit)
        assertEquals(1, downshifted.downshiftCount)

        repeat(4) {
            scheduler.recordFeedback(AiTranslationRequestResult.Success(normalizedJson = "{}"))
        }
        assertEquals(4, scheduler.snapshot().currentLimit)

        nowMs = 1_000L
        repeat(4) {
            scheduler.recordFeedback(AiTranslationRequestResult.Success(normalizedJson = "{}"))
        }
        assertEquals(5, scheduler.snapshot().currentLimit)
    }

    @Test
    fun secondaryPageWaitsForFirstRegionAndUsesTwoSlots() = runBlocking {
        val scheduler = AiTranslationWindowScheduler(pageIndexes = listOf(0, 1), configuredLimit = 8)
        scheduler.markPageReady(pageIndex = 0, memoryLimit = 8)
        scheduler.markPageReady(pageIndex = 1, memoryLimit = 8)
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val jobs = (0 until 3).map { requestIndex ->
            async {
                scheduler.execute(pageIndex = 1) {
                    started.send(requestIndex)
                    release.await()
                    AiTranslationRequestResult.Success(normalizedJson = "{}")
                }
            }
        }

        assertNull(withTimeoutOrNull(100L) { started.receive() })
        scheduler.markFirstRegionVisible(pageIndex = 0)
        withTimeout(1_000L) { started.receive() }
        withTimeout(1_000L) { started.receive() }
        assertNull(withTimeoutOrNull(100L) { started.receive() })

        release.complete(Unit)
        jobs.awaitAll()
        assertTrue(scheduler.snapshot().crossPageConcurrencyStarted)
    }

    @Test
    fun secondaryPageBecomesPrimaryAfterCurrentPageCompletes() = runBlocking {
        val scheduler = AiTranslationWindowScheduler(pageIndexes = listOf(0, 1), configuredLimit = 4)
        scheduler.markPageReady(pageIndex = 0, memoryLimit = 4)
        scheduler.markPageReady(pageIndex = 1, memoryLimit = 4)
        scheduler.markPageCompleted(pageIndex = 0)
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val jobs = (0 until 4).map { requestIndex ->
            async {
                scheduler.execute(pageIndex = 1) {
                    started.send(requestIndex)
                    release.await()
                    AiTranslationRequestResult.Success(normalizedJson = "{}")
                }
            }
        }

        repeat(4) { withTimeout(1_000L) { started.receive() } }
        release.complete(Unit)
        jobs.awaitAll()
        assertEquals(4, scheduler.snapshot().peakActiveRequests)
    }

    @Test
    fun prioritizingPageKeepsRunningRequestAndGrantsNextSlotToCurrentPage() = runBlocking {
        val scheduler = AiTranslationWindowScheduler(pageIndexes = listOf(0, 1), configuredLimit = 1)
        scheduler.markPageReady(pageIndex = 0, memoryLimit = 1)
        scheduler.markPageReady(pageIndex = 1, memoryLimit = 1)
        val started = Channel<Int>(Channel.UNLIMITED)
        val releaseOldPage = CompletableDeferred<Unit>()

        val oldPage = async {
            scheduler.execute(pageIndex = 0) {
                started.send(0)
                releaseOldPage.await()
                AiTranslationRequestResult.Success(normalizedJson = "{}")
            }
        }
        assertEquals(0, withTimeout(1_000L) { started.receive() })

        val currentPage = async {
            scheduler.execute(pageIndex = 1) {
                started.send(1)
                AiTranslationRequestResult.Success(normalizedJson = "{}")
            }
        }
        assertNull(withTimeoutOrNull(100L) { started.receive() })
        assertTrue(scheduler.prioritizePage(1))
        releaseOldPage.complete(Unit)

        assertEquals(1, withTimeout(1_000L) { started.receive() })
        oldPage.await()
        currentPage.await()
        Unit
    }

    @Test
    fun prioritizingPageChangesNextPreparationClaim() = runBlocking {
        val scheduler = AiTranslationWindowScheduler(pageIndexes = listOf(0, 1, 2), configuredLimit = 2)

        assertEquals(0, scheduler.claimNextPageForPreparation())
        assertTrue(scheduler.prioritizePage(2))
        assertEquals(2, scheduler.claimNextPageForPreparation())
        assertEquals(1, scheduler.claimNextPageForPreparation())
        assertNull(scheduler.claimNextPageForPreparation())
    }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
