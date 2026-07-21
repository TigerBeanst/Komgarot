package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fail.tiger.komgarot.data.local.AiImageMaxEdge
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiLocalModelSource
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSeriesSourceLanguageState
import fail.tiger.komgarot.data.local.AiSourceReadingDirection
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiBookTranslationMetadata
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationFailureCategory
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRegionStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationRequestMode
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AiTranslationTaskStatus
import fail.tiger.komgarot.data.local.AiTranslationTaskSummary
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.local.SecureAiSettingsStore
import fail.tiger.komgarot.data.local.aiSourceLanguageOnMetadataFailure
import fail.tiger.komgarot.data.local.normalizeAiSourceLanguageTag
import fail.tiger.komgarot.data.local.resolveAiSourceLanguageFromKomga
import fail.tiger.komgarot.data.remote.AiTranslationClient
import fail.tiger.komgarot.data.remote.AiTranslationImageInput
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import fail.tiger.komgarot.data.remote.AiTranslationRequestResult
import fail.tiger.komgarot.data.remote.AiTranslationUsage
import fail.tiger.komgarot.data.remote.aiTranslationSystemPrompt
import fail.tiger.komgarot.data.remote.aiTranslationUserPrompt
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto
import fail.tiger.komgarot.ui.reader.readerPageUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

data class AiTranslationTimingStep(
    val label: String,
    val durationMs: Long
)

data class AiTranslationPageTiming(
    val pageIndex: Int,
    val totalMs: Long,
    val steps: List<AiTranslationTimingStep>,
    val localDetectionStats: AiLocalDetectionStats? = null,
    val requestStats: AiTranslationRequestStats = AiTranslationRequestStats()
)

data class AiTranslationRequestStats(
    val regionCount: Int = 0,
    val requestCount: Int = 0,
    val retryCount: Int = 0,
    val firstRegionVisibleMs: Long? = null,
    val pageCompletedMs: Long = 0L,
    val usage: AiTranslationUsage = AiTranslationUsage(),
    val pageContextStrategy: String = "",
    val pageContextBytes: Int = 0,
    val configuredConcurrency: Int = 0,
    val initialConcurrency: Int = 0,
    val peakConcurrency: Int = 0,
    val concurrencyDownshiftCount: Int = 0,
    val crossPageConcurrencyStarted: Boolean = false
)

data class AiTranslationContextBenchmarkRecord(
    val strategy: String,
    val regionCount: Int,
    val contextBytes: Int,
    val firstRegionVisibleMs: Long?,
    val pageCompletedMs: Long,
    val requestCount: Int,
    val retryCount: Int,
    val usage: AiTranslationUsage,
    val configuredConcurrency: Int,
    val peakConcurrency: Int,
    val concurrencyDownshiftCount: Int,
    val manualQualityScore: Int?
)

fun AiTranslationPageTiming.toContextBenchmarkRecord(
    manualQualityScore: Int? = null
): AiTranslationContextBenchmarkRecord = AiTranslationContextBenchmarkRecord(
    strategy = requestStats.pageContextStrategy,
    regionCount = requestStats.regionCount,
    contextBytes = requestStats.pageContextBytes,
    firstRegionVisibleMs = requestStats.firstRegionVisibleMs,
    pageCompletedMs = requestStats.pageCompletedMs,
    requestCount = requestStats.requestCount,
    retryCount = requestStats.retryCount,
    usage = requestStats.usage,
    configuredConcurrency = requestStats.configuredConcurrency,
    peakConcurrency = requestStats.peakConcurrency,
    concurrencyDownshiftCount = requestStats.concurrencyDownshiftCount,
    manualQualityScore = manualQualityScore?.coerceIn(1, 5)
)

enum class AiPageContextStrategy(
    val storedValue: String,
    val maxEdge: Int
) {
    FULL_PAGE_512("full_page_512", 512),
    FULL_PAGE_768("full_page_768", 768),
    FULL_PAGE_1024("full_page_1024", 1024),
    LOCAL_PANEL_768("local_panel_768", 768)
}

