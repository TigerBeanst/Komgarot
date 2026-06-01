package fail.tiger.komgarot.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto
import fail.tiger.komgarot.data.repository.BookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PageImageInfo(val bookId: String, val seriesId: String, val pageUrl: String)

enum class ReadingMode { PAGER, SCROLL }

private val directImageMediaTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/jxl",
    "image/heif",
    "image/avif"
)

private fun readerPageUrl(serverUrl: String, bookId: String, page: PageDto): String {
    val url = KomgaUrls.page(serverUrl, bookId, page.number)
    return if (page.mediaType.lowercase() in directImageMediaTypes) url else "$url?convert=png"
}

class ReaderViewModel(private val repo: BookRepository, val prefs: AuthPreferences) : ViewModel() {
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
            repo.getBookById(bookId)
                .onSuccess { loadedBook ->
                    book = loadedBook
                    currentSeriesId = loadedBook.seriesId
                    previousBook = repo.getPreviousBook(bookId).getOrNull()
                    nextBook = repo.getNextBook(bookId).getOrNull()
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载书籍失败"
                }

            runCatching { repo.getPages(bookId) }
                .onSuccess { pages ->
                    pageUrls = pages.map { readerPageUrl(base, bookId, it) }
                    currentPage = if (pageUrls.isEmpty()) 0 else (startPage - 1).coerceIn(0, pageUrls.lastIndex)
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载页面失败"
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

    fun uploadBookThumbnail(imageBytes: ByteArray, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.uploadBookThumbnail(currentBookId, imageBytes, "image/jpeg") }.isSuccess
            if (ok) ThumbnailVersion.bump(currentBookId)
            onDone(ok)
        }
    }

    fun uploadSeriesThumbnail(imageBytes: ByteArray, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.uploadSeriesThumbnail(currentSeriesId, imageBytes, "image/jpeg") }.isSuccess
            if (ok) ThumbnailVersion.bump(currentSeriesId)
            onDone(ok)
        }
    }

    class Factory(private val repo: BookRepository, private val prefs: AuthPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(repo, prefs) as T
    }
}
