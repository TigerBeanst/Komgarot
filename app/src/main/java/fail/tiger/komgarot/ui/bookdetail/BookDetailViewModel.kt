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
    private var currentBookId = ""

    fun load(bookId: String) {
        currentBookId = bookId
        viewModelScope.launch {
            loading = true
            book = bookRepo.getBookById(bookId).getOrNull()
            book?.let { metadata = it.metadata }
            loading = false
        }
    }

    fun refresh() {
        book = null
        metadata = null
        ThumbnailVersion.bump(currentBookId)
        load(currentBookId)
    }

    class Factory(
        private val bookRepo: BookRepository,
        private val seriesRepo: SeriesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(bookRepo, seriesRepo) as T
    }
}
