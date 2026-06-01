package fail.tiger.komgarot.ui.readlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.ReadListDto
import fail.tiger.komgarot.data.repository.ReadListRepository
import fail.tiger.komgarot.ui.state.PagedListState
import kotlinx.coroutines.launch

class ReadListViewModel(private val repo: ReadListRepository) : ViewModel() {
    private val readListPaging = PagedListState<ReadListDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载阅读列表失败"
    )
    private val bookPaging = PagedListState<BookDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载阅读列表内容失败"
    )
    val readLists = readListPaging.items
    val books = bookPaging.items
    var selected by mutableStateOf<ReadListDto?>(null)
    var search by mutableStateOf("")
    private var selectedLoading by mutableStateOf(false)
    private var selectedError by mutableStateOf<String?>(null)
    val loading: Boolean get() = selectedLoading || readListPaging.loading || bookPaging.loading
    val error: String? get() = selectedError ?: readListPaging.error ?: bookPaging.error
    val hasMore: Boolean get() = readListPaging.hasMore
    val detailHasMore: Boolean get() = bookPaging.hasMore
    private var currentReadListId = ""
    private var listLoaded = false

    fun ensureListLoaded() {
        if (!listLoaded) refresh()
    }

    fun updateSearch(value: String) {
        search = value
        refresh()
    }

    fun refresh() {
        listLoaded = true
        readListPaging.reset()
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch {
            readListPaging.loadMore { page -> repo.getReadLists(page, search) }
        }
    }

    fun loadReadList(id: String, refresh: Boolean = false) {
        if (currentReadListId != id || refresh) {
            currentReadListId = id
            selected = null
            selectedError = null
            bookPaging.reset()
        }
        viewModelScope.launch {
            selectedLoading = true
            repo.getReadList(id)
                .onSuccess { selected = it }
                .onFailure { selectedError = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载阅读列表失败" }
            selectedLoading = false
            loadMoreBooks()
        }
    }

    fun loadMoreBooks() {
        if (currentReadListId.isEmpty()) return
        viewModelScope.launch {
            bookPaging.loadMore { page -> repo.getBooks(currentReadListId, page) }
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
