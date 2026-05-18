package fail.tiger.komgarot.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import kotlinx.coroutines.launch

class BookViewModel(
    private val bookRepo: BookRepository,
    private val seriesRepo: SeriesRepository
) : ViewModel() {
    val books = mutableStateListOf<BookDto>()
    var series by mutableStateOf<SeriesDto?>(null)
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 0
    private var seriesId: String = ""
    private var initialized = false

    fun init(id: String) {
        if (seriesId != id) {
            seriesId = id
            books.clear()
            series = null
            page = 0
            hasMore = true
            initialized = false
            error = null
            viewModelScope.launch {
                seriesRepo.getSeriesById(id)
                    .onSuccess { series = it }
                    .onFailure { error = it.message ?: "加载系列详情失败" }
            }
        }
        if (!initialized) {
            initialized = true
            loadMore()
        }
    }

    fun refresh() {
        books.clear()
        page = 0
        hasMore = true
        error = null
        loadMore()
    }

    fun loadMore() {
        if (!hasMore || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { bookRepo.getBooks(seriesId, page) }
                .onSuccess {
                    val existingIds = books.map { item -> item.id }.toSet()
                    val newItems = it.content.filter { item -> item.id !in existingIds }
                    books.addAll(newItems)
                    hasMore = page < it.totalPages - 1
                    page++
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载书籍失败"
                }
            loading = false
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
