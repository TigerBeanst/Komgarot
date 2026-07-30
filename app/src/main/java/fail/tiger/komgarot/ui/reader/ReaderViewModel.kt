package fail.tiger.komgarot.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiBookTranslationMetadata
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationFailureCategory
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRegionStatus
import fail.tiger.komgarot.data.local.pausedForRegionResume
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto
import fail.tiger.komgarot.data.repository.AiLocalModelPlan
import fail.tiger.komgarot.data.repository.AiLocalModelRepository
import fail.tiger.komgarot.data.repository.AiLocalModelTier
import fail.tiger.komgarot.data.repository.AiTranslationRepository
import fail.tiger.komgarot.data.repository.AiTranslationPageTiming
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.defaultAiLocalModelPlan
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class PageImageInfo(val bookId: String, val seriesId: String, val pageUrl: String)

enum class ReadingMode { PAGER, SCROLL }

private data class RequiredAiLocalModelPlan(
    val plan: AiLocalModelPlan,
    val revision: String
)

private sealed interface PendingAiTranslationAction {
    val bookId: String

    data class TranslateWindow(
        override val bookId: String,
        val anchorPage: Int,
        val preloadPages: Int,
        val publishStartedMessage: Boolean,
        val includeFailedPages: Boolean
    ) : PendingAiTranslationAction

    data class RetryPage(
        override val bookId: String,
        val pageIndex: Int
    ) : PendingAiTranslationAction
}

