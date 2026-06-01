package fail.tiger.komgarot.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.ui.state.PagedListState
import kotlinx.coroutines.launch

class BookViewModel(
    private val bookRepo: BookRepository,
    private val seriesRepo: SeriesRepository
) : ViewModel() {
    private val paging = PagedListState<BookDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载书籍失败"
    )
    val books = paging.items
    var series by mutableStateOf<SeriesDto?>(null)
    val hasMore: Boolean get() = paging.hasMore
    val loading: Boolean get() = paging.loading
    val error: String? get() = paging.error
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
        paging.reset()
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch {
            paging.loadMore { page -> bookRepo.getBooks(seriesId, page) }
        }
    }

    class Factory(
        private val bookRepo: BookRepository,
        private val seriesRepo: SeriesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookViewModel(bookRepo, seriesRepo) as T
    }
}
