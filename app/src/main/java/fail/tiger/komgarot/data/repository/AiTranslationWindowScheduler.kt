package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import fail.tiger.komgarot.data.remote.AiTranslationRequestResult
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AiRuntimeHeapSnapshot(
    val maxBytes: Long,
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long = (totalBytes - freeBytes).coerceAtLeast(0L)
    val headroomBytes: Long = (maxBytes - usedBytes).coerceAtLeast(0L)
}

internal fun currentAiRuntimeHeapSnapshot(runtime: Runtime = Runtime.getRuntime()): AiRuntimeHeapSnapshot =
    AiRuntimeHeapSnapshot(
        maxBytes = runtime.maxMemory(),
        totalBytes = runtime.totalMemory(),
        freeBytes = runtime.freeMemory()
    )

internal fun estimatedAiRequestWorkingBytes(
    imageTransport: AiImageTransport,
    pageContextPayloadBytes: Long
): Long {
    val contextBytes = pageContextPayloadBytes.coerceAtLeast(0L)
    return when (imageTransport) {
        AiImageTransport.BASE64 -> AI_BASE64_REQUEST_FIXED_BYTES + contextBytes * AI_BASE64_REQUEST_COPY_FACTOR
        AiImageTransport.IMAGE_URL -> AI_IMAGE_URL_REQUEST_FIXED_BYTES + min(contextBytes, AI_IMAGE_URL_CONTEXT_COUNTED_BYTES)
    }.coerceAtLeast(1L)
}

internal fun effectiveAiTranslationMemoryConcurrency(
    configuredLimit: Int,
    imageTransport: AiImageTransport,
    pageContextPayloadBytes: Long,
    heap: AiRuntimeHeapSnapshot
): Int {
    val configured = configuredLimit.coerceIn(1, AI_TRANSLATION_MAX_CONFIGURED_CONCURRENCY)
    val perRequestBytes = estimatedAiRequestWorkingBytes(imageTransport, pageContextPayloadBytes)
    val usableHeadroom = (heap.headroomBytes - AI_TRANSLATION_HEAP_RESERVE_BYTES).coerceAtLeast(perRequestBytes)
    val requestBudget = (usableHeadroom * AI_TRANSLATION_HEAP_BUDGET_PERCENT / 100L).coerceAtLeast(perRequestBytes)
    return (requestBudget / perRequestBytes)
        .coerceIn(1L, configured.toLong())
        .toInt()
}

internal data class AiTranslationConcurrencySnapshot(
    val configuredLimit: Int,
    val currentLimit: Int,
    val initialLimit: Int,
    val peakActiveRequests: Int,
    val downshiftCount: Int,
    val crossPageConcurrencyStarted: Boolean
)