class ReaderViewModel(
    private val repo: BookRepository,
    val prefs: AuthPreferences,
    private val loadBookFailed: String,
    private val loadPagesFailed: String,
    private val aiTranslationRepository: AiTranslationRepository? = null,
    private val aiLocalModelRepository: AiLocalModelRepository? = null,
    private val aiLocalModelTierProvider: () -> AiLocalModelTier = { AiLocalModelTier.LOW }
) : ViewModel() {
    var pageUrls by mutableStateOf<List<String>>(emptyList())
    var currentPage by mutableIntStateOf(0)
    var mode by mutableStateOf(ReadingMode.PAGER)
    var showControls by mutableStateOf(true)
    var trackProgress by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var book by mutableStateOf<BookDto?>(null)
    var previousBook by mutableStateOf<BookDto?>(null)
    var nextBook by mutableStateOf<BookDto?>(null)
    var currentAiTranslationDisplayMode by mutableStateOf(AiTranslationDisplayMode.OFF)
    var currentAiTranslationMode by mutableStateOf(AiTranslationMode.LOCAL_DETECTION)
    var showAiTranslationPageActions by mutableStateOf(false)
    var showAiLocalModelRequiredDialog by mutableStateOf(false)
    var aiLocalModelDownloading by mutableStateOf(false)
    var aiTranslationMessageNonce by mutableIntStateOf(0)
        private set
    var aiTranslationMessageRes by mutableIntStateOf(0)
        private set
    var aiTranslationMessageText by mutableStateOf("")
        private set
    private var aiTranslatedBook by mutableStateOf<AiTranslatedBook?>(null)
    private var currentServerUrl: String = ""
    private var currentPages: List<PageDto> = emptyList()
    var currentBookId: String = ""
        private set
    var currentSeriesId: String = ""
        private set
    private var progressJob: Job? = null
    private var aiTranslationJob: Job? = null
    private var aiTranslationSequentialRestartJob: Job? = null
    private var aiTranslationPageIndexes: List<Int> = emptyList()
    var aiTranslationActivePageIndexes by mutableStateOf<List<Int>>(emptyList())
        private set
    var aiTranslationPriorityPageIndex by mutableIntStateOf(-1)
        private set
    private var pendingAiTranslationAction: PendingAiTranslationAction? = null
    private var pendingSequentialAiTranslationRestart: PendingAiTranslationAction.TranslateWindow? = null

    fun load(bookId: String, startPage: Int, trackProgress: Boolean = true) {
        progressJob?.cancel()
        if (isAiTranslationWorkRunning()) stopAiTranslationWork()
        this.trackProgress = trackProgress
        if (currentBookId != bookId) {
            pendingAiTranslationAction = null
            pageUrls = emptyList()
            currentPages = emptyList()
            currentPage = 0
            book = null
            previousBook = null
            nextBook = null
        }
        currentBookId = bookId
        viewModelScope.launch {
            loading = true
            error = null
            val base = prefs.serverUrl.first()
            currentServerUrl = base
            currentAiTranslationDisplayMode = AiTranslationDisplayMode.fromStoredValue(prefs.aiTranslationDisplayMode.first())
            repo.getBookById(bookId)
                .onSuccess { loadedBook ->
                    book = loadedBook
                    currentSeriesId = loadedBook.seriesId
                    currentAiTranslationMode = aiTranslationRepository?.preferredModeForBook(bookId)
                        ?: AiTranslationMode.LOCAL_DETECTION
                    aiTranslatedBook = aiTranslationRepository?.readBookState(bookId)
                    previousBook = repo.getPreviousBook(bookId).getOrNull()
                    nextBook = repo.getNextBook(bookId).getOrNull()
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: loadBookFailed
                }

            runCatching { repo.getPages(bookId) }
                .onSuccess { pages ->
                    currentPages = pages
                    pageUrls = pages.map { readerPageUrl(base, bookId, it) }
                    currentPage = if (pageUrls.isEmpty()) 0 else (startPage - 1).coerceIn(0, pageUrls.lastIndex)
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: loadPagesFailed
                }
            loading = false
        }
    }

    fun toggleControls() { showControls = !showControls }
    fun toggleMode() { mode = if (mode == ReadingMode.PAGER) ReadingMode.SCROLL else ReadingMode.PAGER }

    fun updatePage(page: Int) {
        currentPage = page
        ensureAiTranslationPageShell(currentPage)
        scheduleProgressUpdate()
    }

    fun currentAiTranslatedPage(pageIndex: Int): AiTranslatedPage? =
        aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == pageIndex }

    fun currentAiTranslationTiming(): AiTranslationPageTiming? {
        val loaded = book ?: return null
        return aiTranslationRepository?.readPageTiming(loaded.id, currentPage)
    }

    fun currentAiTranslationWindowStatus(preloadPages: Int): ReaderAiTranslationWindowStatus =
        readerAiTranslationWindowStatus(
            pageIndexes = readerAiTranslationPageRange(currentPage, pageUrls.size, preloadPages),
            pages = aiTranslatedBook?.pages.orEmpty()
        )

    fun pageInfo(pageIndex: Int): PageDto? = currentPages.getOrNull(pageIndex)

    fun currentAiTranslationModeForPage(pageIndex: Int): String {
        val page = currentAiTranslatedPage(pageIndex)
        return when (page?.status) {
            AiTranslationPageStatus.RUNNING,
            AiTranslationPageStatus.DONE,
            AiTranslationPageStatus.FAILED -> page.mode.ifBlank { currentAiTranslationMode.storedValue }
            else -> currentAiTranslationMode.storedValue
        }
    }

    fun refreshAiTranslationState() {
        val loaded = book ?: return
        val repository = aiTranslationRepository ?: return
        aiTranslatedBook = mergeAiTranslationRefresh(aiTranslatedBook, repository.readBookState(loaded.id))
        currentAiTranslationMode = repository.preferredModeForBook(loaded.id)
    }

    fun aiTranslationDisplayModeForPage(pageIndex: Int): AiTranslationDisplayMode =
        when (currentAiTranslationDisplayMode) {
            AiTranslationDisplayMode.ON -> AiTranslationDisplayMode.ON
            AiTranslationDisplayMode.OFF -> AiTranslationDisplayMode.OFF
        }

    fun cycleAiTranslationDisplayMode() {
        currentAiTranslationDisplayMode = when (currentAiTranslationDisplayMode) {
            AiTranslationDisplayMode.OFF -> AiTranslationDisplayMode.ON
            AiTranslationDisplayMode.ON -> AiTranslationDisplayMode.OFF
        }
        viewModelScope.launch {
            prefs.setAiTranslationDisplayMode(currentAiTranslationDisplayMode.storedValue)
        }
    }

    fun handleAiTranslationButtonClick(preloadPages: Int) {
        if (isAiTranslationWorkRunning()) {
            stopAiTranslationWork()
            return
        }
        if (currentAiTranslationDisplayMode == AiTranslationDisplayMode.ON) {
            cycleAiTranslationDisplayMode()
            return
        }
        viewModelScope.launch {
            val showedCachedCurrentPage = showCachedCurrentAiTranslationIfAvailable()
            if (showedCachedCurrentPage && !hasPendingAiTranslationPages(preloadPages)) return@launch
            if (!canStartAiTranslationWithLocalModel()) {
                pendingAiTranslationAction = PendingAiTranslationAction.TranslateWindow(
                    bookId = currentBookId,
                    anchorPage = currentPage,
                    preloadPages = preloadPages,
                    publishStartedMessage = true,
                    includeFailedPages = true
                )
                showAiLocalModelRequiredDialog = true
                return@launch
            }
            startCurrentAndPreloadedAiTranslation(preloadPages)
        }
    }

    private suspend fun canStartAiTranslationWithLocalModel(): Boolean {
        val repository = aiLocalModelRepository ?: return false
        val required = requiredAiLocalModelPlan()
        return repository.isPlanInstalled(required.plan, required.revision)
    }

    fun downloadRequiredAiLocalModel() {
        val repository = aiLocalModelRepository ?: return
        if (aiLocalModelDownloading) return
        viewModelScope.launch {
            aiLocalModelDownloading = true
            val required = requiredAiLocalModelPlan()
            val result = repository.downloadPlan(required.plan, required.revision)
            aiLocalModelDownloading = false
            if (result.isSuccess) {
                showAiLocalModelRequiredDialog = false
                publishAiTranslationMessage(R.string.reader_ai_local_model_download_success)
                continuePendingAiTranslationAction()
            } else {
                publishAiTranslationMessage(R.string.reader_ai_local_model_download_failed)
            }
        }
    }

    fun dismissAiLocalModelRequiredDialog() {
        if (aiLocalModelDownloading) return
        showAiLocalModelRequiredDialog = false
        pendingAiTranslationAction = null
    }

    private fun continuePendingAiTranslationAction() {
        val action = pendingAiTranslationAction ?: return
        pendingAiTranslationAction = null
        if (action.bookId != currentBookId) return
        when (action) {
            is PendingAiTranslationAction.TranslateWindow -> startOrExtendCurrentAndPreloadedAiTranslation(
                preloadPages = action.preloadPages,
                publishStartedMessage = action.publishStartedMessage,
                includeFailedPages = action.includeFailedPages,
                anchorPage = action.anchorPage
            )
            is PendingAiTranslationAction.RetryPage -> {
                val loaded = book ?: return
                val repository = aiTranslationRepository ?: return
                startCurrentAiTranslationPageRetry(loaded, repository, action.pageIndex)
            }
        }
    }

    private suspend fun requiredAiLocalModelPlan(): RequiredAiLocalModelPlan {
        val revision = prefs.aiModelRevision.first()
        val tier = if (prefs.aiAutoSelectDeviceTier.first()) aiLocalModelTierProvider() else AiLocalModelTier.LOW
        val plan = defaultAiLocalModelPlan(
            collectionId = prefs.aiModelCollectionId.first(),
            revision = revision,
            tier = tier
        )
        return RequiredAiLocalModelPlan(plan = plan, revision = revision)
    }

    private fun showCachedCurrentAiTranslationIfAvailable(): Boolean {
        if (currentAiTranslationDisplayMode == AiTranslationDisplayMode.ON) return false
        val loaded = book ?: return false
        val repository = aiTranslationRepository ?: return false
        val cachedBook = repository.readBookState(loaded.id) ?: return false
        val cachedPage = cachedBook.pages.firstOrNull { it.pageIndex == currentPage }
        if (cachedPage?.status == AiTranslationPageStatus.DONE) {
            aiTranslatedBook = cachedBook
            currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON
            viewModelScope.launch {
                prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)
            }
            return true
        }
        return false
    }

    private fun hasPendingAiTranslationPages(preloadPages: Int): Boolean {
        val loaded = book ?: return false
        val repository = aiTranslationRepository ?: return false
        val cachedBook = repository.readBookState(loaded.id) ?: aiTranslatedBook
        val pages = cachedBook?.pages.orEmpty().associateBy { it.pageIndex }
        return readerAiTranslationPageRange(currentPage, pageUrls.size, preloadPages).any { pageIndex ->
            val page = pages[pageIndex]
            page?.status != AiTranslationPageStatus.DONE
        }
    }

    fun translateCurrentAiPageIfDisplayEnabled(preloadPages: Int) {
        if (currentAiTranslationDisplayMode != AiTranslationDisplayMode.ON) return
        viewModelScope.launch {
            if (!canStartAiTranslationWithLocalModel()) {
                pendingAiTranslationAction = PendingAiTranslationAction.TranslateWindow(
                    bookId = currentBookId,
                    anchorPage = currentPage,
                    preloadPages = preloadPages,
                    publishStartedMessage = false,
                    includeFailedPages = false
                )
                showAiLocalModelRequiredDialog = true
                return@launch
            }
            startOrExtendCurrentAndPreloadedAiTranslation(
                preloadPages = preloadPages,
                publishStartedMessage = false,
                includeFailedPages = false
            )
        }
    }

    private fun isAiTranslationWorkRunning(): Boolean =
        aiTranslationJob?.isActive == true || aiTranslationSequentialRestartJob?.isActive == true

    private fun stopAiTranslationWork() {
        val pageIndexes = aiTranslationPageIndexes
        pendingSequentialAiTranslationRestart = null
        aiTranslationSequentialRestartJob?.cancel()
        aiTranslationSequentialRestartJob = null
        aiTranslationJob?.cancel(userPausedAiTranslationCancellation())
        aiTranslationJob = null
        aiTranslationPageIndexes = emptyList()
        aiTranslationActivePageIndexes = emptyList()
        aiTranslationPriorityPageIndex = -1
        resetRunningAiTranslationStoreState(pageIndexes)
        clearRunningAiTranslationState()
        currentAiTranslationDisplayMode = AiTranslationDisplayMode.OFF
        viewModelScope.launch {
            prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.OFF.storedValue)
        }
    }

    private fun userPausedAiTranslationCancellation(): CancellationException =
        CancellationException("AI translation paused by user")

    private fun readerPageOrderAiTranslationCancellation(): CancellationException =
        CancellationException("AI translation restarted to preserve reader page order")

    private fun resetRunningAiTranslationStoreState(pageIndexes: List<Int>) {
        val loaded = book ?: return
        if (pageIndexes.isEmpty()) return
        val repository = aiTranslationRepository ?: return
        repository.resetRunningPages(loaded.id, pageIndexes)
        aiTranslatedBook = mergeAiTranslationRefresh(aiTranslatedBook, repository.readBookState(loaded.id))
    }

    private fun clearRunningAiTranslationState() {
        val existing = aiTranslatedBook ?: return
        aiTranslatedBook = existing.copy(
            pages = existing.pages.map { page ->
                if (page.status == AiTranslationPageStatus.RUNNING) {
                    page.pausedForRegionResume()
                } else {
                    page
                }
            }
        )
    }

    private fun startCurrentAndPreloadedAiTranslation(preloadPages: Int) {
        startOrExtendCurrentAndPreloadedAiTranslation(
            preloadPages = preloadPages,
            publishStartedMessage = true,
            includeFailedPages = true
        )
    }

    private fun startOrExtendCurrentAndPreloadedAiTranslation(
        preloadPages: Int,
        publishStartedMessage: Boolean,
        includeFailedPages: Boolean,
        anchorPage: Int = currentPage
    ) {
        val loaded = book ?: return
        val repository = aiTranslationRepository ?: run {
            publishAiTranslationMessage(R.string.ai_translate_config_required)
            return
        }
        val sequentialRestart = PendingAiTranslationAction.TranslateWindow(
            bookId = loaded.id,
            anchorPage = anchorPage,
            preloadPages = preloadPages,
            publishStartedMessage = publishStartedMessage,
            includeFailedPages = includeFailedPages
        )
        if (aiTranslationSequentialRestartJob?.isActive == true) {
            pendingSequentialAiTranslationRestart = sequentialRestart
            return
        }
        val storedBook = repository.readBookState(loaded.id)
        aiTranslatedBook = mergeAiTranslationRefresh(aiTranslatedBook, storedBook)
            ?: storedBook
            ?: aiTranslatedBook
            ?: localAiBookShell(loaded)
        currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON
        val sequentialPageIndexes = readerAiTranslationPageRange(anchorPage, pageUrls.size, preloadPages)
        val pageIndexes = sequentialReaderAiTranslationQueue(
            pageIndexes = sequentialPageIndexes,
            pages = aiTranslatedBook?.pages.orEmpty(),
            includeFailedPages = includeFailedPages
        )
        if (pageIndexes.isEmpty()) {
            viewModelScope.launch {
                prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)
            }
            return
        }
        if (aiTranslationJob?.isActive == true) {
            val firstSequentialPage = firstSequentialAiTranslationPage(
                pageIndexes = sequentialPageIndexes,
                pages = aiTranslatedBook?.pages.orEmpty()
            )
            val activePage = aiTranslationActivePageIndexes.firstOrNull()
            if (firstSequentialPage != null && activePage != null && activePage != firstSequentialPage) {
                restartAiTranslationInSequentialOrder(sequentialRestart)
                return
            }
            prioritizeAiTranslationPageIndexes(pageIndexes)
            if (firstSequentialPage in aiTranslationActivePageIndexes) {
                aiTranslationPriorityPageIndex = firstSequentialPage ?: -1
                viewModelScope.launch {
                    repository.prioritizeReaderPage(loaded.id, firstSequentialPage ?: return@launch)
                }
            }
            return
        }
        if (publishStartedMessage) publishAiTranslationMessage(R.string.reader_ai_retry_started)
        aiTranslationJob?.cancel(userPausedAiTranslationCancellation())
        prioritizeAiTranslationPageIndexes(pageIndexes)
        val launchedJob = viewModelScope.launch {
            prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)
            val processedPageIndexes = mutableSetOf<Int>()
            val automaticRetryCounts = mutableMapOf<Int, Int>()
            try {
                while (true) {
                    val batchPageIndexes = nextAiTranslationPageBatch(
                        pageIndexes = aiTranslationPageIndexes,
                        processedPageIndexes = processedPageIndexes
                    )
                    if (batchPageIndexes.isEmpty()) break
                    processedPageIndexes += batchPageIndexes
                    aiTranslationActivePageIndexes = batchPageIndexes
                    aiTranslationPriorityPageIndex = batchPageIndexes.firstOrNull() ?: -1
                    repository.prioritizeReaderPage(loaded.id, aiTranslationPriorityPageIndex)
                    batchPageIndexes.forEach { pageIndex ->
                        updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.RUNNING)
                    }
                    val result = try {
                        repository.resumePagesTranslation(
                            book = loaded,
                            serverUrl = currentServerUrl,
                            pageIndexes = batchPageIndexes,
                            cachedPages = currentPages,
                            remotePageConcurrencyCap = READER_AI_TRANSLATION_REMOTE_PAGE_CONCURRENCY,
                            onPageUpdated = { page ->
                                launch { applyAiTranslationPageUpdate(page) }
                            }
                        )
                    } finally {
                        if (aiTranslationActivePageIndexes == batchPageIndexes) {
                            aiTranslationActivePageIndexes = emptyList()
                            aiTranslationPriorityPageIndex = -1
                        }
                    }
                    aiTranslatedBook = repository.readBookState(loaded.id)
                    currentAiTranslationMode = repository.preferredModeForBook(loaded.id)
                    val firstFailedPageIndex = batchPageIndexes.firstOrNull { pageIndex ->
                        aiTranslatedBook?.pages
                            ?.firstOrNull { page -> page.pageIndex == pageIndex }
                            ?.status != AiTranslationPageStatus.DONE
                    }
                    var scheduledAutomaticRetry = false
                    var stopAtFailedPage = false
                    if (!result.ok || firstFailedPageIndex != null) {
                        val pageIndex = firstFailedPageIndex ?: batchPageIndexes.first()
                        val updatedPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == pageIndex }
                        batchPageIndexes.forEach { batchPageIndex ->
                            val batchPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == batchPageIndex }
                            if (batchPage?.status != AiTranslationPageStatus.DONE && batchPage?.status != AiTranslationPageStatus.FAILED) {
                                updateAiTranslationPageStatus(batchPageIndex, AiTranslationPageStatus.FAILED)
                            }
                        }
                        if (updatedPage?.status != AiTranslationPageStatus.DONE) {
                            updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.FAILED)
                        }
                        val recoverablePageIndexes = batchPageIndexes
                            .filter { batchPageIndex ->
                                val failedPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == batchPageIndex }
                                shouldAutomaticallyRetryReaderAiTranslation(
                                    page = failedPage,
                                    retryCount = automaticRetryCounts[batchPageIndex] ?: 0
                                )
                            }
                            .sortedBy { batchPageIndex -> if (batchPageIndex == currentPage) 0 else 1 }
                        if (recoverablePageIndexes.isNotEmpty()) {
                            scheduledAutomaticRetry = true
                            recoverablePageIndexes.forEach { retryPageIndex ->
                                automaticRetryCounts[retryPageIndex] = (automaticRetryCounts[retryPageIndex] ?: 0) + 1
                                updateAiTranslationPageStatus(retryPageIndex, AiTranslationPageStatus.RUNNING)
                            }
                            processedPageIndexes.removeAll(recoverablePageIndexes.toSet())
                            prioritizeAiTranslationPageIndexes(recoverablePageIndexes)
                            val retryDelayMs = recoverablePageIndexes
                                .mapNotNull { retryPageIndex ->
                                    aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == retryPageIndex }?.retryAfterMs
                                }
                                .maxOrNull()
                                ?.coerceIn(READER_AI_TRANSLATION_AUTO_RETRY_MIN_DELAY_MS, READER_AI_TRANSLATION_AUTO_RETRY_MAX_DELAY_MS)
                                ?: READER_AI_TRANSLATION_AUTO_RETRY_MIN_DELAY_MS
                            delay(retryDelayMs)
                        }
                        val reportedPageIndex = batchPageIndexes.firstOrNull { batchPageIndex ->
                            batchPageIndex !in recoverablePageIndexes &&
                                aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == batchPageIndex }?.status !=
                                AiTranslationPageStatus.DONE
                        }
                        if (reportedPageIndex != null) {
                            stopAtFailedPage = true
                            val reportedPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == reportedPageIndex }
                            publishAiTranslationFailureMessage(loaded, reportedPageIndex, reportedPage, result)
                        }
                    }
                    if (!scheduledAutomaticRetry) refreshAiTranslationState()
                    if (stopAtFailedPage) break
                }
            } catch (cancelled: CancellationException) {
                resetRunningAiTranslationStoreState(aiTranslationPageIndexes)
                clearRunningAiTranslationState()
                throw cancelled
            } finally {
                aiTranslationActivePageIndexes = emptyList()
                aiTranslationPriorityPageIndex = -1
                if (aiTranslationJob == coroutineContext[Job]) {
                    aiTranslationJob = null
                    aiTranslationPageIndexes = emptyList()
                }
            }
        }
        aiTranslationJob = launchedJob
    }

    private fun restartAiTranslationInSequentialOrder(
        request: PendingAiTranslationAction.TranslateWindow
    ) {
        pendingSequentialAiTranslationRestart = request
        if (aiTranslationSequentialRestartJob?.isActive == true) return
        val runningJob = aiTranslationJob ?: return
        val restartJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            runningJob.cancel(readerPageOrderAiTranslationCancellation())
            runningJob.join()
            val latestRequest = pendingSequentialAiTranslationRestart
            pendingSequentialAiTranslationRestart = null
            aiTranslationSequentialRestartJob = null
            if (
                latestRequest != null &&
                latestRequest.bookId == currentBookId &&
                currentAiTranslationDisplayMode == AiTranslationDisplayMode.ON
            ) {
                startOrExtendCurrentAndPreloadedAiTranslation(
                    preloadPages = latestRequest.preloadPages,
                    publishStartedMessage = false,
                    includeFailedPages = latestRequest.includeFailedPages,
                    anchorPage = latestRequest.anchorPage
                )
            }
        }
        aiTranslationSequentialRestartJob = restartJob
        restartJob.start()
    }

    private fun prioritizeAiTranslationPageIndexes(pageIndexes: List<Int>) {
        aiTranslationPageIndexes = (pageIndexes + aiTranslationPageIndexes)
            .distinct()
            .filter { it in 0 until pageUrls.size }
    }

    fun retryCurrentAiTranslationPage() {
        val loaded = book ?: return
        val repository = aiTranslationRepository ?: run {
            publishAiTranslationMessage(R.string.ai_translate_config_required)
            return
        }
        viewModelScope.launch {
            if (!canStartAiTranslationWithLocalModel()) {
                pendingAiTranslationAction = PendingAiTranslationAction.RetryPage(
                    bookId = currentBookId,
                    pageIndex = currentPage
                )
                showAiLocalModelRequiredDialog = true
                return@launch
            }
            startCurrentAiTranslationPageRetry(loaded, repository, currentPage)
        }
    }

    private fun startCurrentAiTranslationPageRetry(
        loaded: BookDto,
        repository: AiTranslationRepository,
        pageIndex: Int
    ) {
        aiTranslatedBook = repository.readBookState(loaded.id) ?: localAiBookShell(loaded)
        currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON
        viewModelScope.launch {
            prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)
        }
        val pageIndexes = listOf(pageIndex)
        aiTranslationJob?.cancel(userPausedAiTranslationCancellation())
        resetRunningAiTranslationStoreState(aiTranslationPageIndexes)
        clearRunningAiTranslationState()
        updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.RUNNING)
        publishAiTranslationMessage(R.string.reader_ai_retry_started)
        aiTranslationPageIndexes = pageIndexes
        val launchedJob = viewModelScope.launch {
            try {
                val result = repository.retryPageTranslation(
                    book = loaded,
                    serverUrl = currentServerUrl,
                    pageIndex = pageIndex,
                    cachedPages = currentPages,
                    onPageUpdated = { page ->
                        launch { applyAiTranslationPageUpdate(page) }
                    }
                )
                aiTranslatedBook = repository.readBookState(loaded.id)
                currentAiTranslationMode = repository.preferredModeForBook(loaded.id)
                val updatedPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == pageIndex }
                val pageUpdated = result.ok && updatedPage?.status == AiTranslationPageStatus.DONE
                if (!pageUpdated) updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.FAILED)
                if (pageUpdated) {
                    publishAiTranslationMessage(R.string.reader_ai_retry_success)
                } else {
                    publishAiTranslationFailureMessage(loaded, pageIndex, updatedPage, result)
                }
            } catch (cancelled: CancellationException) {
                resetRunningAiTranslationStoreState(pageIndexes)
                clearRunningAiTranslationState()
                throw cancelled
            } finally {
                if (aiTranslationJob == coroutineContext[Job]) {
                    aiTranslationJob = null
                    aiTranslationPageIndexes = emptyList()
                }
            }
        }
        aiTranslationJob = launchedJob
    }

    fun deleteCurrentAiTranslationPage() {
        val loaded = book ?: return
        aiTranslationRepository?.deletePageTranslation(loaded.id, currentPage)
        aiTranslatedBook = aiTranslationRepository?.readBookState(loaded.id)
    }

    fun clearCurrentBookAiTranslation() {
        val loaded = book ?: return
        aiTranslationJob?.cancel()
        aiTranslationJob = null
        currentAiTranslationDisplayMode = AiTranslationDisplayMode.OFF
        viewModelScope.launch {
            aiTranslationRepository?.clearBook(loaded.id)
            aiTranslatedBook = aiTranslationRepository?.readBookState(loaded.id)
            prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.OFF.storedValue)
        }
    }

    fun testCurrentAiTranslationPage() {
        val loaded = book ?: run {
            publishAiTranslationMessage(R.string.reader_ai_test_failed)
            return
        }
        val repository = aiTranslationRepository ?: run {
            publishAiTranslationMessage(R.string.ai_translate_config_required)
            return
        }
        publishAiTranslationMessage(R.string.reader_ai_test_started)
        viewModelScope.launch {
            val ok = repository.testPageTranslationConfiguration(loaded, currentServerUrl, currentPage, currentPages)
            aiTranslatedBook = repository.readBookState(loaded.id)
            currentAiTranslationMode = repository.preferredModeForBook(loaded.id)
            publishAiTranslationMessage(
                if (ok) R.string.reader_ai_test_success else R.string.reader_ai_test_failed
            )
        }
    }

    private fun updateCurrentAiTranslationPageStatus(status: AiTranslationPageStatus) {
        updateAiTranslationPageStatus(currentPage, status)
    }

    private fun updateAiTranslationPageStatus(pageIndex: Int, status: AiTranslationPageStatus) {
        val existing = aiTranslatedBook ?: return
        val current = existing.pages.firstOrNull { it.pageIndex == pageIndex }
        val updated = (current ?: AiTranslatedPage(pageIndex = pageIndex)).copy(
            status = status,
            errorSummary = if (status == AiTranslationPageStatus.RUNNING) "" else current?.errorSummary.orEmpty()
        )
        aiTranslatedBook = existing.copy(
            pages = (existing.pages.filterNot { it.pageIndex == pageIndex } + updated)
                .sortedBy { it.pageIndex }
        )
    }

    private fun applyAiTranslationPageUpdate(page: AiTranslatedPage) {
        aiTranslatedBook = mergeAiTranslationPageUpdate(aiTranslatedBook, page)
    }

    private fun localAiBookShell(loaded: BookDto): AiTranslatedBook =
        AiTranslatedBook(
            bookId = loaded.id,
            seriesId = loaded.seriesId,
            title = loaded.metadata.title,
            pageCount = loaded.media.pagesCount,
            translation = AiBookTranslationMetadata(mode = currentAiTranslationMode.storedValue),
            pages = (0 until loaded.media.pagesCount.coerceAtLeast(0)).map {
                AiTranslatedPage(pageIndex = it, mode = currentAiTranslationMode.storedValue)
            }
        )

    private fun ensureAiTranslationPageShell(pageIndex: Int) {
        if (currentAiTranslationDisplayMode != AiTranslationDisplayMode.ON) return
        val loaded = book ?: return
        val existing = aiTranslatedBook ?: localAiBookShell(loaded)
        if (existing.pages.any { it.pageIndex == pageIndex }) {
            aiTranslatedBook = existing
            return
        }
        aiTranslatedBook = existing.copy(
            pages = (existing.pages + AiTranslatedPage(pageIndex = pageIndex, mode = currentAiTranslationMode.storedValue))
                .sortedBy { it.pageIndex }
        )
    }

    private fun publishAiTranslationMessage(messageRes: Int, messageText: String = "") {
        aiTranslationMessageRes = messageRes
        aiTranslationMessageText = messageText
        aiTranslationMessageNonce += 1
    }

    private fun publishAiTranslationFailureMessage(
        loaded: BookDto,
        pageIndex: Int = currentPage,
        updatedPage: AiTranslatedPage?,
        result: fail.tiger.komgarot.data.repository.AiTranslationPageActionResult
    ) {
        val failureSummary = updatedPage?.errorSummary?.takeIf { it.isNotBlank() } ?: result.summary.takeIf { it.isNotBlank() }
        publishAiTranslationMessage(
            R.string.reader_ai_retry_failed,
            failureSummary ?: buildAiRetryFallbackSummary(loaded, pageIndex, updatedPage, result)
        )
    }

    private fun buildAiRetryFallbackSummary(
        loaded: BookDto,
        pageIndex: Int,
        updatedPage: AiTranslatedPage?,
        result: fail.tiger.komgarot.data.repository.AiTranslationPageActionResult
    ): String =
        "AI translation failed: book=${loaded.id}, page=$pageIndex, resultOk=${result.ok}, savedStatus=${updatedPage?.status?.name ?: "missing"}"

    fun goToPage(page: Int) {
        if (pageUrls.isEmpty()) return
        currentPage = page.coerceIn(0, pageUrls.size - 1)
        ensureAiTranslationPageShell(currentPage)
        scheduleProgressUpdate()
    }

    fun flushProgress() {
        progressJob?.cancel()
        if (trackProgress && currentBookId.isNotEmpty() && pageUrls.isNotEmpty()) {
            submitProgress(currentPage)
        }
    }

    private fun scheduleProgressUpdate() {
        if (!trackProgress || currentBookId.isEmpty() || pageUrls.isEmpty()) return
        val pageToSubmit = currentPage
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(700)
            submitProgress(pageToSubmit)
        }
    }

    private fun submitProgress(page: Int) {
        viewModelScope.launch {
            runCatching {
                repo.updateReadProgress(currentBookId, page + 1, page == pageUrls.size - 1)
            }
        }
    }

    class Factory(
        repo: BookRepository,
        prefs: AuthPreferences,
        textProvider: UiTextProvider,
        aiTranslationRepository: AiTranslationRepository? = null,
        aiLocalModelRepository: AiLocalModelRepository? = null,
        aiLocalModelTierProvider: () -> AiLocalModelTier = { AiLocalModelTier.LOW }
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            ReaderViewModel(
                repo,
                prefs,
                textProvider.get(R.string.error_load_books_failed),
                textProvider.get(R.string.error_load_pages_failed),
                aiTranslationRepository,
                aiLocalModelRepository,
                aiLocalModelTierProvider
            )
        }
    })
}

