package fail.tiger.komgarot.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.repository.BookRepository
import kotlinx.coroutines.launch

data class PageImageInfo(val bookId: String, val seriesId: String, val pageUrl: String)

enum class ReadingMode { PAGER, SCROLL }

class ReaderViewModel(private val repo: BookRepository, val prefs: AuthPreferences) : ViewModel() {
    var pageUrls by mutableStateOf<List<String>>(emptyList())
    var currentPage by mutableIntStateOf(0)
    var mode by mutableStateOf(ReadingMode.PAGER)
    var showControls by mutableStateOf(true)
    var trackProgress by mutableStateOf(true)
    var currentBookId: String = ""
        private set
    var currentSeriesId: String = ""
        private set

    fun load(bookId: String, startPage: Int, trackProgress: Boolean = true) {
        this.trackProgress = trackProgress
        this.currentBookId = bookId
        viewModelScope.launch {
            val base = prefs.serverUrlBlocking
            runCatching { repo.getBookById(bookId).getOrThrow() }.onSuccess { book ->
                currentSeriesId = book.seriesId
            }
            runCatching { repo.getPages(bookId) }.onSuccess { pages ->
                pageUrls = pages.map { "$base/api/v1/books/$bookId/pages/${it.number}" }
                currentPage = (startPage - 1).coerceIn(0, pageUrls.size - 1)
            }
        }
    }

    fun toggleControls() { showControls = !showControls }
    fun toggleMode() { mode = if (mode == ReadingMode.PAGER) ReadingMode.SCROLL else ReadingMode.PAGER }

    fun updatePage(page: Int) {
        currentPage = page
        if (trackProgress) {
            viewModelScope.launch {
                runCatching {
                    repo.updateReadProgress(currentBookId, currentPage + 1, currentPage == pageUrls.size - 1)
                }
            }
        }
    }

    fun goToPage(page: Int) {
        currentPage = page.coerceIn(0, pageUrls.size - 1)
        if (trackProgress) {
            viewModelScope.launch {
                runCatching {
                    repo.updateReadProgress(currentBookId, currentPage + 1, currentPage == pageUrls.size - 1)
                }
            }
        }
    }

    fun uploadBookThumbnail(imageBytes: ByteArray, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.uploadBookThumbnail(currentBookId, imageBytes, "image/jpeg") }.isSuccess
            onDone(ok)
        }
    }

    fun uploadSeriesThumbnail(imageBytes: ByteArray, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.uploadSeriesThumbnail(currentSeriesId, imageBytes, "image/jpeg") }.isSuccess
            onDone(ok)
        }
    }

    class Factory(private val repo: BookRepository, private val prefs: AuthPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(repo, prefs) as T
    }
}