internal class AiTranslationWindowScheduler(
    pageIndexes: List<Int>,
    configuredLimit: Int,
    private val secondaryPageLimit: Int = 2,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private data class PageState(
        var ready: Boolean = false,
        var firstRegionVisible: Boolean = false,
        var completed: Boolean = false
    )

    private sealed interface AcquireDecision {
        data object Granted : AcquireDecision
        data class Cooldown(val delayMs: Long) : AcquireDecision
        data class Changed(val version: Long) : AcquireDecision
    }

    private val mutex = Mutex()
    private val changes = MutableStateFlow(0L)
    private val orderedPages = pageIndexes.distinct().toMutableList()
    private val pageStates = orderedPages.associateWith { PageState() }.toMutableMap()
    private val pageMemoryLimits = mutableMapOf<Int, Int>()
    private val activeByPage = mutableMapOf<Int, Int>()
    private val configuredLimit = configuredLimit.coerceIn(1, AI_TRANSLATION_MAX_CONFIGURED_CONCURRENCY)
    private var serviceLimit = this.configuredLimit
    private var activeRequests = 0
    private var initialLimit = 0
    private var peakActiveRequests = 0
    private var downshiftCount = 0
    private var successStreak = 0
    private var cooldownUntilMs = 0L
    private var crossPageConcurrencyStarted = false

    suspend fun markPageReady(pageIndex: Int, memoryLimit: Int) {
        mutex.withLock {
            val state = pageStates.getOrPut(pageIndex) { PageState() }
            state.ready = true
            pageMemoryLimits[pageIndex] = memoryLimit.coerceIn(1, configuredLimit)
            signalChange()
        }
    }

    suspend fun markFirstRegionVisible(pageIndex: Int) {
        mutex.withLock {
            pageStates[pageIndex]?.firstRegionVisible = true
            signalChange()
        }
    }

    suspend fun markPageCompleted(pageIndex: Int) {
        mutex.withLock {
            pageStates[pageIndex]?.completed = true
            signalChange()
        }
    }

    suspend fun prioritizePage(pageIndex: Int): Boolean = mutex.withLock {
        val state = pageStates[pageIndex]
        if (state == null || state.completed) return@withLock false
        orderedPages.remove(pageIndex)
        orderedPages.add(0, pageIndex)
        signalChange()
        true
    }

    suspend fun execute(
        pageIndex: Int,
        block: suspend () -> AiTranslationRequestResult
    ): AiTranslationRequestResult = withPermit(pageIndex) {
        block().also { result -> recordFeedback(result) }
    }

    suspend fun <T> withPermit(
        pageIndex: Int,
        block: suspend () -> T
    ): T {
        acquire(pageIndex)
        return try {
            block()
        } finally {
            release(pageIndex)
        }
    }

    suspend fun snapshot(): AiTranslationConcurrencySnapshot = mutex.withLock {
        AiTranslationConcurrencySnapshot(
            configuredLimit = configuredLimit,
            currentLimit = effectiveLimit(primaryPage()),
            initialLimit = initialLimit.takeIf { it > 0 } ?: effectiveLimit(primaryPage()),
            peakActiveRequests = peakActiveRequests,
            downshiftCount = downshiftCount,
            crossPageConcurrencyStarted = crossPageConcurrencyStarted
        )
    }

    private suspend fun acquire(pageIndex: Int) {
        while (true) {
            val observedVersion = changes.value
            val decision = mutex.withLock {
                val now = clockMs()
                if (now < cooldownUntilMs) {
                    AcquireDecision.Cooldown(cooldownUntilMs - now)
                } else if (canAcquire(pageIndex)) {
                    activeRequests += 1
                    activeByPage[pageIndex] = activeByPage.getOrDefault(pageIndex, 0) + 1
                    val currentEffectiveLimit = effectiveLimit(primaryPage())
                    if (initialLimit == 0) initialLimit = currentEffectiveLimit
                    peakActiveRequests = maxOf(peakActiveRequests, activeRequests)
                    val primary = primaryPage()
                    if (primary != null && pageIndex != primary) crossPageConcurrencyStarted = true
                    AcquireDecision.Granted
                } else {
                    AcquireDecision.Changed(observedVersion)
                }
            }
            when (decision) {
                AcquireDecision.Granted -> return
                is AcquireDecision.Cooldown -> delay(decision.delayMs.coerceAtMost(AI_TRANSLATION_COOLDOWN_POLL_MS))
                is AcquireDecision.Changed -> changes.first { version -> version != decision.version }
            }
        }
    }

    private suspend fun release(pageIndex: Int) {
        mutex.withLock {
            activeRequests = (activeRequests - 1).coerceAtLeast(0)
            val pageActive = (activeByPage.getOrDefault(pageIndex, 0) - 1).coerceAtLeast(0)
            if (pageActive == 0) activeByPage.remove(pageIndex) else activeByPage[pageIndex] = pageActive
            signalChange()
        }
    }

    suspend fun recordFeedback(result: AiTranslationRequestResult) {
        mutex.withLock {
            when (result) {
                is AiTranslationRequestResult.Success -> {
                    if (clockMs() < cooldownUntilMs) {
                        successStreak = 0
                    } else {
                        successStreak += 1
                        val recoveryThreshold = maxOf(AI_TRANSLATION_SUCCESS_RECOVERY_MINIMUM, serviceLimit)
                        if (successStreak >= recoveryThreshold && serviceLimit < configuredLimit) {
                            serviceLimit += 1
                            successStreak = 0
                        }
                    }
                }
                is AiTranslationRequestResult.Failure -> when (result.category) {
                    AiTranslationErrorCategory.RATE_LIMITED -> {
                        serviceLimit = maxOf(1, serviceLimit / 2)
                        cooldownUntilMs = maxOf(
                            cooldownUntilMs,
                            clockMs() + (result.retryAfterMs ?: AI_TRANSLATION_DEFAULT_RATE_LIMIT_COOLDOWN_MS)
                        )
                        successStreak = 0
                        downshiftCount += 1
                    }
                    AiTranslationErrorCategory.SERVER_TEMPORARY,
                    AiTranslationErrorCategory.NETWORK_OR_API -> {
                        serviceLimit = maxOf(1, serviceLimit - 1)
                        successStreak = 0
                        downshiftCount += 1
                    }
                    AiTranslationErrorCategory.AUTHENTICATION,
                    AiTranslationErrorCategory.MODEL_CONFIGURATION,
                    AiTranslationErrorCategory.VISION_UNSUPPORTED,
                    AiTranslationErrorCategory.NON_JSON_RESPONSE,
                    AiTranslationErrorCategory.JSON_VALIDATION_FAILED -> Unit
                }
            }
            signalChange()
        }
    }

    private fun canAcquire(pageIndex: Int): Boolean {
        val state = pageStates[pageIndex] ?: return false
        if (!state.ready || state.completed) return false
        val primary = primaryPage() ?: return false
        val limit = effectiveLimit(primary)
        if (activeRequests >= limit) return false
        if (pageIndex == primary) {
            val secondary = secondaryPage(primary)
            val reservedForSecondary = if (
                pageStates[primary]?.firstRegionVisible == true &&
                secondary != null &&
                pageStates[secondary]?.ready == true
            ) {
                min(secondaryPageLimit, (limit - 1).coerceAtLeast(0))
            } else {
                0
            }
            return activeByPage.getOrDefault(primary, 0) < limit - reservedForSecondary
        }
        val secondary = secondaryPage(primary)
        return pageIndex == secondary &&
            pageStates[primary]?.firstRegionVisible == true &&
            activeByPage.getOrDefault(pageIndex, 0) < min(secondaryPageLimit, (limit - 1).coerceAtLeast(0))
    }

    private fun primaryPage(): Int? = orderedPages.firstOrNull { pageIndex ->
        pageStates[pageIndex]?.completed == false
    }

    private fun secondaryPage(primaryPage: Int): Int? {
        val primaryOffset = orderedPages.indexOf(primaryPage)
        return orderedPages.getOrNull(primaryOffset + 1)
    }

    private fun effectiveLimit(primaryPage: Int?): Int {
        val memoryLimit = primaryPage?.let { pageMemoryLimits[it] } ?: configuredLimit
        return min(serviceLimit, memoryLimit).coerceIn(1, configuredLimit)
    }

    private fun signalChange() {
        changes.value = changes.value + 1L
    }
}

internal const val AI_TRANSLATION_MAX_CONFIGURED_CONCURRENCY = 32
private const val AI_TRANSLATION_SUCCESS_RECOVERY_MINIMUM = 4
private const val AI_TRANSLATION_DEFAULT_RATE_LIMIT_COOLDOWN_MS = 500L
private const val AI_TRANSLATION_COOLDOWN_POLL_MS = 250L
private const val AI_TRANSLATION_HEAP_RESERVE_BYTES = 48L * 1024L * 1024L
private const val AI_TRANSLATION_HEAP_BUDGET_PERCENT = 55L
private const val AI_BASE64_REQUEST_FIXED_BYTES = 2L * 1024L * 1024L
private const val AI_BASE64_REQUEST_COPY_FACTOR = 3L
private const val AI_IMAGE_URL_REQUEST_FIXED_BYTES = 1L * 1024L * 1024L
private const val AI_IMAGE_URL_CONTEXT_COUNTED_BYTES = 256L * 1024L
