package fail.tiger.komgarot.ui.readlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.ReadListDto
import fail.tiger.komgarot.data.repository.ReadListRepository
import kotlinx.coroutines.launch

class ReadListViewModel(private val repo: ReadListRepository) : ViewModel() {
    val readLists = mutableStateListOf<ReadListDto>()
    val books = mutableStateListOf<BookDto>()
    var selected by mutableStateOf<ReadListDto?>(null)
    var search by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    var detailHasMore by mutableStateOf(true)
    private var page = 0
    private var detailPage = 0
    private var currentReadListId = ""

    init { refresh() }

    fun updateSearch(value: String) {
        search = value
        refresh()
    }

    fun refresh() {
        readLists.clear()
        page = 0
        hasMore = true
        loadMore()
    }

    fun loadMore() {
        if (!hasMore || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.getReadLists(page, search) }
                .onSuccess {
                    readLists.addAll(it.content.filter { item -> readLists.none { existing -> existing.id == item.id } })
                    hasMore = page < it.totalPages - 1
                    page++
                }
                .onFailure { error = it.message ?: "加载阅读列表失败" }
            loading = false
        }
    }

    fun loadReadList(id: String) {
        if (currentReadListId != id) {
            currentReadListId = id
            selected = null
            books.clear()
            detailPage = 0
            detailHasMore = true
        }
        viewModelScope.launch {
            loading = true
            error = null
            repo.getReadList(id).onSuccess { selected = it }.onFailure { error = it.message ?: "加载阅读列表失败" }
            loading = false
            loadMoreBooks()
        }
    }

    fun loadMoreBooks() {
        if (currentReadListId.isEmpty() || !detailHasMore || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.getBooks(currentReadListId, detailPage) }
                .onSuccess {
                    books.addAll(it.content.filter { item -> books.none { existing -> existing.id == item.id } })
                    detailHasMore = detailPage < it.totalPages - 1
                    detailPage++
                }
                .onFailure { error = it.message ?: "加载阅读列表内容失败" }
            loading = false
        }
    }

    fun create(name: String, summary: String, ordered: Boolean, bookIds: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.create(name, summary, ordered, bookIds).onSuccess { refresh() }.isSuccess
            onDone(ok)
        }
    }

    fun update(id: String, name: String, summary: String, ordered: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.update(id, name = name, summary = summary, ordered = ordered).onSuccess { loadReadList(id) }.isSuccess
            onDone(ok)
        }
    }

    fun delete(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.delete(id).onSuccess { refresh() }.isSuccess
            onDone(ok)
        }
    }

    class Factory(private val repo: ReadListRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReadListViewModel(repo) as T
    }
}
