package fail.tiger.komgarot.ui.bookdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val bookRepo: BookRepository,
    private val seriesRepo: SeriesRepository
) : ViewModel() {
    var book by mutableStateOf<BookDto?>(null)
    var series by mutableStateOf<SeriesDto?>(null)
    var metadata by mutableStateOf<BookMetadataDto?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var currentBookId = ""

    fun load(bookId: String) {
        currentBookId = bookId
        viewModelScope.launch {
            loading = true
            error = null
            bookRepo.getBookById(bookId)
                .onSuccess {
                    book = it
                    metadata = it.metadata
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载书籍详情失败"
                }
            loading = false
        }
    }

    fun refresh() {
        book = null
        metadata = null
        ThumbnailVersion.bump(currentBookId)
        load(currentBookId)
    }

    fun markRead() {
        val loaded = book ?: return
        viewModelScope.launch {
            runCatching { bookRepo.updateReadProgress(loaded.id, loaded.media.pagesCount.coerceAtLeast(1), completed = true) }
                .onSuccess { load(loaded.id) }
        }
    }

    fun markUnread() {
        val loaded = book ?: return
        viewModelScope.launch {
            runCatching { bookRepo.deleteBookReadProgress(loaded.id) }
                .onSuccess { load(loaded.id) }
        }
    }

    class Factory(
        private val bookRepo: BookRepository,
        private val seriesRepo: SeriesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(bookRepo, seriesRepo) as T
    }
}
