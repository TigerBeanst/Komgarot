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
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto
import fail.tiger.komgarot.data.repository.AiTranslationRepository
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PageImageInfo(val bookId: String, val seriesId: String, val pageUrl: String)

enum class ReadingMode { PAGER, SCROLL }

class ReaderViewModel(
    private val repo: BookRepository,
    val prefs: AuthPreferences,
    private val loadBookFailed: String,
    private val loadPagesFailed: String,
    private val aiTranslationRepository: AiTranslationRepository? = null
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

    fun load(bookId: String, startPage: Int, trackProgress: Boolean = true) {
        progressJob?.cancel()
        this.trackProgress = trackProgress
        if (currentBookId != bookId) {
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
        scheduleProgressUpdate()
    }

    fun currentAiTranslatedPage(pageIndex: Int): AiTranslatedPage? =
        aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == pageIndex }

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

    fun retryCurrentAiTranslationPage() {
        val loaded = book ?: return
        val repository = aiTranslationRepository ?: run {
            publishAiTranslationMessage(R.string.ai_translate_config_required)
            return
        }
        aiTranslatedBook = repository.readBookState(loaded.id) ?: localAiBookShell(loaded)
        currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON
        viewModelScope.launch {
            prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)
        }
        updateCurrentAiTranslationPageStatus(AiTranslationPageStatus.RUNNING)
        publishAiTranslationMessage(R.string.reader_ai_retry_started)
        viewModelScope.launch {
            val result = repository.retryPageTranslation(loaded, currentServerUrl, currentPage, currentPages)
            aiTranslatedBook = repository.readBookState(loaded.id)
            currentAiTranslationMode = repository.preferredModeForBook(loaded.id)
            val updatedPage = aiTranslatedBook?.pages?.firstOrNull { it.pageIndex == currentPage }
            val pageUpdated = result.ok && updatedPage?.status == AiTranslationPageStatus.DONE
            if (!pageUpdated) updateCurrentAiTranslationPageStatus(AiTranslationPageStatus.FAILED)
            if (pageUpdated) {
                publishAiTranslationMessage(R.string.reader_ai_retry_success)
            } else {
                val failureSummary = updatedPage?.errorSummary?.takeIf { it.isNotBlank() } ?: result.summary.takeIf { it.isNotBlank() }
                publishAiTranslationMessage(
                    R.string.reader_ai_retry_failed,
                    failureSummary ?: buildAiRetryFallbackSummary(loaded, updatedPage, result)
                )
            }
        }
    }

    fun deleteCurrentAiTranslationPage() {
        val loaded = book ?: return
        aiTranslationRepository?.deletePageTranslation(loaded.id, currentPage)
        aiTranslatedBook = aiTranslationRepository?.readBookState(loaded.id)
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
        val existing = aiTranslatedBook ?: return
        val current = existing.pages.firstOrNull { it.pageIndex == currentPage }
        val updated = (current ?: AiTranslatedPage(pageIndex = currentPage)).copy(
            status = status,
            errorSummary = if (status == AiTranslationPageStatus.RUNNING) "" else current?.errorSummary.orEmpty()
        )
        aiTranslatedBook = existing.copy(
            pages = (existing.pages.filterNot { it.pageIndex == currentPage } + updated)
                .sortedBy { it.pageIndex }
        )
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

    private fun publishAiTranslationMessage(messageRes: Int, messageText: String = "") {
        aiTranslationMessageRes = messageRes
        aiTranslationMessageText = messageText
        aiTranslationMessageNonce += 1
    }

    private fun buildAiRetryFallbackSummary(
        loaded: BookDto,
        updatedPage: AiTranslatedPage?,
        result: fail.tiger.komgarot.data.repository.AiTranslationPageActionResult
    ): String =
        "AI translation failed: book=${loaded.id}, page=$currentPage, resultOk=${result.ok}, savedStatus=${updatedPage?.status?.name ?: "missing"}"

    fun goToPage(page: Int) {
        if (pageUrls.isEmpty()) return
        currentPage = page.coerceIn(0, pageUrls.size - 1)
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
        aiTranslationRepository: AiTranslationRepository? = null
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            ReaderViewModel(
                repo,
                prefs,
                textProvider.get(R.string.error_load_books_failed),
                textProvider.get(R.string.error_load_pages_failed),
                aiTranslationRepository
            )
        }
    })
}

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