internal fun readerAiTranslationPageRange(currentPage: Int, pageCount: Int, preloadPages: Int): List<Int> {
    if (pageCount <= 0) return emptyList()
    val start = currentPage.coerceIn(0, pageCount - 1)
    val end = (start + preloadPages.coerceAtLeast(0)).coerceAtMost(pageCount - 1)
    return (start..end).toList()
}

data class ReaderAiTranslationWindowStatus(
    val totalPages: Int = 0,
    val completedPages: Int = 0,
    val runningPages: Int = 0,
    val failedPages: Int = 0,
    val pausedPages: Int = 0,
    val completedRegions: Int = 0,
    val runningRegions: Int = 0,
    val failedRegions: Int = 0,
    val pausedRegions: Int = 0
)

internal fun readerAiTranslationWindowStatus(
    pageIndexes: List<Int>,
    pages: List<AiTranslatedPage>
): ReaderAiTranslationWindowStatus {
    val pagesByIndex = pages.associateBy { it.pageIndex }
    var completedPages = 0
    var runningPages = 0
    var failedPages = 0
    var pausedPages = 0
    var completedRegions = 0
    var runningRegions = 0
    var failedRegions = 0
    var pausedRegions = 0
    pageIndexes.distinct().forEach { pageIndex ->
        val page = pagesByIndex[pageIndex]
        when (page?.status) {
            AiTranslationPageStatus.DONE -> completedPages += 1
            AiTranslationPageStatus.RUNNING -> runningPages += 1
            AiTranslationPageStatus.FAILED -> failedPages += 1
            AiTranslationPageStatus.PENDING, null -> pausedPages += 1
        }
        page?.blocks.orEmpty().forEach { block ->
            when (block.regionStatus) {
                AiTranslationRegionStatus.DONE -> completedRegions += 1
                AiTranslationRegionStatus.RUNNING -> runningRegions += 1
                AiTranslationRegionStatus.FAILED -> failedRegions += 1
                AiTranslationRegionStatus.PENDING -> {
                    if (page?.status == AiTranslationPageStatus.RUNNING) {
                        runningRegions += 1
                    } else {
                        pausedRegions += 1
                    }
                }
            }
        }
    }
    return ReaderAiTranslationWindowStatus(
        totalPages = pageIndexes.distinct().size,
        completedPages = completedPages,
        runningPages = runningPages,
        failedPages = failedPages,
        pausedPages = pausedPages,
        completedRegions = completedRegions,
        runningRegions = runningRegions,
        failedRegions = failedRegions,
        pausedRegions = pausedRegions
    )
}