class AiTranslationRepository(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val prefs: AuthPreferences,
    private val secureAiSettingsStore: SecureAiSettingsStore,
    private val store: AiTranslationStore,
    private val komgaHttpClient: OkHttpClient = OkHttpClient(),
    private val imageUploadHttpClient: OkHttpClient = OkHttpClient(),
    private val localTextDetector: AiLocalTextDetector = AiLocalTextDetector(),
    private val aiClient: AiTranslationClient = AiTranslationClient(),
    private val pageContextStrategy: AiPageContextStrategy = AiPageContextStrategy.FULL_PAGE_768
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bookTranslationQueue = Semaphore(1)
    private val operationGate = AiTranslationOperationGate()
    private val pageTimingStats = ConcurrentHashMap<String, AiTranslationPageTiming>()
    private val activeReaderSchedulers = ConcurrentHashMap<String, AiTranslationWindowScheduler>()
    private val readerPagePriorities = ConcurrentHashMap<String, Int>()

    fun readBookState(bookId: String): AiTranslatedBook? {
        if (!operationGate.hasActiveJobs(bookId)) store.recoverInterruptedPages(bookId)
        return store.readBook(bookId)
    }

    fun readPageTiming(bookId: String, pageIndex: Int): AiTranslationPageTiming? =
        pageTimingStats[aiTranslationTimingKey(bookId, pageIndex)]

    suspend fun clearBook(bookId: String) {
        operationGate.clearBook(bookId) {
            store.clearBook(bookId)
            pageTimingStats.keys.removeAll { it.startsWith("$bookId:") }
        }
    }

    suspend fun clearAllTranslations() {
        operationGate.clearAll {
            store.clearAll()
            pageTimingStats.clear()
        }
    }

    fun resetRunningPages(bookId: String, pageIndexes: List<Int>) {
        store.resetRunningPages(bookId, pageIndexes)
    }

    suspend fun prioritizeReaderPage(bookId: String, pageIndex: Int): Boolean {
        readerPagePriorities[bookId] = pageIndex
        return activeReaderSchedulers[bookId]?.prioritizePage(pageIndex) == true
    }

    suspend fun scanMissingBookTranslations(): AiTranslationPurgeScanResult = withContext(Dispatchers.IO) {
        scanAiTranslationPurgeCandidates(store.listBookIds()) { bookId ->
            bookRepository.getBookById(bookId)
        }
    }

    suspend fun purgeMissingBookTranslations(candidateBookIds: List<String>): AiTranslationPurgeResult =
        withContext(Dispatchers.IO) {
            val verified = scanAiTranslationPurgeCandidates(candidateBookIds) { bookId ->
                bookRepository.getBookById(bookId)
            }
            when (verified) {
                is AiTranslationPurgeScanResult.Aborted -> AiTranslationPurgeResult.Aborted(
                    checkedCount = verified.checkedCount,
                    reason = verified.reason,
                    detail = verified.detail
                )
                is AiTranslationPurgeScanResult.Ready -> {
                    verified.candidateBookIds.forEach { bookId -> clearBook(bookId) }
                    val existingBookIds = store.listBookIds().toSet()
                    val state = store.readTaskState()
                    store.saveTaskState(state.copy(tasks = state.tasks.filter { it.bookId in existingBookIds }))
                    AiTranslationPurgeResult.Completed(
                        checkedCount = verified.checkedCount,
                        removedCount = verified.candidateBookIds.size
                    )
                }
            }
        }

    fun preferredModeForBook(bookId: String): AiTranslationMode = AiTranslationMode.LOCAL_DETECTION

    fun setPreferredMode(book: BookDto, mode: AiTranslationMode) {
        val existing = store.readBook(book.id)
        if (existing == null) {
            ensureBookFile(book, book.media.pagesCount, AiTranslationMode.LOCAL_DETECTION)
            return
        }
        store.saveBookNow(
            existing.copy(
                translation = existing.translation.copy(
                    mode = AiTranslationMode.LOCAL_DETECTION.storedValue
                )
            )
        )
    }

    fun setTranslationDisplayEnabled(enabled: Boolean) {
        scope.launch {
            prefs.setAiTranslationDisplayMode(if (enabled) "on" else "off")
        }
    }

    fun startBookTranslation(book: BookDto, serverUrl: String) {
        launchTrackedBookJob(book.id) {
            updateTask(book, AiTranslationTaskStatus.QUEUED, (0 until book.media.pagesCount).toList())
            bookTranslationQueue.withPermit {
                val pages = bookRepository.getPages(book.id)
                ensureBookFile(book, pages.size, AiTranslationMode.LOCAL_DETECTION)
                val result = translatePages(
                    book,
                    serverUrl,
                    pages.indices.toList(),
                    force = false,
                    knownPages = pages,
                    onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
                )
                updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED)
            }
        }
    }

    fun retryIncompleteBookTranslation(book: BookDto, serverUrl: String) {
        launchTrackedBookJob(book.id) {
            updateTask(book, AiTranslationTaskStatus.QUEUED, (0 until book.media.pagesCount).toList())
            bookTranslationQueue.withPermit {
                val pages = bookRepository.getPages(book.id)
                ensureBookFile(book, pages.size, AiTranslationMode.LOCAL_DETECTION)
                val currentPages = store.readBook(book.id)?.pages.orEmpty()
                val completed = currentPages
                    .filter { it.status == AiTranslationPageStatus.DONE }
                    .map { it.pageIndex }
                    .toSet()
                val pending = pages.indices.filterNot { it in completed }
                val result = translatePages(
                    book,
                    serverUrl,
                    pending,
                    force = true,
                    knownPages = pages,
                    cachedPages = pages,
                    onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
                )
                updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED)
            }
        }
    }

    fun retryIncompleteBookTranslation(bookId: String, serverUrl: String) {
        launchTrackedBookJob(bookId) {
            val book = bookRepository.getBookById(bookId).getOrNull() ?: return@launchTrackedBookJob
            retryIncompleteBookTranslation(book, serverUrl)
        }
    }

    fun resumeTaskTranslation(bookId: String, serverUrl: String, pageIndexes: List<Int>) {
        launchTrackedBookJob(bookId) {
            val book = bookRepository.getBookById(bookId).getOrNull() ?: run {
                markTaskLaunchFailed(bookId, AiTranslationFailureCategory.PAGE_LIST)
                return@launchTrackedBookJob
            }
            val pages = runCatching { bookRepository.getPages(bookId) }.getOrNull() ?: run {
                markTaskLaunchFailed(bookId, AiTranslationFailureCategory.PAGE_LIST)
                return@launchTrackedBookJob
            }
            val targets = taskTranslationTargets(bookId, pageIndexes, pages.size)
            if (targets.isNotEmpty()) {
                resumePagesTranslation(
                    book = book,
                    serverUrl = serverUrl,
                    pageIndexes = targets,
                    cachedPages = pages,
                    remotePageConcurrencyCap = 2
                )
            }
        }
    }

    fun retryTaskTranslation(bookId: String, serverUrl: String, pageIndexes: List<Int>) {
        launchTrackedBookJob(bookId) {
            val book = bookRepository.getBookById(bookId).getOrNull() ?: run {
                markTaskLaunchFailed(bookId, AiTranslationFailureCategory.PAGE_LIST)
                return@launchTrackedBookJob
            }
            val pages = runCatching { bookRepository.getPages(bookId) }.getOrNull() ?: run {
                markTaskLaunchFailed(bookId, AiTranslationFailureCategory.PAGE_LIST)
                return@launchTrackedBookJob
            }
            val targets = taskTranslationTargets(bookId, pageIndexes, pages.size)
            if (targets.isNotEmpty()) {
                retryPagesTranslation(
                    book = book,
                    serverUrl = serverUrl,
                    pageIndexes = targets,
                    cachedPages = pages,
                    remotePageConcurrencyCap = 2
                )
            }
        }
    }

    private fun taskTranslationTargets(bookId: String, pageIndexes: List<Int>, pageCount: Int): List<Int> {
        val validRequested = pageIndexes.distinct().filter { it in 0 until pageCount }
        if (validRequested.isNotEmpty()) return validRequested
        return store.readBook(bookId)
            ?.pages
            .orEmpty()
            .filter { it.status != AiTranslationPageStatus.DONE }
            .map { it.pageIndex }
            .filter { it in 0 until pageCount }
    }

    private fun markTaskLaunchFailed(bookId: String, category: AiTranslationFailureCategory) {
        val state = store.readTaskState()
        store.saveTaskState(
            state.copy(
                tasks = state.tasks.map { task ->
                    if (task.bookId == bookId) {
                        task.copy(
                            status = AiTranslationTaskStatus.FAILED,
                            failureCategories = task.failureCategories + (category.storedValue to 1),
                            recoveryRequired = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        task
                    }
                }
            )
        )
    }

    private fun launchTrackedBookJob(bookId: String, block: suspend () -> Unit) {
        val job: Job = scope.launch(start = CoroutineStart.LAZY) { block() }
        operationGate.trackBookJob(bookId, job)
        job.start()
    }

    suspend fun retryPageTranslation(
        book: BookDto,
        serverUrl: String,
        pageIndex: Int,
        cachedPages: List<PageDto> = emptyList(),
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): AiTranslationPageActionResult = retryPagesTranslation(
        book = book,
        serverUrl = serverUrl,
        pageIndexes = listOf(pageIndex),
        cachedPages = cachedPages,
        onPageUpdated = onPageUpdated
    )

    suspend fun resumePagesTranslation(
        book: BookDto,
        serverUrl: String,
        pageIndexes: List<Int>,
        cachedPages: List<PageDto> = emptyList(),
        remotePageConcurrencyCap: Int? = null,
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): AiTranslationPageActionResult {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        resumeAiTranslationQueueForReader()
        ensureBookFile(book, book.media.pagesCount, runMode)
        updateTask(book, AiTranslationTaskStatus.QUEUED, pageIndexes)
        val result = bookTranslationQueue.withPermit {
            translatePages(
                book,
                serverUrl,
                pageIndexes,
                force = true,
                requireEnabled = false,
                knownPages = cachedPages,
                cachedPages = cachedPages,
                remotePageConcurrencyCap = remotePageConcurrencyCap,
                onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) },
                onPageUpdated = onPageUpdated
            )
        }
        updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED, pageIndexes)
        return AiTranslationPageActionResult(
            ok = result.ok,
            summary = result.summary.ifBlank { "AI translation resume failed: book=${book.id}, pages=${pageIndexes.joinToString(",")}, no repository diagnostic summary." }
        )
    }

    suspend fun retryPagesTranslation(
        book: BookDto,
        serverUrl: String,
        pageIndexes: List<Int>,
        cachedPages: List<PageDto> = emptyList(),
        remotePageConcurrencyCap: Int? = null,
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): AiTranslationPageActionResult {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        resumeAiTranslationQueueForReader()
        ensureBookFile(book, book.media.pagesCount, runMode)
        updateTask(book, AiTranslationTaskStatus.QUEUED, pageIndexes)
        val result = bookTranslationQueue.withPermit {
            pageIndexes.forEach { pageIndex -> deletePageTranslation(book.id, pageIndex) }
            translatePages(
                book,
                serverUrl,
                pageIndexes,
                force = true,
                requireEnabled = false,
                knownPages = cachedPages,
                cachedPages = cachedPages,
                remotePageConcurrencyCap = remotePageConcurrencyCap,
                onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) },
                onPageUpdated = onPageUpdated
            )
        }
        updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED, pageIndexes)
        return AiTranslationPageActionResult(
            ok = result.ok,
            summary = result.summary.ifBlank { "AI translation retry failed: book=${book.id}, pages=${pageIndexes.joinToString(",")}, no repository diagnostic summary." }
        )
    }

    fun deletePageTranslation(bookId: String, pageIndex: Int) {
        store.deletePage(bookId, pageIndex)
        pageTimingStats.remove(aiTranslationTimingKey(bookId, pageIndex))
    }

    suspend fun testPageTranslationConfiguration(
        book: BookDto,
        serverUrl: String,
        pageIndex: Int,
        cachedPages: List<PageDto> = emptyList()
    ): Boolean {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        ensureBookFile(book, book.media.pagesCount, runMode)
        val ok = bookTranslationQueue.withPermit {
            deletePageTranslation(book.id, pageIndex)
            translatePages(
                book,
                serverUrl,
                listOf(pageIndex),
                force = true,
                requireEnabled = false,
                knownPages = cachedPages,
                cachedPages = cachedPages,
                onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
            )
        }.ok
        if (ok) {
            prefs.setAiConfigurationTestPassed(true)
            prefs.setAiTestModeEnabled(false)
        }
        return ok
    }

    private suspend fun translatePages(
        book: BookDto,
        serverUrl: String,
        pageIndexes: List<Int>,
        force: Boolean,
        requireEnabled: Boolean = true,
        knownPages: List<PageDto> = emptyList(),
        cachedPages: List<PageDto> = emptyList(),
        remotePageConcurrencyCap: Int? = null,
        onPageTranslated: () -> Unit = {},
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): AiTranslationRunResult = operationGate.runBookOperation(book.id) { generation ->
        withContext(Dispatchers.IO) {
        val secure = secureAiSettingsStore.read()
        val settings = AiSettings.defaults(
            targetLocale = prefs.aiTargetLocale.first(),
            targetLanguageName = prefs.aiTargetLanguageName.first()
        ).copy(
            enabled = prefs.aiTranslationEnabled.first(),
            baseUrl = prefs.aiBaseUrl.first(),
            modelName = prefs.aiModelName.first(),
            preferredMode = AiTranslationMode.LOCAL_DETECTION,
            sourceTextProfile = prefs.aiSourceTextProfile.first(),
            localModelSource = prefs.aiLocalModelSource.first(),
            modelCollectionId = prefs.aiModelCollectionId.first(),
            modelRevision = prefs.aiModelRevision.first(),
            downloadLatestModel = prefs.aiDownloadLatestModel.first(),
            autoSelectDeviceTier = prefs.aiAutoSelectDeviceTier.first(),
            imageTransport = prefs.aiImageTransport.first(),
            requestMode = prefs.aiTranslationRequestMode.first(),
            pagesPerRequest = prefs.aiPagesPerRequest.first(),
            concurrentRequests = prefs.aiConcurrentRequests.first(),
            maxImagesPerRequest = prefs.aiMaxImagesPerRequest.first(),
            timeoutSeconds = prefs.aiTimeoutSeconds.first(),
            imageMaxEdge = prefs.aiImageMaxEdge.first(),
            customInstructions = prefs.aiCustomInstructions.first(),
            testModeEnabled = prefs.aiTestModeEnabled.first(),
            configurationTestPassed = prefs.aiConfigurationTestPassed.first()
        )
        if (requireEnabled && !settings.enabled) {
            return@withContext failRun(book.id, pageIndexes, "AI translation is disabled in settings.")
        }
        if (!settings.hasCompleteModelConfiguration(secure.apiKey)) {
            return@withContext failRun(book.id, pageIndexes, "AI model configuration is incomplete.")
        }
        val sourceLanguageSession = resolveAiSourceLanguageSession(book)

        val allPages = knownPages.takeIf { it.isNotEmpty() }
            ?: cachedPages.takeIf { it.isNotEmpty() }
            ?: runCatching { bookRepository.getPages(book.id) }.getOrElse { throwable ->
                return@withContext failRun(book.id, pageIndexes, "Failed to load page list: ${throwable.message.orEmpty()}")
            }
        val runMode = AiTranslationMode.LOCAL_DETECTION
        ensureBookFile(book, allPages.size, runMode)
        updateBookTranslationMetadata(book.id, settings, runMode, sourceLanguageSession.current())
        val existing = store.readBook(book.id)
        val pending = pageIndexes
            .filter { it in allPages.indices }
            .filter { index ->
                force || existing?.pages?.firstOrNull { it.pageIndex == index }?.status != AiTranslationPageStatus.DONE
            }
        if (pending.isEmpty()) {
            return@withContext if (force) {
                failRun(book.id, pageIndexes, "No page was queued for AI translation.")
            } else {
                AiTranslationRunResult(ok = pageIndexes.isNotEmpty())
            }
        }

        updateTask(book, AiTranslationTaskStatus.RUNNING, pageIndexes)
        store.markPagesRunning(book.id, pending, runMode)

        val results = translatePendingPagesInPageOrder(
            book = book,
            serverUrl = serverUrl,
            settings = settings,
            apiKey = secure.apiKey,
            s3Uploader = secure.s3ImageUrlConfigOrNull()?.let { AiS3ImageUploader(imageUploadHttpClient, it) },
            pending = pending,
            allPages = allPages,
            sourceLanguageSession = sourceLanguageSession,
            remotePageConcurrencyCap = remotePageConcurrencyCap,
            onPageTranslated = onPageTranslated,
            onPageUpdated = onPageUpdated
        )
        if (!operationGate.isCurrent(book.id, generation)) throw CancellationException("AI translation generation changed")
        updateBookTranslationMetadata(book.id, settings, runMode, sourceLanguageSession.current())
        val ok = results.all { it.ok }
        val summary = summarizeAiTranslationResults(pending, results)
        updateTask(book, if (ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED, pageIndexes)
        AiTranslationRunResult(ok = ok, summary = summary)
        }
    }

    private suspend fun translatePendingPagesInPageOrder(
        book: BookDto,
        serverUrl: String,
        settings: AiSettings,
        apiKey: String,
        s3Uploader: AiS3ImageUploader?,
        pending: List<Int>,
        allPages: List<PageDto>,
        sourceLanguageSession: AiSourceLanguageSession,
        remotePageConcurrencyCap: Int? = null,
        onPageTranslated: () -> Unit = {},
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): List<AiTranslationRunResult> = coroutineScope {
        val orderedPending = pending.distinct()
        val remoteWorkerCount = effectiveAiTranslationRemoteWorkerCount(
            settings = settings,
            pendingCount = orderedPending.size,
            concurrencyCap = remotePageConcurrencyCap
        )
        val preparationWorkerCount = effectiveAiTranslationPreparationWorkerCount()
        val configuredRequestLimit = if (settings.requestMode == AiTranslationRequestMode.SERIAL) {
            1
        } else {
            settings.concurrentRequests
        }
        val requestControl = AiTranslationRequestControl(
            scheduler = AiTranslationWindowScheduler(
                pageIndexes = orderedPending,
                configuredLimit = configuredRequestLimit,
                secondaryPageLimit = if (remoteWorkerCount > 1) AI_TRANSLATION_SECONDARY_PAGE_REQUEST_LIMIT else 0
            )
        )
        activeReaderSchedulers[book.id] = requestControl.scheduler
        try {
            readerPagePriorities[book.id]?.let { pageIndex ->
                requestControl.scheduler.prioritizePage(pageIndex)
            }
            val preparedPages = orderedPending.map { CompletableDeferred<PreparedAiPageResult>() }
            val nextPrepareOffset = AtomicInteger(0)
            val prepareJobs = (0 until preparationWorkerCount).map {
                async {
                    while (true) {
                        val offset = nextPrepareOffset.getAndIncrement()
                        if (offset >= orderedPending.size) break
                        awaitAiTranslationTaskResumed()
                        val pageIndex = orderedPending[offset]
                        val prepared = try {
                            PreparedAiPageResult.Prepared(
                                preparePageInput(
                                    book = book,
                                    serverUrl = serverUrl,
                                    settings = settings,
                                    s3Uploader = s3Uploader,
                                    pageIndex = pageIndex,
                                    pages = allPages,
                                    sourceLanguage = sourceLanguageSession.current(),
                                    onPageUpdated = onPageUpdated
                                )
                            )
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            PreparedAiPageResult.Failed(
                                failRun(
                                    book.id,
                                    listOf(pageIndex),
                                    "Failed to build page image input: ${throwable.message.orEmpty()}"
                                )
                            )
                        }
                        preparedPages[offset].complete(prepared)
                    }
                }
            }
            val remoteJobs = mutableListOf<kotlinx.coroutines.Deferred<AiTranslationRunResult>>()
            orderedPending.indices.forEach { offset ->
                when (val prepared = preparedPages[offset].await()) {
                    is PreparedAiPageResult.Failed -> {
                        remoteJobs += async {
                            requestControl.scheduler.markPageCompleted(orderedPending[offset])
                            onPageTranslated()
                            prepared.result
                        }
                    }
                    is PreparedAiPageResult.Prepared -> {
                        remoteJobs += async {
                            awaitAiTranslationTaskResumed()
                            val result = translatePreparedPage(
                                book = book,
                                settings = settings,
                                apiKey = apiKey,
                                prepared = listOf(prepared.input),
                                sourceLanguageSession = sourceLanguageSession,
                                requestControl = requestControl,
                                onPageUpdated = onPageUpdated
                            )
                            onPageTranslated()
                            result
                        }
                    }
                }
            }
            prepareJobs.awaitAll()
            remoteJobs.awaitAll()
        } finally {
            activeReaderSchedulers.remove(book.id, requestControl.scheduler)
            readerPagePriorities.remove(book.id)
        }
    }

    private fun ensureBookFile(book: BookDto, pageCount: Int, mode: AiTranslationMode) {
        val existing = store.readBook(book.id)
        if (existing != null) {
            if (existing.translation.mode == mode.storedValue) return
            store.saveBookNow(existing.copy(translation = existing.translation.copy(mode = mode.storedValue)))
            return
        }
        store.saveBookNow(
            AiTranslatedBook(
                bookId = book.id,
                seriesId = book.seriesId,
                title = book.metadata.title,
                pageCount = pageCount,
                translation = AiBookTranslationMetadata(
                targetLocale = "",
                targetLanguageName = "",
                model = "",
                mode = mode.storedValue
            ),
                pages = (0 until pageCount.coerceAtLeast(0)).map {
                    AiTranslatedPage(
                        pageIndex = it,
                        status = AiTranslationPageStatus.PENDING,
                        mode = mode.storedValue
                    )
                }
            )
        )
    }

    private fun updateBookTranslationMetadata(
        bookId: String,
        settings: AiSettings,
        mode: AiTranslationMode,
        sourceLanguage: AiSeriesSourceLanguageState
    ) {
        val existing = store.readBook(bookId) ?: return
        store.saveBookNow(
            existing.copy(
                translation = existing.translation.copy(
                    targetLocale = settings.targetLocale,
                    targetLanguageName = settings.targetLanguageName,
                    model = settings.modelName,
                    mode = mode.storedValue,
                    sourceTextProfile = sourceLanguage.sourceTextProfile.storedValue,
                    sourceLanguageCode = sourceLanguage.normalizedCode,
                    sourceLanguageOrigin = sourceLanguage.origin.storedValue,
                    sourceKomgaLanguage = sourceLanguage.rawKomgaValue,
                    sourceReadingDirection = sourceLanguage.readingDirection.storedValue
                )
            )
        )
    }

    private suspend fun resolveAiSourceLanguageSession(book: BookDto): AiSourceLanguageSession {
        val cachedState = store.readSeriesSourceLanguage(book.seriesId)
        val resolved = try {
            val metadata = withTimeoutOrNull(AI_SOURCE_LANGUAGE_METADATA_TIMEOUT_MS) {
                bookRepository.getSeriesMetadata(book.seriesId)
            }
            if (metadata == null) {
                aiSourceLanguageOnMetadataFailure(book.seriesId, cachedState)
            } else {
                resolveAiSourceLanguageFromKomga(
                    seriesId = book.seriesId,
                    rawLanguage = metadata.language,
                    rawReadingDirection = metadata.readingDirection,
                    cachedState = cachedState
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            aiSourceLanguageOnMetadataFailure(book.seriesId, cachedState)
        }
        store.saveSeriesSourceLanguage(resolved)
        return AiSourceLanguageSession(resolved, store)
    }

    private fun resumeAiTranslationQueueForReader() {
        val state = store.readTaskState()
        if (state.paused) store.saveTaskState(state.copy(paused = false))
    }

    private suspend fun translatePreparedPage(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        prepared: List<PreparedAiPageInput>,
        sourceLanguageSession: AiSourceLanguageSession,
        requestControl: AiTranslationRequestControl,
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): AiTranslationRunResult {
        try {
            val runMode = AiTranslationMode.LOCAL_DETECTION
            val pageIndexes = prepared.map { it.localContext.pageIndex }
            if (prepared.isEmpty()) {
                return failRun(book.id, pageIndexes, "No page image input was built.")
            }
            val localPageContexts = prepared.map { it.localContext }
            val detectedRegionCount = localPageContexts.sumOf { it.regions.size }
            if (detectedRegionCount <= 0) {
                return failRun(book.id, pageIndexes, localDetectionEmptyTextMessage(localPageContexts), runMode)
            }
            prepared.forEach { preparedPage ->
                val memoryLimit = effectiveAiTranslationPageRequestLimit(
                    settings = settings,
                    regionCount = preparedPage.localContext.regions.size,
                    pageContextPayloadBytes = preparedPage.pageImageInput.requestPayloadBytes(),
                    heap = currentAiRuntimeHeapSnapshot()
                )
                requestControl.scheduler.markPageReady(preparedPage.localContext.pageIndex, memoryLimit)
            }

            val translatedPages = mutableListOf<AiTranslatedPage>()
            prepared.forEach { preparedPage ->
                requestControl.stoppingFailure()?.let { failure ->
                    return failRun(
                        bookId = book.id,
                        pageIndexes = listOf(preparedPage.localContext.pageIndex),
                        summary = failure.summary,
                        mode = runMode,
                        remoteCategory = failure.category,
                        httpStatusCode = failure.httpStatusCode,
                        retryAfterMs = failure.retryAfterMs
                    )
                }
                val pageFragments = mutableListOf(preparedPage.initialPage)
                val pageFragmentsLock = Any()
                val initialBlocksByRegion = preparedPage.initialPage.blocks.associateBy { it.localRegionId }
                val remainingRegions = preparedPage.localContext.regions.filter { region ->
                    initialBlocksByRegion[region.id]?.regionStatus != AiTranslationRegionStatus.DONE
                }
                if (remainingRegions.isEmpty()) {
                    translatedPages += mergeTranslatedPageFragments(
                        localContext = preparedPage.localContext,
                        fragments = pageFragments,
                        mode = runMode
                    ) ?: emptyTranslatedPage(preparedPage.localContext, runMode)
                    return@forEach
                }
                val regionChunks = remainingRegions.chunked(regionImagesPerRequest(settings))
                val chunkResults = timedAiTranslationStep(preparedPage.timingRecorder, AI_TIMING_AI_REQUEST_BATCH) {
                    translatePreparedRegionChunks(
                        book = book,
                        settings = settings,
                        apiKey = apiKey,
                        runMode = runMode,
                        preparedPage = preparedPage,
                        sourceLanguageSession = sourceLanguageSession,
                        regionChunks = regionChunks,
                        requestControl = requestControl,
                        pageFragments = pageFragments,
                        pageFragmentsLock = pageFragmentsLock,
                        onPageUpdated = onPageUpdated
                    )
                }
                val failedChunk = chunkResults.filterIsInstance<PreparedRegionChunkResult.Failed>().firstOrNull()
                if (failedChunk != null) {
                    val pageFragmentsSnapshot = synchronized(pageFragmentsLock) {
                        pageFragments.toList()
                    }
                    val failedPartialPage = saveFailedPartialTranslatedPage(
                        bookId = book.id,
                        localContext = preparedPage.localContext,
                        runMode = runMode,
                        fragments = pageFragmentsSnapshot,
                        summary = failedChunk.summary,
                        category = aiTranslationFailureCategory(failedChunk.summary, failedChunk.category),
                        httpStatusCode = failedChunk.httpStatusCode,
                        retryAfterMs = failedChunk.retryAfterMs
                    )
                    if (failedPartialPage != null) onPageUpdated(failedPartialPage)
                    if (failedPartialPage == null) {
                        return failRun(
                            bookId = book.id,
                            pageIndexes = pageIndexes,
                            summary = failedChunk.summary,
                            mode = runMode,
                            remoteCategory = failedChunk.category,
                            httpStatusCode = failedChunk.httpStatusCode,
                            retryAfterMs = failedChunk.retryAfterMs
                        )
                    }
                    return AiTranslationRunResult(ok = false, summary = failedChunk.summary.take(1200))
                }
                val pageFragmentsSnapshot = synchronized(pageFragmentsLock) {
                    pageFragments.toList()
                }
                val mergedPage = mergeTranslatedPageFragments(
                    localContext = preparedPage.localContext,
                    fragments = pageFragmentsSnapshot,
                    mode = runMode
                ) ?: emptyTranslatedPage(preparedPage.localContext, runMode)
                translatedPages += mergedPage
            }
            val saveStartedAt = System.currentTimeMillis()
            val saveResult = try {
                store.upsertPages(book.id, translatedPages)
                translatedPages.forEach(onPageUpdated)
                verifySavedTranslatedPages(book.id, translatedPages.map { it.pageIndex })
            } finally {
                val saveDuration = System.currentTimeMillis() - saveStartedAt
                prepared.forEach { it.timingRecorder.add(AI_TIMING_SAVE_AND_VERIFY, saveDuration) }
            }
            return saveResult
        } finally {
            prepared.forEach { preparedPage ->
                requestControl.scheduler.markPageCompleted(preparedPage.localContext.pageIndex)
                preparedPage.timingRecorder.setConcurrencyStats(requestControl.scheduler.snapshot())
                preparedPage.regionImageProvider.close()
                recordAiTranslationTiming(book.id, preparedPage.localContext.pageIndex, preparedPage.timingRecorder)
            }
        }
    }

    private suspend fun translatePreparedRegionChunks(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        runMode: AiTranslationMode,
        preparedPage: PreparedAiPageInput,
        sourceLanguageSession: AiSourceLanguageSession,
        regionChunks: List<List<AiTranslationLocalTextRegion>>,
        requestControl: AiTranslationRequestControl,
        pageFragments: MutableList<AiTranslatedPage>,
        pageFragmentsLock: Any,
        onPageUpdated: (AiTranslatedPage) -> Unit
    ): List<PreparedRegionChunkResult> =
        when (settings.requestMode) {
            AiTranslationRequestMode.SERIAL -> regionChunks.map { regionChunk ->
                translatePreparedRegionChunkAndSavePartial(
                    book = book,
                    settings = settings,
                    apiKey = apiKey,
                    runMode = runMode,
                    preparedPage = preparedPage,
                    sourceLanguageSession = sourceLanguageSession,
                    regionChunk = regionChunk,
                    requestControl = requestControl,
                    pageFragments = pageFragments,
                    pageFragmentsLock = pageFragmentsLock,
                    onPageUpdated = onPageUpdated
                )
            }
            AiTranslationRequestMode.PARALLEL -> coroutineScope {
                regionChunks.map { regionChunk ->
                    async {
                        translatePreparedRegionChunkAndSavePartial(
                            book = book,
                            settings = settings,
                            apiKey = apiKey,
                            runMode = runMode,
                            preparedPage = preparedPage,
                            sourceLanguageSession = sourceLanguageSession,
                            regionChunk = regionChunk,
                            requestControl = requestControl,
                            pageFragments = pageFragments,
                            pageFragmentsLock = pageFragmentsLock,
                            onPageUpdated = onPageUpdated
                        )
                    }
                }.awaitAll()
            }
        }

    private suspend fun translatePreparedRegionChunkAndSavePartial(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        runMode: AiTranslationMode,
        preparedPage: PreparedAiPageInput,
        sourceLanguageSession: AiSourceLanguageSession,
        regionChunk: List<AiTranslationLocalTextRegion>,
        requestControl: AiTranslationRequestControl,
        pageFragments: MutableList<AiTranslatedPage>,
        pageFragmentsLock: Any,
        onPageUpdated: (AiTranslatedPage) -> Unit
    ): PreparedRegionChunkResult {
        requestControl.stoppingFailure()?.let { return it }
        val runningPage = saveRunningRegionChunk(
            bookId = book.id,
            localContext = preparedPage.localContext,
            runMode = runMode,
            regionChunk = regionChunk,
            pageFragments = pageFragments,
            pageFragmentsLock = pageFragmentsLock
        )
        if (runningPage != null) onPageUpdated(runningPage)
        val chunkResult = translatePreparedRegionChunk(
            book = book,
            settings = settings,
            apiKey = apiKey,
            runMode = runMode,
            preparedPage = preparedPage,
            sourceLanguageSession = sourceLanguageSession,
            regionChunk = regionChunk,
            requestControl = requestControl
        )
        if (chunkResult is PreparedRegionChunkResult.Success) {
            val partialPage = savePartialTranslatedPageFragment(
                bookId = book.id,
                localContext = preparedPage.localContext,
                runMode = runMode,
                fragment = chunkResult.fragment,
                pageFragments = pageFragments,
                pageFragmentsLock = pageFragmentsLock
            )
            if (partialPage != null) {
                preparedPage.timingRecorder.recordFirstRegionVisible()
                requestControl.scheduler.markFirstRegionVisible(preparedPage.localContext.pageIndex)
                onPageUpdated(partialPage)
            }
        }
        if (chunkResult is PreparedRegionChunkResult.Failed && shouldStopNewAiRequests(chunkResult.category)) {
            requestControl.stop(chunkResult)
        }
        return chunkResult
    }

    private suspend fun translatePreparedRegionChunk(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        runMode: AiTranslationMode,
        preparedPage: PreparedAiPageInput,
        sourceLanguageSession: AiSourceLanguageSession,
        regionChunk: List<AiTranslationLocalTextRegion>,
        requestControl: AiTranslationRequestControl
    ): PreparedRegionChunkResult = requestControl.scheduler.withPermit(preparedPage.localContext.pageIndex) {
        translatePreparedRegionChunkWithPermit(
            book = book,
            settings = settings,
            apiKey = apiKey,
            runMode = runMode,
            preparedPage = preparedPage,
            sourceLanguageSession = sourceLanguageSession,
            regionChunk = regionChunk,
            requestControl = requestControl
        )
    }

    private suspend fun translatePreparedRegionChunkWithPermit(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        runMode: AiTranslationMode,
        preparedPage: PreparedAiPageInput,
        sourceLanguageSession: AiSourceLanguageSession,
        regionChunk: List<AiTranslationLocalTextRegion>,
        requestControl: AiTranslationRequestControl
    ): PreparedRegionChunkResult {
        requestControl.stoppingFailure()?.let { return it }
        awaitAiTranslationTaskResumed()
        val regionImages = timedAiTranslationStep(preparedPage.timingRecorder, AI_TIMING_REGION_CROP_IMAGES) {
            preparedPage.regionImageProvider.build(regionChunk)
        }
        if (regionImages.size != regionChunk.size) {
            return PreparedRegionChunkResult.Failed(
                summary = "Failed to build text-region crop images for page=${preparedPage.localContext.pageIndex}.",
                category = AiTranslationErrorCategory.JSON_VALIDATION_FAILED
            )
        }
        val chunkContext = preparedPage.localContext.copy(regions = regionChunk)
        val chunkImages = listOf(preparedPage.pageImageInput) + regionImages
        val result = timedAiTranslationStep(preparedPage.timingRecorder, AI_TIMING_AI_REQUEST) {
            translateRegionChunkImages(
                settings = settings,
                apiKey = apiKey,
                book = book,
                runMode = runMode,
                chunkContext = chunkContext,
                sourceLanguage = sourceLanguageSession.current(),
                images = chunkImages,
                timingRecorder = preparedPage.timingRecorder,
                requestControl = requestControl
            )
        }
        val finalResult = if (isRetryableImageUrlFetchFailure(result)) {
            awaitAiTranslationTaskResumed()
            timedAiTranslationStep(preparedPage.timingRecorder, AI_TIMING_AI_REQUEST) {
                translateRegionChunkImages(
                    settings = settings,
                    apiKey = apiKey,
                    book = book,
                    runMode = runMode,
                    chunkContext = chunkContext,
                    sourceLanguage = sourceLanguageSession.current(),
                    images = chunkImages.map { it.asBase64Fallback() },
                    timingRecorder = preparedPage.timingRecorder,
                    requestControl = requestControl
                )
            }
        } else {
            result
        }
        currentCoroutineContext().ensureActive()
        return when (finalResult) {
            is AiTranslationRequestResult.Success -> {
                timedAiTranslationStep(preparedPage.timingRecorder, AI_TIMING_AI_RESPONSE_PARSE) {
                    val parsedResponsePages = parseLocalRegionTranslationResponse(finalResult.normalizedJson)
                    if (parsedResponsePages.isEmpty()) {
                        PreparedRegionChunkResult.Failed(
                            summary = "AI response did not contain parsable page translation JSON for page=${preparedPage.localContext.pageIndex}.",
                            category = AiTranslationErrorCategory.JSON_VALIDATION_FAILED
                        )
                    } else {
                        parsedResponsePages
                            .flatMap(AiLocalRegionTranslationPage::translations)
                            .filter(::isEligibleAiSourceLanguageEvidence)
                            .forEach { translation ->
                                sourceLanguageSession.recordEvidence(translation.detectedSourceLanguage)
                            }
                        val pageFragment = translatedPagesFromLocalRegionResponse(
                            normalizedJson = finalResult.normalizedJson,
                            fallbackPageIndexes = listOf(preparedPage.localContext.pageIndex),
                            localPageContexts = listOf(chunkContext),
                            mode = runMode
                        ).firstOrNull()
                        if (pageFragment != null) {
                            PreparedRegionChunkResult.Success(pageFragment)
                        } else {
                            PreparedRegionChunkResult.Failed(
                                summary = "AI response did not bind a translation to region=${regionChunk.singleOrNull()?.id.orEmpty()}.",
                                category = AiTranslationErrorCategory.JSON_VALIDATION_FAILED
                            )
                        }
                    }
                }
            }
            is AiTranslationRequestResult.Failure -> {
                PreparedRegionChunkResult.Failed(
                    summary = "page=${preparedPage.localContext.pageIndex}: ${finalResult.summary}",
                    category = finalResult.category,
                    httpStatusCode = finalResult.httpStatusCode,
                    retryAfterMs = finalResult.retryAfterMs
                )
            }
        }
    }

    private suspend fun translateRegionChunkImages(
        settings: AiSettings,
        apiKey: String,
        book: BookDto,
        runMode: AiTranslationMode,
        chunkContext: AiTranslationLocalPageContext,
        sourceLanguage: AiSeriesSourceLanguageState,
        images: List<AiTranslationImageInput>,
        timingRecorder: AiTranslationTimingRecorder,
        requestControl: AiTranslationRequestControl
    ): AiTranslationRequestResult {
        var retryIndex = 0
        while (true) {
            val result = aiClient.translate(
                baseUrl = settings.baseUrl,
                apiKey = apiKey,
                model = settings.modelName,
                systemPrompt = aiTranslationSystemPrompt(),
                userPrompt = aiTranslationUserPrompt(
                    bookId = book.id,
                    targetLocale = settings.targetLocale,
                    targetLanguageName = settings.targetLanguageName,
                    translationMode = runMode,
                    localPageContexts = listOf(chunkContext),
                    customInstructions = settings.customInstructions,
                    sourceTextProfile = sourceLanguage.sourceTextProfile,
                    sourceLanguage = sourceLanguage
                ),
                images = images,
                timeoutSeconds = settings.timeoutSeconds
            )
            requestControl.scheduler.recordFeedback(result)
            timingRecorder.recordRequest(result)
            if (!isRetryableAiChunkFailure(result) || retryIndex >= AI_TRANSLATION_MAX_CHUNK_RETRIES) return result
            val failure = result as AiTranslationRequestResult.Failure
            timingRecorder.recordRetry()
            delay(aiTranslationRetryDelayMs(failure, retryIndex, Random.nextDouble()))
            retryIndex += 1
        }
    }

    private fun isRetryableImageUrlFetchFailure(result: AiTranslationRequestResult): Boolean {
        if (result !is AiTranslationRequestResult.Failure) return false
        val summary = result.summary.lowercase()
        return summary.contains("invalid_image_url") ||
            summary.contains("timeout while downloading") ||
            summary.contains("timed out while downloading") ||
            summary.contains("expired") ||
            (summary.contains("image") && summary.contains("403"))
    }

    private fun isRetryableAiChunkFailure(result: AiTranslationRequestResult): Boolean =
        result is AiTranslationRequestResult.Failure &&
            when (result.category) {
                AiTranslationErrorCategory.NETWORK_OR_API,
                AiTranslationErrorCategory.RATE_LIMITED,
                AiTranslationErrorCategory.SERVER_TEMPORARY -> true
                AiTranslationErrorCategory.AUTHENTICATION,
                AiTranslationErrorCategory.MODEL_CONFIGURATION,
                AiTranslationErrorCategory.VISION_UNSUPPORTED,
                AiTranslationErrorCategory.NON_JSON_RESPONSE,
                AiTranslationErrorCategory.JSON_VALIDATION_FAILED -> false
            } &&
            !isRetryableImageUrlFetchFailure(result)

    private fun savePartialTranslatedPageFragment(
        bookId: String,
        localContext: AiTranslationLocalPageContext,
        runMode: AiTranslationMode,
        fragment: AiTranslatedPage,
        pageFragments: MutableList<AiTranslatedPage>,
        pageFragmentsLock: Any
    ): AiTranslatedPage? {
        return synchronized(pageFragmentsLock) {
            pageFragments += fragment
            val partialPage = mergeTranslatedPageFragments(
                localContext = localContext,
                fragments = pageFragments.toList(),
                mode = runMode,
                status = AiTranslationPageStatus.RUNNING
            )
            if (partialPage != null) store.upsertPages(bookId, listOf(partialPage))
            partialPage
        }
    }

    private fun saveRunningRegionChunk(
        bookId: String,
        localContext: AiTranslationLocalPageContext,
        runMode: AiTranslationMode,
        regionChunk: List<AiTranslationLocalTextRegion>,
        pageFragments: MutableList<AiTranslatedPage>,
        pageFragmentsLock: Any
    ): AiTranslatedPage? = synchronized(pageFragmentsLock) {
        pageFragments += AiTranslatedPage(
            pageIndex = localContext.pageIndex,
            status = AiTranslationPageStatus.RUNNING,
            blocks = regionChunk.map { region ->
                localDetectionPlaceholderBlock(region, AiTranslationRegionStatus.RUNNING)
            },
            mode = runMode.storedValue
        )
        val runningPage = mergeTranslatedPageFragments(
            localContext = localContext,
            fragments = pageFragments.toList(),
            mode = runMode,
            status = AiTranslationPageStatus.RUNNING
        )
        if (runningPage != null) store.upsertPages(bookId, listOf(runningPage))
        runningPage
    }

    private fun saveFailedPartialTranslatedPage(
        bookId: String,
        localContext: AiTranslationLocalPageContext,
        runMode: AiTranslationMode,
        fragments: List<AiTranslatedPage>,
        summary: String,
        category: AiTranslationFailureCategory,
        httpStatusCode: Int? = null,
        retryAfterMs: Long? = null
    ): AiTranslatedPage? {
        val failedPage = mergeTranslatedPageFragments(
            localContext = localContext,
            fragments = fragments,
            mode = runMode,
            status = AiTranslationPageStatus.FAILED
        )?.copy(
            errorSummary = summary.take(1200),
            errorCategory = category.storedValue,
            errorHttpStatus = httpStatusCode,
            retryAfterMs = retryAfterMs
        )
        if (failedPage != null) {
            store.upsertPages(bookId, listOf(failedPage))
        }
        return failedPage
    }

    private fun localDetectionEmptyTextMessage(localPageContexts: List<AiTranslationLocalPageContext>): String {
        val pageIndexes = localPageContexts.joinToString(",") { it.pageIndex.toString() }.ifBlank { "empty" }
        return "Local text detection found zero text boxes for pages=$pageIndexes. Download or re-download local AI models in settings, then retry this page."
    }

    private fun saveTranslatedPages(
        bookId: String,
        normalizedJson: String,
        fallbackPageIndexes: List<Int>,
        mode: AiTranslationMode,
        localPageContexts: List<AiTranslationLocalPageContext> = emptyList()
    ): AiTranslationRunResult {
        val returnedPages = translatedPagesFromLocalRegionResponse(
            normalizedJson = normalizedJson,
            fallbackPageIndexes = fallbackPageIndexes,
            localPageContexts = localPageContexts,
            mode = mode
        )
        if (returnedPages.isEmpty()) {
            return failRun(bookId, fallbackPageIndexes, "AI response did not contain parsable page translation JSON.", mode)
        }
        store.upsertPages(bookId, returnedPages)
        return verifySavedTranslatedPages(bookId, fallbackPageIndexes)
    }

    private fun verifySavedTranslatedPages(bookId: String, pageIndexes: List<Int>): AiTranslationRunResult {
        val savedBook = store.readBook(bookId)
        val savedPages = savedBook?.pages.orEmpty()
        val failedPage = pageIndexes.firstOrNull { pageIndex ->
            savedPages.firstOrNull { it.pageIndex == pageIndex }?.status != AiTranslationPageStatus.DONE
        }
        if (failedPage == null) return AiTranslationRunResult(ok = true)

        val savedStatus = savedPages.firstOrNull { it.pageIndex == failedPage }?.status?.name ?: "missing"
        val file = store.bookFile(bookId)
        val rawPagesCount = store.rawBookPageCount(bookId)?.toString() ?: "unreadable"
        val rawBookState = store.rawBookState(bookId)
        val savedPageSummary = savedPages.joinToString(",") { "${it.pageIndex}:${it.status.name}" }.ifBlank { "empty" }
        return failRun(
            bookId,
            listOf(failedPage),
            "AI translation save verification failed: page=$failedPage, savedStatus=$savedStatus, book=$bookId, requestedPages=${pageIndexes.joinToString(",")}, savedPages=$savedPageSummary, rawPagesCount=$rawPagesCount, rawBookState=$rawBookState, fileExists=${file.isFile}, file=${file.absolutePath}",
            preferredModeForBook(bookId)
        )
    }

    private fun failRun(
        bookId: String,
        pageIndexes: List<Int>,
        summary: String,
        mode: AiTranslationMode = preferredModeForBook(bookId),
        remoteCategory: AiTranslationErrorCategory? = null,
        httpStatusCode: Int? = null,
        retryAfterMs: Long? = null
    ): AiTranslationRunResult {
        val safeSummary = summary.ifBlank { "AI translation failed without a detailed error." }
        val category = aiTranslationFailureCategory(safeSummary, remoteCategory)
        markPagesFailed(bookId, pageIndexes, safeSummary, mode, category, httpStatusCode, retryAfterMs)
        return AiTranslationRunResult(ok = false, summary = safeSummary.take(1200))
    }

    private fun summarizeAiTranslationResults(pageIndexes: List<Int>, results: List<AiTranslationRunResult>): String {
        val failed = results.filterNot { it.ok }
        val firstSummary = failed.firstOrNull()
            ?.summary
            ?.takeIf { it.isNotBlank() }
        return firstSummary ?: if (failed.isEmpty()) {
            ""
        } else {
            "AI translation batch failed: pages=${pageIndexes.joinToString(",")}, failedBatches=${failed.size}, totalBatches=${results.size}"
        }
    }

    private fun markPagesFailed(
        bookId: String,
        pageIndexes: List<Int>,
        summary: String,
        mode: AiTranslationMode,
        category: AiTranslationFailureCategory,
        httpStatusCode: Int? = null,
        retryAfterMs: Long? = null
    ) {
        val existingPages = store.readBook(bookId)?.pages.orEmpty().associateBy { it.pageIndex }
        store.upsertPages(
            bookId,
            pageIndexes.map { pageIndex ->
                val existing = existingPages[pageIndex]
                (existing ?: AiTranslatedPage(pageIndex = pageIndex)).copy(
                    status = AiTranslationPageStatus.FAILED,
                    blocks = existing
                        ?.blocks
                        .orEmpty()
                        .map { block ->
                            if (block.regionStatus == AiTranslationRegionStatus.DONE) {
                                block
                            } else {
                                block.copy(regionStatus = AiTranslationRegionStatus.FAILED)
                            }
                        },
                    errorSummary = summary.take(1200),
                    errorCategory = category.storedValue,
                    errorHttpStatus = httpStatusCode,
                    retryAfterMs = retryAfterMs,
                    mode = mode.storedValue,
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    private suspend fun preparePageInput(
        book: BookDto,
        serverUrl: String,
        settings: AiSettings,
        s3Uploader: AiS3ImageUploader?,
        pageIndex: Int,
        pages: List<PageDto>,
        sourceLanguage: AiSeriesSourceLanguageState,
        onPageUpdated: (AiTranslatedPage) -> Unit = {}
    ): PreparedAiPageInput {
        val mode = AiTranslationMode.LOCAL_DETECTION
        val bookId = book.id
        val timingRecorder = AiTranslationTimingRecorder(pageIndex)
        val page = pages.getOrNull(pageIndex) ?: error("page index out of bounds: $pageIndex")
        val url = readerPageUrl(serverUrl, bookId, page)
        val cachedPageFile = timedAiTranslationStep(timingRecorder, AI_TIMING_PAGE_IMAGE_CACHE) {
            ensureCachedPageFile(book.seriesId, bookId, url)
        }
        val sourceSettings = settings.copy(sourceTextProfile = sourceLanguage.sourceTextProfile)
        val localContextCacheKey = aiLocalContextCacheKey(cachedPageFile, sourceSettings, sourceLanguage)
        val cachedLocalContext = timedAiTranslationStep(timingRecorder, AI_TIMING_LOCAL_DETECTION_CACHE) {
            store.readLocalPageContext(bookId, pageIndex, localContextCacheKey)
        }
        val detectedLocalContext = cachedLocalContext ?: localTextDetector.detect(
                file = cachedPageFile,
                pageIndex = pageIndex,
                settings = sourceSettings,
                sourceLanguageTag = sourceLanguage.normalizedCode,
                onTimingStep = timingRecorder::add,
                onDetectionStats = timingRecorder::setLocalDetectionStats
            )
        val localContext = detectedLocalContext.copy(
            regions = detectedLocalContext.regions.sortedWith(
                localRegionReadingOrder(sourceLanguage.readingDirection)
            )
        )
        timingRecorder.setRegionCount(localContext.regions.size)
        currentCoroutineContext().ensureActive()
        if (cachedLocalContext == null) {
            timedAiTranslationStep(timingRecorder, AI_TIMING_LOCAL_DETECTION_CACHE) {
                store.saveLocalPageContext(bookId, pageIndex, localContextCacheKey, localContext)
            }
        }
        val existingPage = store.readBook(bookId)?.pages?.firstOrNull { it.pageIndex == pageIndex }
        val resumablePage = mergeLocalDetectionPageForRegionResume(localContext, existingPage, mode)
        store.upsertPages(bookId, listOf(resumablePage))
        onPageUpdated(resumablePage)
        val pageImageInput = timedAiTranslationStep(timingRecorder, AI_TIMING_PAGE_IMAGE_INPUT) {
            val compressed = compressPageContextImageForAi(
                file = cachedPageFile,
                regions = localContext.regions,
                strategy = pageContextStrategy
            )
            timingRecorder.recordPageContext(pageContextStrategy, compressed.bytes.size)
            imageInputFromBytes(
                bytes = compressed.bytes,
                pageIndex = pageIndex,
                mimeType = compressed.mimeType.takeIf { it.isNotBlank() } ?: page.mediaType,
                localRegionId = "",
                objectKey = s3Uploader?.objectKey(bookId, pageIndex, "page", "jpg"),
                imageTransport = settings.imageTransport,
                s3Uploader = s3Uploader
            )
        }
        return PreparedAiPageInput(
            pageImageInput = pageImageInput,
            localContext = localContext,
            regionImageProvider = AiRegionImageInputProvider(
                bookId = bookId,
                store = store,
                file = cachedPageFile,
                pageIndex = pageIndex,
                imageTransport = settings.imageTransport,
                s3Uploader = s3Uploader
            ),
            initialPage = resumablePage,
            timingRecorder = timingRecorder
        )
    }

    private fun ensureCachedPageFile(seriesId: String, bookId: String, url: String): File {
        ReaderPageCache.cachedFile(context, seriesId, bookId, url)?.let { return it }
        val entry = ReaderPageCache.entry(context, seriesId, bookId, url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/*,*/*;q=0.8")
            .build()
        try {
            komgaHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(response.message)
                val body = response.body ?: error("empty image body")
                entry.tempFile.parentFile?.mkdirs()
                entry.tempFile.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            if (!ReaderPageCache.commit(context, entry, prefs.readerCacheSizeBytesBlocking)) {
                error("failed to cache page image")
            }
            return ReaderPageCache.cachedFile(context, seriesId, bookId, url)
                ?: entry.file.takeIf { it.isFile && it.length() > 0L }
                ?: error("cached page image is missing")
        } catch (throwable: Throwable) {
            ReaderPageCache.discard(entry)
            throw throwable
        }
    }

    private fun updateTask(
        book: BookDto,
        status: AiTranslationTaskStatus,
        targetPageIndexes: List<Int> = emptyList()
    ) {
        val pages = store.readBook(book.id)?.pages.orEmpty()
        val state = store.readTaskState()
        val previous = state.tasks.firstOrNull { it.bookId == book.id }
        val validPageRange = 0 until book.media.pagesCount.coerceAtLeast(0)
        val targets = targetPageIndexes
            .distinct()
            .filter { it in validPageRange }
            .ifEmpty { previous?.targetPageIndexes.orEmpty().filter { it in validPageRange } }
            .ifEmpty { validPageRange.toList() }
        val targetSet = targets.toSet()
        val targetPages = pages.filter { it.pageIndex in targetSet }
        val completedPages = targetPages.count { it.status == AiTranslationPageStatus.DONE }
        val failedPages = targetPages.filter { it.status == AiTranslationPageStatus.FAILED }
        val displayStatus = if (state.paused && status == AiTranslationTaskStatus.RUNNING) {
            AiTranslationTaskStatus.PAUSED
        } else {
            status
        }
        val summary = AiTranslationTaskSummary(
            bookId = book.id,
            title = book.metadata.title,
            pageCount = targets.size,
            completedPages = completedPages,
            failedPages = failedPages.size,
            failureCategories = failedPages
                .groupingBy { it.errorCategory.ifBlank { AiTranslationFailureCategory.UNKNOWN.storedValue } }
                .eachCount()
                .filterKeys { it.isNotBlank() },
            targetPageIndexes = targets,
            recoveryRequired = false,
            status = displayStatus,
            updatedAt = System.currentTimeMillis()
        )
        store.saveTaskState(state.copy(tasks = state.tasks.filterNot { it.bookId == book.id } + summary))
    }

    private suspend fun awaitAiTranslationTaskResumed() {
        while (store.readTaskState().paused) {
            delay(AI_TRANSLATION_TASK_PAUSE_POLL_MS)
            yield()
        }
    }

    private data class PreparedAiPageInput(
        val pageImageInput: AiTranslationImageInput,
        val localContext: AiTranslationLocalPageContext,
        val regionImageProvider: AiRegionImageInputProvider,
        val initialPage: AiTranslatedPage,
        val timingRecorder: AiTranslationTimingRecorder
    )

    private sealed interface PreparedAiPageResult {
        data class Prepared(val input: PreparedAiPageInput) : PreparedAiPageResult
        data class Failed(val result: AiTranslationRunResult) : PreparedAiPageResult
    }

    private class AiTranslationTimingRecorder(private val pageIndex: Int) {
        private val startedAt = System.currentTimeMillis()
        private val steps = mutableListOf<AiTranslationTimingStep>()
        private var localDetectionStats: AiLocalDetectionStats? = null
        private var regionCount = 0
        private var requestCount = 0
        private var retryCount = 0
        private var firstRegionVisibleMs: Long? = null
        private var usage = AiTranslationUsage()
        private var pageContextStrategy = ""
        private var pageContextBytes = 0
        private var concurrencyStats = AiTranslationConcurrencySnapshot(
            configuredLimit = 0,
            currentLimit = 0,
            initialLimit = 0,
            peakActiveRequests = 0,
            downshiftCount = 0,
            crossPageConcurrencyStarted = false
        )

        @Synchronized
        fun add(label: String, durationMs: Long) {
            steps += AiTranslationTimingStep(label, durationMs.coerceAtLeast(0L))
        }

        @Synchronized
        fun setLocalDetectionStats(stats: AiLocalDetectionStats) {
            localDetectionStats = stats
        }

        @Synchronized
        fun setRegionCount(value: Int) {
            regionCount = value.coerceAtLeast(0)
        }

        @Synchronized
        fun recordPageContext(strategy: AiPageContextStrategy, byteCount: Int) {
            pageContextStrategy = strategy.storedValue
            pageContextBytes = byteCount.coerceAtLeast(0)
        }

        @Synchronized
        fun recordRequest(result: AiTranslationRequestResult) {
            requestCount += 1
            if (result is AiTranslationRequestResult.Success) usage += result.usage
        }

        @Synchronized
        fun recordRetry() {
            retryCount += 1
        }

        @Synchronized
        fun recordFirstRegionVisible() {
            if (firstRegionVisibleMs == null) {
                firstRegionVisibleMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            }
        }

        @Synchronized
        fun setConcurrencyStats(stats: AiTranslationConcurrencySnapshot) {
            concurrencyStats = stats
        }

        @Synchronized
        fun snapshot(): AiTranslationPageTiming =
            (System.currentTimeMillis() - startedAt).coerceAtLeast(0L).let { totalMs ->
                AiTranslationPageTiming(
                    pageIndex = pageIndex,
                    totalMs = totalMs,
                    steps = steps.toList(),
                    localDetectionStats = localDetectionStats,
                    requestStats = AiTranslationRequestStats(
                        regionCount = regionCount,
                        requestCount = requestCount,
                        retryCount = retryCount,
                        firstRegionVisibleMs = firstRegionVisibleMs,
                        pageCompletedMs = totalMs,
                        usage = usage,
                        pageContextStrategy = pageContextStrategy,
                        pageContextBytes = pageContextBytes,
                        configuredConcurrency = concurrencyStats.configuredLimit,
                        initialConcurrency = concurrencyStats.initialLimit,
                        peakConcurrency = concurrencyStats.peakActiveRequests,
                        concurrencyDownshiftCount = concurrencyStats.downshiftCount,
                        crossPageConcurrencyStarted = concurrencyStats.crossPageConcurrencyStarted
                    )
                )
            }
    }

    private suspend fun <T> timedAiTranslationStep(
        recorder: AiTranslationTimingRecorder,
        label: String,
        block: suspend () -> T
    ): T {
        val startedAt = System.currentTimeMillis()
        return try {
            block()
        } finally {
            recorder.add(label, System.currentTimeMillis() - startedAt)
        }
    }

    private fun recordAiTranslationTiming(
        bookId: String,
        pageIndex: Int,
        recorder: AiTranslationTimingRecorder
    ) {
        pageTimingStats[aiTranslationTimingKey(bookId, pageIndex)] = recorder.snapshot()
    }

    private sealed interface PreparedRegionChunkResult {
        data class Success(val fragment: AiTranslatedPage) : PreparedRegionChunkResult
        data class Failed(
            val summary: String,
            val category: AiTranslationErrorCategory? = null,
            val httpStatusCode: Int? = null,
            val retryAfterMs: Long? = null
        ) : PreparedRegionChunkResult
    }

    private class AiTranslationRequestControl(
        val scheduler: AiTranslationWindowScheduler
    ) {
        private val stoppingFailure = AtomicReference<PreparedRegionChunkResult.Failed?>(null)

        fun stop(failure: PreparedRegionChunkResult.Failed) {
            stoppingFailure.compareAndSet(null, failure)
        }

        fun stoppingFailure(): PreparedRegionChunkResult.Failed? = stoppingFailure.get()
    }
}

data class AiTranslationPageActionResult(
    val ok: Boolean,
    val summary: String = ""
)

internal fun aiTranslationFailureCategory(
    summary: String,
    remoteCategory: AiTranslationErrorCategory? = null
): AiTranslationFailureCategory {
    return when (remoteCategory) {
        AiTranslationErrorCategory.NETWORK_OR_API,
        AiTranslationErrorCategory.RATE_LIMITED,
        AiTranslationErrorCategory.SERVER_TEMPORARY -> AiTranslationFailureCategory.NETWORK_OR_API
        AiTranslationErrorCategory.AUTHENTICATION,
        AiTranslationErrorCategory.MODEL_CONFIGURATION -> AiTranslationFailureCategory.MODEL_CONFIGURATION
        AiTranslationErrorCategory.VISION_UNSUPPORTED -> AiTranslationFailureCategory.VISION_UNSUPPORTED
        AiTranslationErrorCategory.NON_JSON_RESPONSE -> AiTranslationFailureCategory.NON_JSON_RESPONSE
        AiTranslationErrorCategory.JSON_VALIDATION_FAILED -> AiTranslationFailureCategory.JSON_VALIDATION_FAILED
        null -> aiTranslationFailureCategoryFromSummary(summary)
    }
}

private fun aiTranslationFailureCategoryFromSummary(summary: String): AiTranslationFailureCategory {
    val text = summary.lowercase()
    return when {
        text.contains("disabled in settings") -> AiTranslationFailureCategory.SETTINGS
        text.contains("model configuration") -> AiTranslationFailureCategory.MODEL_CONFIGURATION
        text.contains("failed to load page list") -> AiTranslationFailureCategory.PAGE_LIST
        text.contains("failed to build page image input") -> AiTranslationFailureCategory.IMAGE_INPUT
        text.contains("no page image input") -> AiTranslationFailureCategory.IMAGE_INPUT
        text.contains("local text detection found zero text boxes") -> AiTranslationFailureCategory.LOCAL_TEXT_EMPTY
        text.contains("text-region crop") -> AiTranslationFailureCategory.REGION_CROP
        text.contains("did not contain parsable page translation json") -> AiTranslationFailureCategory.JSON_VALIDATION_FAILED
        text.contains("did not contain any translated text-region result") -> AiTranslationFailureCategory.EMPTY_AI_RESULT
        text.contains("did not contain translated text-region result") -> AiTranslationFailureCategory.EMPTY_AI_RESULT
        text.contains("save verification failed") -> AiTranslationFailureCategory.SAVE_VERIFICATION
        text.contains("timed out") -> AiTranslationFailureCategory.NETWORK_OR_API
        text.contains("http ") -> AiTranslationFailureCategory.NETWORK_OR_API
        text.contains("network") -> AiTranslationFailureCategory.NETWORK_OR_API
        else -> AiTranslationFailureCategory.UNKNOWN
    }
}

internal fun localDetectionPlaceholderPage(
    localContext: AiTranslationLocalPageContext,
    mode: AiTranslationMode
): AiTranslatedPage = AiTranslatedPage(
    pageIndex = localContext.pageIndex,
    status = AiTranslationPageStatus.RUNNING,
    updatedAt = System.currentTimeMillis(),
    imageWidth = localContext.imageWidth,
    imageHeight = localContext.imageHeight,
    mode = mode.storedValue,
    blocks = localContext.regions.map { region -> localDetectionPlaceholderBlock(region) }
)

private fun localDetectionPlaceholderBlock(
    region: AiTranslationLocalTextRegion,
    regionStatus: AiTranslationRegionStatus = AiTranslationRegionStatus.PENDING
): AiTranslationBlock =
    AiTranslationBlock(
        localRegionId = region.id,
        regionStatus = regionStatus,
        kind = AiTranslationBlockKind.OTHER,
        sourceText = "",
        translatedLines = emptyList(),
        rect = region.effectiveSourceMaskBounds(),
        translationRect = region.effectiveRenderBounds(),
        sourceColumns = region.effectiveSourceColumns(),
        textColor = ensureReadableAiTextColor(region.textColor, region.backgroundColor),
        maskColor = region.backgroundColor,
        maskAlpha = 0.55f,
        rotationDegrees = region.rotationDegrees,
        fontScale = region.estimatedFontScale,
        confidence = region.confidence,
        textDirection = region.textDirection
    )

internal fun mergeLocalDetectionPageForRegionResume(
    localContext: AiTranslationLocalPageContext,
    existingPage: AiTranslatedPage?,
    mode: AiTranslationMode
): AiTranslatedPage {
    val completedByRegion = existingPage
        ?.blocks
        .orEmpty()
        .filter { it.localRegionId.isNotBlank() && it.regionStatus == AiTranslationRegionStatus.DONE }
        .associateBy { it.localRegionId }
    return AiTranslatedPage(
        pageIndex = localContext.pageIndex,
        status = AiTranslationPageStatus.RUNNING,
        updatedAt = System.currentTimeMillis(),
        imageWidth = localContext.imageWidth,
        imageHeight = localContext.imageHeight,
        blocks = localContext.regions.map { region ->
            completedByRegion[region.id] ?: localDetectionPlaceholderBlock(region)
        },
        mode = mode.storedValue
    )
}

internal fun translatedPagesFromLocalRegionResponse(
    normalizedJson: String,
    fallbackPageIndexes: List<Int>,
    localPageContexts: List<AiTranslationLocalPageContext>,
    mode: AiTranslationMode
): List<AiTranslatedPage> {
    val responsePages = parseLocalRegionTranslationResponse(normalizedJson)
    val translationsByPage = alignLocalRegionTranslationPagesToRequestedIndexes(responsePages, fallbackPageIndexes)
        .associateBy { it.pageIndex }
    return localPageContexts.mapNotNull { context ->
        val responsePage = translationsByPage[context.pageIndex] ?: return@mapNotNull null
        buildTranslatedPageFromLocalContext(context, responsePage.translations, mode)
    }
}

internal fun buildTranslatedPageFromLocalContext(
    localContext: AiTranslationLocalPageContext,
    translations: List<AiLocalRegionTranslation>,
    mode: AiTranslationMode
): AiTranslatedPage? {
    val usableTranslations = translations
        .filter {
            it.translatedLines.any { line -> line.isNotBlank() } &&
                !isPureNumberAiTranslationSource(it.sourceText)
        }
    val translationsForRegions = if (localContext.regions.size == 1) {
        listOf(
            (usableTranslations.firstOrNull() ?: return null).copy(
                localRegionId = localContext.regions.single().id
            )
        )
    } else {
        usableTranslations
    }
    val translationsByRegion = translationsForRegions
        .filter {
            it.localRegionId.isNotBlank() &&
                it.translatedLines.any { line -> line.isNotBlank() }
        }
        .associateBy { it.localRegionId }
    val blocks = localContext.regions.mapNotNull { region ->
        val translation = translationsByRegion[region.id] ?: return@mapNotNull null
        AiTranslationBlock(
            localRegionId = region.id,
            regionStatus = AiTranslationRegionStatus.DONE,
            kind = translation.kind,
            sourceText = translation.sourceText,
            translatedLines = translation.translatedLines.map { it.trim() }.filter { it.isNotBlank() },
            rect = region.effectiveSourceMaskBounds(),
            translationRect = region.effectiveRenderBoundsForKind(translation.kind),
            sourceColumns = region.effectiveSourceColumns(),
            textColor = ensureReadableAiTextColor(region.textColor, region.backgroundColor),
            maskColor = region.backgroundColor,
            maskAlpha = 0.82f,
            cornerRadius = 0.04f,
            rotationDegrees = region.rotationDegrees,
            fontScale = region.estimatedFontScale,
            confidence = region.confidence,
            textDirection = region.textDirection
        ).withReadableColors()
    }
    if (blocks.isEmpty()) return null
    return AiTranslatedPage(
        pageIndex = localContext.pageIndex,
        status = AiTranslationPageStatus.DONE,
        updatedAt = System.currentTimeMillis(),
        imageWidth = localContext.imageWidth,
        imageHeight = localContext.imageHeight,
        blocks = blocks,
        mode = mode.storedValue
    )
}

internal fun isPureNumberAiTranslationSource(value: String): Boolean {
    val compact = value.trim()
        .replace(" ", "")
        .replace("\n", "")
        .replace("\t", "")
    if (compact.isBlank()) return false
    return compact.all { char ->
        char.isPureNumberAllowedCharacter()
    } && compact.any { it.isNumericValueCharacter() }
}

private fun Char.isPureNumberAllowedCharacter(): Boolean =
    isNumericValueCharacter() || code in PURE_NUMBER_SEPARATOR_CODE_POINTS

private fun Char.isNumericValueCharacter(): Boolean =
    isDigit() || code in 0xFF10..0xFF19 || code in CJK_NUMERIC_IDEOGRAPH_CODE_POINTS

private val CJK_NUMERIC_IDEOGRAPH_CODE_POINTS = setOf(
    0x3007,
    0x96F6,
    0x4E00,
    0x4E8C,
    0x4E09,
    0x56DB,
    0x4E94,
    0x516D,
    0x4E03,
    0x516B,
    0x4E5D,
    0x5341,
    0x767E,
    0x5343,
    0x4E07
)

private val PURE_NUMBER_SEPARATOR_CODE_POINTS = setOf(
    0x002C,
    0xFF0C,
    0x002E,
    0xFF0E,
    0x30FB,
    0x002F,
    0xFF0F,
    0x002D,
    0xFF0D,
    0x2014,
    0x2013,
    0x007E,
    0x301C
)

internal data class AiLocalRegionTranslationPage(
    val pageIndex: Int,
    val translations: List<AiLocalRegionTranslation>
)

internal data class AiLocalRegionTranslation(
    val localRegionId: String,
    val sourceText: String,
    val translatedLines: List<String>,
    val kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE,
    val detectedSourceLanguage: String = ""
)

internal fun parseLocalRegionTranslationResponse(text: String): List<AiLocalRegionTranslationPage> = runCatching {
    val root = JsonParser.parseString(text).asJsonObjectOrNull() ?: return@runCatching emptyList()
    val pages = root.jsonArrayOrNull("pages")?.toList()
        ?: root.takeIf { it.has("pageIndex") }?.let { listOf(it) }
        ?: emptyList()
    pages.mapNotNull { pageElement ->
        val page = pageElement.asJsonObjectOrNull() ?: return@mapNotNull null
        AiLocalRegionTranslationPage(
            pageIndex = page.intOrNull("pageIndex") ?: 0,
            translations = page.jsonArrayOrNull("translations")
                ?.mapNotNull(::parseLocalRegionTranslationElement)
                .orEmpty()
        )
    }
}.getOrDefault(emptyList())

private fun parseLocalRegionTranslationElement(element: JsonElement): AiLocalRegionTranslation? {
    val obj = element.asJsonObjectOrNull() ?: return null
    val localRegionId = obj.stringOrNull("localRegionId") ?: obj.stringOrNull("id").orEmpty()
    val translatedLines = obj.jsonArrayOrNull("translatedLines")
        ?.mapNotNull { it.asStringOrNull() }
        ?.filter { it.isNotBlank() }
        ?: obj.stringOrNull("translatedText")
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(it) }
        ?: emptyList()
    return AiLocalRegionTranslation(
        localRegionId = localRegionId,
        sourceText = obj.stringOrNull("sourceText").orEmpty(),
        translatedLines = translatedLines,
        kind = parseLocalRegionTranslationKind(obj.stringOrNull("kind")),
        detectedSourceLanguage = normalizeAiSourceLanguageTag(obj.stringOrNull("detectedSourceLanguage"))
    )
}

internal fun isEligibleAiSourceLanguageEvidence(translation: AiLocalRegionTranslation): Boolean =
    translation.kind in setOf(
        AiTranslationBlockKind.DIALOGUE,
        AiTranslationBlockKind.NARRATION,
        AiTranslationBlockKind.SIGN
    ) &&
        translation.sourceText.isNotBlank() &&
        !isPureNumberAiTranslationSource(translation.sourceText) &&
        translation.detectedSourceLanguage.isNotBlank()

private fun parseLocalRegionTranslationKind(value: String?): AiTranslationBlockKind =
    when (value?.uppercase()) {
        "NARRATION" -> AiTranslationBlockKind.NARRATION
        "SFX", "SOUND_EFFECT", "SOUND" -> AiTranslationBlockKind.SFX
        "SIGN", "CAPTION", "TITLE", "LABEL" -> AiTranslationBlockKind.SIGN
        "OTHER" -> AiTranslationBlockKind.OTHER
        else -> AiTranslationBlockKind.DIALOGUE
    }

private fun alignLocalRegionTranslationPagesToRequestedIndexes(
    pages: List<AiLocalRegionTranslationPage>,
    requestedPageIndexes: List<Int>
): List<AiLocalRegionTranslationPage> =
    if (pages.size == requestedPageIndexes.size) {
        pages.mapIndexed { index, page -> page.copy(pageIndex = requestedPageIndexes[index]) }
    } else {
        pages
    }

private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement.asStringOrNull(): String? =
    runCatching { takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

private fun JsonObject.jsonArrayOrNull(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.asStringOrNull()

private fun JsonObject.intOrNull(name: String): Int? =
    runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

private class AiSourceLanguageSession(
    initialState: AiSeriesSourceLanguageState,
    private val store: AiTranslationStore
) {
    @Volatile
    private var state: AiSeriesSourceLanguageState = initialState

    fun current(): AiSeriesSourceLanguageState = state

    fun recordEvidence(detectedSourceLanguage: String?) {
        synchronized(this) {
            val updated = state.recordAiEvidence(detectedSourceLanguage)
            if (updated != state) {
                state = updated
                store.saveSeriesSourceLanguage(updated)
            }
        }
    }
}

internal fun correctBlocksWithLocalRegions(
    blocks: List<AiTranslationBlock>,
    regions: List<AiTranslationLocalTextRegion>
): List<AiTranslationBlock> {
    if (blocks.isEmpty() || regions.isEmpty()) return blocks
    val regionsById = regions.associateBy { it.id }
    val corrected = mutableMapOf<Int, AiTranslationBlock>()
    val correctedIndexes = mutableSetOf<Int>()
    val usedRegionIds = mutableSetOf<String>()

    blocks.forEachIndexed { index, block ->
        val region = regionsById[block.localRegionId]
        if (region != null) {
            corrected[index] = block.correctWithLocalRegion(region)
            correctedIndexes += index
            usedRegionIds += region.id
        }
    }

    val remainingRegions = regions
        .filterNot { it.id in usedRegionIds }
        .sortedWith(localRegionReadingOrder())
    val remainingIndexes = blocks.indices.filterNot { it in correctedIndexes }
    if (remainingIndexes.isEmpty() || remainingRegions.isEmpty()) return blocks.correctedBlocks(corrected)

    val orderedRegions = regions.filterNot { it.id in usedRegionIds }.sortedWith(localRegionReadingOrder())
    val orderedIndexes = blocks.indices
        .filterNot { it in correctedIndexes }
    if (orderedIndexes.isEmpty() || orderedRegions.isEmpty()) return blocks.correctedBlocks(corrected)

    if (orderedIndexes.size <= orderedRegions.size) {
        orderedIndexes.zip(orderedRegions).forEach { (index, region) ->
            corrected[index] = blocks[index].correctWithLocalRegion(region)
        }
    } else {
        orderedIndexes.zip(orderedRegions).forEach { (index, region) ->
            corrected[index] = blocks[index].correctWithLocalRegion(region)
        }
    }
    return blocks.correctedBlocks(corrected)
}

private fun List<AiTranslationBlock>.correctedBlocks(corrected: Map<Int, AiTranslationBlock>): List<AiTranslationBlock> =
    indices.mapNotNull { corrected[it] }

internal fun correctPageWithLocalContext(
    page: AiTranslatedPage,
    mode: AiTranslationMode,
    localContext: AiTranslationLocalPageContext?
): AiTranslatedPage {
    val correctedBlocks = correctBlocksWithLocalRegions(page.blocks, localContext?.regions.orEmpty())
        .map { it.withReadableColors() }
    val localImageWidth = localContext?.imageWidth?.takeIf { it > 0 }
    val localImageHeight = localContext?.imageHeight?.takeIf { it > 0 }
    return page.copy(
        mode = mode.storedValue,
        imageWidth = localImageWidth ?: page.imageWidth.takeIf { it > 0 } ?: 0,
        imageHeight = localImageHeight ?: page.imageHeight.takeIf { it > 0 } ?: 0,
        blocks = correctedBlocks
    )
}

private fun mergeTranslatedPageFragments(
    localContext: AiTranslationLocalPageContext,
    fragments: List<AiTranslatedPage>,
    mode: AiTranslationMode,
    status: AiTranslationPageStatus = AiTranslationPageStatus.DONE
): AiTranslatedPage? {
    val blocksByRegion = fragments
        .flatMap { it.blocks }
        .filter { it.localRegionId.isNotBlank() }
        .associateBy { it.localRegionId }
    val orderedBlocks = localContext.regions.map { region ->
        val current = blocksByRegion[region.id] ?: localDetectionPlaceholderBlock(region)
        when (status) {
            AiTranslationPageStatus.DONE -> if (current.regionStatus == AiTranslationRegionStatus.DONE) {
                current
            } else {
                localDetectionPlaceholderBlock(region, AiTranslationRegionStatus.DONE)
            }
            AiTranslationPageStatus.FAILED -> if (current.regionStatus == AiTranslationRegionStatus.DONE) {
                current
            } else {
                current.copy(regionStatus = AiTranslationRegionStatus.FAILED)
            }
            AiTranslationPageStatus.PENDING -> if (current.regionStatus == AiTranslationRegionStatus.DONE) {
                current
            } else {
                current.copy(regionStatus = AiTranslationRegionStatus.PENDING)
            }
            AiTranslationPageStatus.RUNNING -> current
        }
    }
    if (orderedBlocks.isEmpty()) return null
    return AiTranslatedPage(
        pageIndex = localContext.pageIndex,
        status = status,
        updatedAt = System.currentTimeMillis(),
        imageWidth = localContext.imageWidth,
        imageHeight = localContext.imageHeight,
        blocks = orderedBlocks,
        mode = mode.storedValue
    )
}

private fun emptyTranslatedPage(
    localContext: AiTranslationLocalPageContext,
    mode: AiTranslationMode
): AiTranslatedPage = AiTranslatedPage(
    pageIndex = localContext.pageIndex,
    status = AiTranslationPageStatus.DONE,
    updatedAt = System.currentTimeMillis(),
    imageWidth = localContext.imageWidth,
    imageHeight = localContext.imageHeight,
    blocks = emptyList(),
    mode = mode.storedValue
)

@Suppress("DEPRECATION")
private class AiRegionImageInputProvider(
    private val bookId: String,
    private val store: AiTranslationStore,
    private val file: File,
    private val pageIndex: Int,
    private val imageTransport: AiImageTransport,
    private val s3Uploader: AiS3ImageUploader?
) : AutoCloseable {
    private val decoderMutex = Mutex()
    private val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }
    private var decoder: BitmapRegionDecoder? = null

    suspend fun build(regions: List<AiTranslationLocalTextRegion>): List<AiTranslationImageInput> {
        if (regions.isEmpty() || bounds.outWidth <= 0 || bounds.outHeight <= 0) return emptyList()
        return regions.mapNotNull { region ->
            yield()
            val cropRect = region.effectiveAiCropBounds().toAiCropRect(bounds.outWidth, bounds.outHeight)
                ?: return@mapNotNull null
            val cropCacheKey = aiRegionCropCacheKey(file, cropRect)
            val bytes = store.readRegionCrop(bookId, pageIndex, region.id, cropCacheKey)
                ?: decoderMutex.withLock {
                    store.readRegionCrop(bookId, pageIndex, region.id, cropCacheKey)
                        ?: decodeAndCacheRegion(region, cropRect, cropCacheKey)
                }
                ?: return@mapNotNull null
            imageInputFromBytes(
                bytes = bytes,
                pageIndex = pageIndex,
                mimeType = "image/jpeg",
                localRegionId = region.id,
                objectKey = s3Uploader?.objectKey(bookId, pageIndex, region.id, "jpg"),
                imageTransport = imageTransport,
                s3Uploader = s3Uploader
            )
        }
    }

    private fun decodeAndCacheRegion(
        region: AiTranslationLocalTextRegion,
        cropRect: Rect,
        cropCacheKey: String
    ): ByteArray? {
        val activeDecoder = decoder
            ?: BitmapRegionDecoder.newInstance(file.absolutePath, false)?.also { decoder = it }
            ?: return null
        val bitmap = activeDecoder.decodeRegion(
            cropRect,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        ) ?: return null
        return compressTextRegionCropBitmap(bitmap).also { compressed ->
            store.saveRegionCrop(bookId, pageIndex, region.id, cropCacheKey, compressed)
        }
    }

    override fun close() {
        decoder?.recycle()
        decoder = null
    }
}

private fun imageInputFromBytes(
    bytes: ByteArray,
    pageIndex: Int,
    mimeType: String,
    localRegionId: String,
    objectKey: String?,
    imageTransport: AiImageTransport,
    s3Uploader: AiS3ImageUploader?
): AiTranslationImageInput {
    if (imageTransport == AiImageTransport.BASE64) {
        return fallbackBase64Input(bytes, pageIndex, mimeType, localRegionId)
    }
    val uploader = s3Uploader ?: error("Image URL transport requires complete S3 image URL settings.")
    val key = objectKey ?: error("Image URL transport requires an S3 object key.")
    val imageUrl = runCatching { uploader.uploadImage(bytes, mimeType, key) }
        .getOrElse { throwable ->
            error("Image URL upload failed for page=$pageIndex localRegionId=${localRegionId.ifBlank { "page" }}: ${throwable.message.orEmpty()}")
        }
    return AiTranslationImageInput(
        pageIndex = pageIndex,
        transport = AiImageTransport.IMAGE_URL,
        mimeType = mimeType,
        base64 = "",
        imageUrl = imageUrl,
        localRegionId = localRegionId,
        fallbackBase64 = Base64.getEncoder().encodeToString(bytes)
    )
}

private fun AiTranslationImageInput.requestPayloadBytes(): Long = when (transport) {
    AiImageTransport.BASE64 -> base64.length.toLong()
    AiImageTransport.IMAGE_URL -> imageUrl.length.toLong()
}

private fun fallbackBase64Input(
    bytes: ByteArray,
    pageIndex: Int,
    mimeType: String,
    localRegionId: String = ""
): AiTranslationImageInput = AiTranslationImageInput(
    pageIndex = pageIndex,
    transport = AiImageTransport.BASE64,
    mimeType = mimeType,
    base64 = Base64.getEncoder().encodeToString(bytes),
    imageUrl = "",
    localRegionId = localRegionId
)

private fun AiTranslationRect.toAiCropRect(imageWidth: Int, imageHeight: Int): Rect? {
    if (imageWidth <= 0 || imageHeight <= 0 || width <= 0f || height <= 0f) return null
    val left = (x * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val top = (y * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
    val right = ((x + width) * imageWidth).roundToInt().coerceIn(left + 1, imageWidth)
    val bottom = ((y + height) * imageHeight).roundToInt().coerceIn(top + 1, imageHeight)
    val base = Rect(left, top, right, bottom)
    val pad = (min(base.width(), base.height()) * AI_REGION_CROP_PADDING_RATIO)
        .roundToInt()
        .coerceIn(AI_REGION_CROP_MIN_PADDING_PX, AI_REGION_CROP_MAX_PADDING_PX)
    return Rect(
        (base.left - pad).coerceAtLeast(0),
        (base.top - pad).coerceAtLeast(0),
        (base.right + pad).coerceAtMost(imageWidth),
        (base.bottom + pad).coerceAtMost(imageHeight)
    )
}

private fun compressTextRegionCropBitmap(bitmap: Bitmap): ByteArray {
    val scaled = bitmap.scaledForAiTextRegionCrop()
    return try {
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, AI_REGION_CROP_JPEG_QUALITY, output)
            output.toByteArray()
        }
    } finally {
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }
}

private fun Bitmap.scaledForAiTextRegionCrop(): Bitmap {
    val longest = max(width, height).coerceAtLeast(1)
    val shortest = min(width, height).coerceAtLeast(1)
    val minScale = AI_REGION_CROP_MIN_SHORT_EDGE / shortest.toFloat()
    val maxScale = AI_REGION_CROP_MAX_LONG_EDGE / longest.toFloat()
    val scale = min(max(minScale, 1f), maxScale).coerceIn(0.25f, 4f)
    if (scale in 0.98f..1.02f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

internal fun regionImagesPerRequest(settings: AiSettings): Int = 1

internal fun aiTranslationRetryDelayMs(
    failure: AiTranslationRequestResult.Failure,
    retryIndex: Int,
    jitterUnit: Double
): Long {
    failure.retryAfterMs?.let { return it.coerceAtLeast(0L) }
    val exponent = retryIndex.coerceIn(0, 6)
    val baseDelay = (AI_TRANSLATION_RETRY_BASE_DELAY_MS * (1L shl exponent))
        .coerceAtMost(AI_TRANSLATION_RETRY_MAX_DELAY_MS)
    val jitterFactor = 0.75 + jitterUnit.coerceIn(0.0, 1.0) * 0.50
    return (baseDelay * jitterFactor).roundToLong().coerceAtLeast(1L)
}

private fun shouldStopNewAiRequests(category: AiTranslationErrorCategory?): Boolean = when (category) {
    null,
    AiTranslationErrorCategory.RATE_LIMITED,
    AiTranslationErrorCategory.SERVER_TEMPORARY,
    AiTranslationErrorCategory.NETWORK_OR_API -> false
    AiTranslationErrorCategory.AUTHENTICATION,
    AiTranslationErrorCategory.MODEL_CONFIGURATION,
    AiTranslationErrorCategory.VISION_UNSUPPORTED,
    AiTranslationErrorCategory.NON_JSON_RESPONSE,
    AiTranslationErrorCategory.JSON_VALIDATION_FAILED -> true
}

internal fun effectiveAiTranslationWorkerCount(settings: AiSettings, pendingCount: Int, maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Int {
    val configured = settings.concurrentRequests.coerceAtLeast(1)
    val memoryCap = when {
        maxMemoryBytes < 256L * 1024L * 1024L -> 1
        maxMemoryBytes < 512L * 1024L * 1024L -> 2
        else -> configured
    }
    return min(configured, memoryCap).coerceAtMost(pendingCount.coerceAtLeast(1)).coerceAtLeast(1)
}

internal fun effectiveAiTranslationRemoteWorkerCount(
    settings: AiSettings,
    pendingCount: Int,
    concurrencyCap: Int?,
    maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()
): Int {
    val configuredWorkerCount = effectiveAiTranslationWorkerCount(settings, pendingCount, maxMemoryBytes)
    val callerCapped = concurrencyCap
        ?.coerceAtLeast(1)
        ?.let { cap -> min(configuredWorkerCount, cap) }
        ?: configuredWorkerCount
    return min(callerCapped, AI_TRANSLATION_PAGE_REMOTE_WORKER_CAP)
}

internal fun effectiveAiTranslationPreparationWorkerCount(): Int = 1

internal fun effectiveAiTranslationChunkWorkerCount(settings: AiSettings, chunkCount: Int, maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Int {
    val configured = settings.concurrentRequests.coerceIn(1, AI_TRANSLATION_MAX_CONFIGURED_CONCURRENCY)
    val memoryCap = when {
        maxMemoryBytes < 256L * 1024L * 1024L -> 2
        maxMemoryBytes < 384L * 1024L * 1024L -> 4
        maxMemoryBytes < 512L * 1024L * 1024L -> 8
        maxMemoryBytes < 768L * 1024L * 1024L -> 16
        else -> AI_TRANSLATION_MAX_CONFIGURED_CONCURRENCY
    }
    return min(configured, memoryCap)
        .coerceAtMost(chunkCount.coerceAtLeast(1))
        .coerceAtLeast(1)
}

internal fun effectiveAiTranslationPageRequestLimit(
    settings: AiSettings,
    regionCount: Int,
    pageContextPayloadBytes: Long,
    heap: AiRuntimeHeapSnapshot
): Int {
    if (settings.requestMode == AiTranslationRequestMode.SERIAL) return 1
    return effectiveAiTranslationMemoryConcurrency(
        configuredLimit = settings.concurrentRequests,
        imageTransport = settings.imageTransport,
        pageContextPayloadBytes = pageContextPayloadBytes,
        heap = heap
    ).coerceAtMost(regionCount.coerceAtLeast(1))
}

private fun aiLocalContextCacheKey(
    file: File,
    settings: AiSettings,
    sourceLanguage: AiSeriesSourceLanguageState
): String =
    listOf(
        "local-v14",
        file.length(),
        file.lastModified(),
        settings.localModelSource.storedValue,
        settings.modelCollectionId,
        settings.modelRevision,
        settings.autoSelectDeviceTier,
        settings.sourceTextProfile.storedValue,
        sourceLanguage.detectionCacheKey()
    ).joinToString(":")

private fun aiTranslationTimingKey(bookId: String, pageIndex: Int): String =
    "$bookId:$pageIndex"

private fun aiRegionCropCacheKey(file: File, rect: Rect): String =
    listOf(
        "region-v2",
        file.length(),
        file.lastModified(),
        rect.left,
        rect.top,
        rect.right,
        rect.bottom,
        AI_REGION_CROP_JPEG_QUALITY
    ).joinToString(":")

private fun localRegionReadingOrder(
    readingDirection: AiSourceReadingDirection = AiSourceReadingDirection.UNKNOWN
): Comparator<AiTranslationLocalTextRegion> =
    Comparator { left, right ->
        if (left.textDirection == AiTranslationTextDirection.VERTICAL || right.textDirection == AiTranslationTextDirection.VERTICAL) {
            compareValuesBy(left, right, { -it.rect.x }, { it.rect.y })
        } else if (readingDirection == AiSourceReadingDirection.RIGHT_TO_LEFT) {
            compareValuesBy(left, right, { it.rect.y }, { -it.rect.x })
        } else {
            compareValuesBy(left, right, { it.rect.y }, { it.rect.x })
        }
    }

private const val AI_IMAGE_JPEG_QUALITY = 82
private const val AI_PAGE_CONTEXT_JPEG_QUALITY = 75
private const val AI_REGION_CROP_JPEG_QUALITY = 88
private const val AI_REGION_CROP_PADDING_RATIO = 0.08f
private const val AI_REGION_CROP_MIN_PADDING_PX = 2
private const val AI_REGION_CROP_MAX_PADDING_PX = 48
private const val AI_REGION_CROP_MIN_SHORT_EDGE = 180
private const val AI_REGION_CROP_MAX_LONG_EDGE = 1024
private const val AI_TIMING_PAGE_IMAGE_CACHE = "page_image_cache"
private const val AI_TIMING_LOCAL_DETECTION_CACHE = "local_detection_cache"
private const val AI_TIMING_PAGE_IMAGE_INPUT = "page_image_input"
private const val AI_TIMING_REGION_CROP_IMAGES = "region_crop_images"
private const val AI_TIMING_AI_REQUEST_BATCH = "ai_request_batch"
private const val AI_TIMING_AI_REQUEST = "ai_request"
private const val AI_TIMING_AI_RESPONSE_PARSE = "ai_response_parse"
private const val AI_TIMING_SAVE_AND_VERIFY = "save_and_verify"
private const val AI_TRANSLATION_MAX_CHUNK_RETRIES = 2
private const val AI_TRANSLATION_PAGE_REMOTE_WORKER_CAP = 2
private const val AI_TRANSLATION_SECONDARY_PAGE_REQUEST_LIMIT = 2
private const val AI_TRANSLATION_RETRY_BASE_DELAY_MS = 500L
private const val AI_TRANSLATION_RETRY_MAX_DELAY_MS = 8_000L
private const val AI_TRANSLATION_TASK_PAUSE_POLL_MS = 500L
private const val AI_SOURCE_LANGUAGE_METADATA_TIMEOUT_MS = 2_000L
private const val AI_PAGE_CONTEXT_TEXT_MASK_PADDING_PX = 2

private data class CompressedAiImage(
    val mimeType: String,
    val bytes: ByteArray
)

internal data class AiPageContextMaskRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun pageContextTextMaskRectsForAi(
    imageWidth: Int,
    imageHeight: Int,
    regions: List<AiTranslationLocalTextRegion>
): List<AiPageContextMaskRect> {
    if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
    return regions.flatMap { region ->
        val sourceColumns = region.sourceColumns.filter { it.width > 0f && it.height > 0f }
        if (sourceColumns.isNotEmpty()) {
            sourceColumns.mapNotNull { column ->
                column.toPageContextMaskRect(
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    paddingPx = AI_PAGE_CONTEXT_TEXT_MASK_PADDING_PX
                )
            }
        } else {
            listOfNotNull(region.effectiveSourceMaskBounds().toPageContextMaskRect(imageWidth, imageHeight))
        }
    }
}

private fun AiTranslationRect.toPageContextMaskRect(
    imageWidth: Int,
    imageHeight: Int,
    paddingPx: Int = 0
): AiPageContextMaskRect? {
    if (width <= 0f || height <= 0f) return null
    val left = (floor(x * imageWidth).toInt() - paddingPx).coerceIn(0, imageWidth - 1)
    val top = (floor(y * imageHeight).toInt() - paddingPx).coerceIn(0, imageHeight - 1)
    val right = (ceil((x + width) * imageWidth).toInt() + paddingPx).coerceIn(left + 1, imageWidth)
    val bottom = (ceil((y + height) * imageHeight).toInt() + paddingPx).coerceIn(top + 1, imageHeight)
    return AiPageContextMaskRect(left = left, top = top, right = right, bottom = bottom)
}

private fun compressPageContextImageForAi(
    file: File,
    regions: List<AiTranslationLocalTextRegion>,
    strategy: AiPageContextStrategy
): CompressedAiImage {
    if (strategy == AiPageContextStrategy.LOCAL_PANEL_768) {
        compressLocalPanelContextImageForAi(file, regions, strategy.maxEdge)?.let { return it }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sampleSize = aiImageSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxEdge = strategy.maxEdge
    )
    return CompressedAiImage(
        mimeType = "image/jpeg",
        bytes = compressDecodedPageImage(
            file = file,
            sampleSize = sampleSize,
            quality = AI_PAGE_CONTEXT_JPEG_QUALITY,
            targetMaxEdge = strategy.maxEdge,
            pageContextMaskRegions = regions
        )
    )
}

@Suppress("DEPRECATION")
private fun compressLocalPanelContextImageForAi(
    file: File,
    regions: List<AiTranslationLocalTextRegion>,
    maxEdge: Int
): CompressedAiImage? {
    if (regions.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val regionBounds = regions.map { it.effectiveAiCropBounds() }
    val left = regionBounds.minOf { it.x }
    val top = regionBounds.minOf { it.y }
    val right = regionBounds.maxOf { it.x + it.width }
    val bottom = regionBounds.maxOf { it.y + it.height }
    val padding = max(right - left, bottom - top).coerceAtLeast(0.08f) * 0.45f
    val panelRect = AiTranslationRect(
        x = (left - padding).coerceAtLeast(0f),
        y = (top - padding).coerceAtLeast(0f),
        width = (right - left + padding * 2f).coerceAtMost(1f),
        height = (bottom - top + padding * 2f).coerceAtMost(1f)
    ).toAiCropRect(bounds.outWidth, bounds.outHeight) ?: return null
    val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return null
    val bitmap = try {
        decoder.decodeRegion(
            panelRect,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        )
    } finally {
        decoder.recycle()
    } ?: return null
    val maskedBitmap = bitmap.withOpaquePageContextPixelMasks(
        pageContextTextMaskRectsForAi(bounds.outWidth, bounds.outHeight, regions),
        panelRect
    )
    val outputBitmap = maskedBitmap.scaledDownToMaxEdge(maxEdge)
    return try {
        CompressedAiImage(
            mimeType = "image/jpeg",
            bytes = ByteArrayOutputStream().use { output ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, AI_PAGE_CONTEXT_JPEG_QUALITY, output)
                output.toByteArray()
            }
        )
    } finally {
        if (outputBitmap !== maskedBitmap) outputBitmap.recycle()
        if (maskedBitmap !== bitmap) maskedBitmap.recycle()
        bitmap.recycle()
    }
}

private fun Bitmap.withOpaquePageContextPixelMasks(
    sourceMasks: List<AiPageContextMaskRect>,
    panelRect: Rect
): Bitmap {
    val localMasks = sourceMasks.mapNotNull { mask ->
        val left = max(mask.left, panelRect.left)
        val top = max(mask.top, panelRect.top)
        val right = min(mask.right, panelRect.right)
        val bottom = min(mask.bottom, panelRect.bottom)
        if (right <= left || bottom <= top) return@mapNotNull null
        AiPageContextMaskRect(
            left = left - panelRect.left,
            top = top - panelRect.top,
            right = right - panelRect.left,
            bottom = bottom - panelRect.top
        )
    }
    if (localMasks.isEmpty()) return this
    val mutableBitmap = copy(Bitmap.Config.ARGB_8888, true) ?: return this
    val paint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 255
    }
    val canvas = Canvas(mutableBitmap)
    localMasks.forEach { mask ->
        canvas.drawRect(mask.left.toFloat(), mask.top.toFloat(), mask.right.toFloat(), mask.bottom.toFloat(), paint)
    }
    return mutableBitmap
}

private fun compressPageImageForAi(file: File, maxEdge: AiImageMaxEdge): CompressedAiImage {
    val targetMaxEdge = maxEdge.pixels
    if (targetMaxEdge == null) {
        return CompressedAiImage(
            mimeType = "image/jpeg",
            bytes = compressDecodedPageImage(file, sampleSize = 1, quality = AI_IMAGE_JPEG_QUALITY)
        )
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sampleSize = aiImageSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxEdge = targetMaxEdge
    )
    return CompressedAiImage(
        mimeType = "image/jpeg",
        bytes = compressDecodedPageImage(
            file = file,
            sampleSize = sampleSize,
            quality = AI_IMAGE_JPEG_QUALITY,
            targetMaxEdge = targetMaxEdge
        )
    )
}

private fun compressDecodedPageImage(
    file: File,
    sampleSize: Int,
    quality: Int,
    targetMaxEdge: Int? = null,
    pageContextMaskRegions: List<AiTranslationLocalTextRegion> = emptyList()
): ByteArray {
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("failed to decode page image")
    val maskedBitmap = bitmap.withOpaquePageContextTextMasks(pageContextMaskRegions)
    val outputBitmap = maskedBitmap.scaledDownToMaxEdge(targetMaxEdge)
    return try {
        ByteArrayOutputStream().use { output ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
    } finally {
        if (outputBitmap !== maskedBitmap) outputBitmap.recycle()
        if (maskedBitmap !== bitmap) maskedBitmap.recycle()
        bitmap.recycle()
    }
}

private fun Bitmap.withOpaquePageContextTextMasks(
    regions: List<AiTranslationLocalTextRegion>
): Bitmap {
    val maskRects = pageContextTextMaskRectsForAi(width, height, regions)
    if (maskRects.isEmpty()) return this
    val mutableBitmap = copy(Bitmap.Config.ARGB_8888, true) ?: return this
    val paint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 255
    }
    val canvas = Canvas(mutableBitmap)
    maskRects.forEach { rect ->
        canvas.drawRect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat(),
            paint
        )
    }
    return mutableBitmap
}

private fun Bitmap.scaledDownToMaxEdge(targetMaxEdge: Int?): Bitmap {
    val maxEdge = targetMaxEdge ?: return this
    val longest = max(width, height)
    if (longest <= 0 || longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun aiImageSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = maxOf(width, height)
    if (longest <= 0 || longest <= maxEdge) return 1
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}

private data class AiTranslationRunResult(
    val ok: Boolean,
    val summary: String = ""
)

internal fun alignReturnedPagesToRequestedIndexes(
    returnedPages: List<AiTranslatedPage>,
    requestedPageIndexes: List<Int>
): List<AiTranslatedPage> =
    if (returnedPages.size == requestedPageIndexes.size) {
        returnedPages.mapIndexed { index, page -> page.copy(pageIndex = requestedPageIndexes[index]) }
    } else {
        returnedPages
    }

class AiTranslationQueueRunner(
    private val store: AiTranslationStore
) {
    fun restoreRunningTasks() {
        val state = store.readTaskState()
        val interruptedBookIds = state.tasks
            .filter { it.status == AiTranslationTaskStatus.QUEUED || it.status == AiTranslationTaskStatus.RUNNING }
            .map { it.bookId }
            .filter { it.isNotBlank() }
            .distinct()
        interruptedBookIds.forEach(store::recoverInterruptedPages)
        store.saveTaskState(
            state.copy(
                paused = state.paused || interruptedBookIds.isNotEmpty(),
                tasks = state.tasks.map { task ->
                    if (task.bookId in interruptedBookIds) {
                        task.copy(
                            status = AiTranslationTaskStatus.PAUSED,
                            recoveryRequired = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        task
                    }
                }
            )
        )
    }
}
