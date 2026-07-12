package fail.tiger.komgarot.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.ImageCacheInvalidator
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import fail.tiger.komgarot.ui.state.PagedListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookViewModel(
    private val bookRepo: BookRepository,
    private val seriesRepo: SeriesRepository,
    private val imageCacheInvalidator: ImageCacheInvalidator,
    fallbackErrorMessage: String
) : ViewModel() {
    private val paging = PagedListState<BookDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = fallbackErrorMessage
    )
    val books = paging.items
    var series by mutableStateOf<SeriesDto?>(null)
    val hasMore: Boolean get() = paging.hasMore
    val loading: Boolean get() = paging.loading
    val error: String? get() = paging.error
    val hasLoadedOnce: Boolean get() = paging.hasLoadedOnce
    private var seriesId: String = ""
    private var initialized = false

    fun init(id: String) {
        if (seriesId != id) {
            seriesId = id
            series = null
            paging.reset()
            initialized = false
            viewModelScope.launch {
                seriesRepo.getSeriesById(id)
                    .onSuccess { series = it }
            }
        }
        if (!initialized) {
            initialized = true
            loadMore()
        }
    }

    fun refresh() {
        imageCacheInvalidator.invalidateSeries(seriesId, books.map { it.id })
        refreshAllKnownBookThumbnails()
        viewModelScope.launch {
            paging.refresh { bookRepo.getBooks(seriesId, 0) }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            paging.loadMore { page -> bookRepo.getBooks(seriesId, page) }
        }
    }

    private fun refreshAllKnownBookThumbnails() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var page = 0
                do {
                    val result = bookRepo.getBooks(seriesId, page)
                    imageCacheInvalidator.invalidateBookCaches(result.content.map { it.id })
                    page++
                } while (page < result.totalPages)
            }
        }
    }

    class Factory(
        bookRepo: BookRepository,
        seriesRepo: SeriesRepository,
        imageCacheInvalidator: ImageCacheInvalidator,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            BookViewModel(
                bookRepo,
                seriesRepo,
                imageCacheInvalidator,
                textProvider.get(R.string.error_load_books_failed)
            )
        }
    })
}