internal fun nextAiTranslationPageBatch(
    pageIndexes: List<Int>,
    processedPageIndexes: Set<Int>
): List<Int> = pageIndexes
    .filter { pageIndex -> pageIndex !in processedPageIndexes }
    .take(READER_AI_TRANSLATION_PAGE_BATCH_SIZE)

internal fun firstSequentialAiTranslationPage(
    pageIndexes: List<Int>,
    pages: List<AiTranslatedPage>
): Int? {
    val pagesByIndex = pages.associateBy(AiTranslatedPage::pageIndex)
    return pageIndexes.firstOrNull { pageIndex ->
        pagesByIndex[pageIndex]?.status != AiTranslationPageStatus.DONE
    }
}

internal fun sequentialReaderAiTranslationQueue(
    pageIndexes: List<Int>,
    pages: List<AiTranslatedPage>,
    includeFailedPages: Boolean
): List<Int> {
    val pagesByIndex = pages.associateBy(AiTranslatedPage::pageIndex)
    val queued = mutableListOf<Int>()
    for (pageIndex in pageIndexes) {
        when (pagesByIndex[pageIndex]?.status) {
            AiTranslationPageStatus.DONE,
            AiTranslationPageStatus.RUNNING -> Unit
            AiTranslationPageStatus.FAILED -> {
                if (includeFailedPages) queued += pageIndex else break
            }
            AiTranslationPageStatus.PENDING,
            null -> queued += pageIndex
        }
    }
    return queued
}

internal fun shouldAutomaticallyRetryReaderAiTranslation(
    page: AiTranslatedPage?,
    retryCount: Int
): Boolean {
    if (page?.status != AiTranslationPageStatus.FAILED || retryCount >= READER_AI_TRANSLATION_AUTO_RETRY_LIMIT) {
        return false
    }
    return AiTranslationFailureCategory.entries
        .firstOrNull { category -> category.storedValue == page.errorCategory }
        .let { category ->
            category == AiTranslationFailureCategory.NETWORK_OR_API ||
                category == AiTranslationFailureCategory.NON_JSON_RESPONSE ||
                category == AiTranslationFailureCategory.JSON_VALIDATION_FAILED ||
                category == AiTranslationFailureCategory.EMPTY_AI_RESULT ||
                category == AiTranslationFailureCategory.IMAGE_INPUT ||
                category == AiTranslationFailureCategory.REGION_CROP ||
                category == AiTranslationFailureCategory.SAVE_VERIFICATION ||
                category == AiTranslationFailureCategory.UNKNOWN
        }
}

private const val READER_AI_TRANSLATION_REMOTE_PAGE_CONCURRENCY = 1
private const val READER_AI_TRANSLATION_PAGE_BATCH_SIZE = READER_AI_TRANSLATION_REMOTE_PAGE_CONCURRENCY
private const val READER_AI_TRANSLATION_AUTO_RETRY_LIMIT = 1
private const val READER_AI_TRANSLATION_AUTO_RETRY_MIN_DELAY_MS = 1_500L
private const val READER_AI_TRANSLATION_AUTO_RETRY_MAX_DELAY_MS = 8_000L

internal fun mergeAiTranslationRefresh(
    current: AiTranslatedBook?,
    refreshed: AiTranslatedBook?
): AiTranslatedBook? {
    if (current == null) return refreshed
    if (refreshed == null) return current

    val currentRunningByPage = current.pages
        .filter { it.status == AiTranslationPageStatus.RUNNING }
        .associateBy { it.pageIndex }
    if (currentRunningByPage.isEmpty()) return refreshed

    val refreshedPageIndexes = refreshed.pages.map { it.pageIndex }.toSet()
    val mergedPages = refreshed.pages.map { fresh ->
        val running = currentRunningByPage[fresh.pageIndex]
        if (running != null && fresh.status == AiTranslationPageStatus.PENDING) running else fresh
    } + current.pages.filter { it.pageIndex !in refreshedPageIndexes && it.status == AiTranslationPageStatus.RUNNING }

    return refreshed.copy(pages = mergedPages.sortedBy { it.pageIndex })
}

internal fun mergeAiTranslationPageUpdate(
    current: AiTranslatedBook?,
    page: AiTranslatedPage
): AiTranslatedBook? {
    if (current == null) return null
    val existing = current.pages.firstOrNull { it.pageIndex == page.pageIndex }
    if (
        page.status == AiTranslationPageStatus.RUNNING &&
        existing?.status != null &&
        existing.status != AiTranslationPageStatus.RUNNING
    ) {
        return current
    }
    if (existing?.status == AiTranslationPageStatus.DONE && page.status != AiTranslationPageStatus.DONE) return current
    val existingDoneByRegion = existing
        ?.blocks
        .orEmpty()
        .filter { it.localRegionId.isNotBlank() && it.regionStatus == AiTranslationRegionStatus.DONE }
        .associateBy { it.localRegionId }
    val incomingRegionIds = page.blocks.map { it.localRegionId }.toSet()
    val mergedPage = page.copy(
        blocks = page.blocks.map { incoming ->
            if (incoming.regionStatus == AiTranslationRegionStatus.DONE) {
                incoming
            } else {
                existingDoneByRegion[incoming.localRegionId] ?: incoming
            }
        } + existingDoneByRegion.values.filter { it.localRegionId !in incomingRegionIds }
    )
    return current.copy(
        pages = (current.pages.filterNot { it.pageIndex == page.pageIndex } + mergedPage)
            .sortedBy { it.pageIndex }
    )
}
