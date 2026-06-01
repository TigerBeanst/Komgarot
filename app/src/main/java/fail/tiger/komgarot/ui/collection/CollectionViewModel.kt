package fail.tiger.komgarot.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.CollectionDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.CollectionRepository
import fail.tiger.komgarot.ui.state.PagedListState
import kotlinx.coroutines.launch

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val collectionPaging = PagedListState<CollectionDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载集合失败"
    )
    private val seriesPaging = PagedListState<SeriesDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载集合内容失败"
    )
    val collections = collectionPaging.items
    val series = seriesPaging.items
    var selected by mutableStateOf<CollectionDto?>(null)
    var search by mutableStateOf("")
    private var selectedLoading by mutableStateOf(false)
    private var selectedError by mutableStateOf<String?>(null)
    val loading: Boolean get() = selectedLoading || collectionPaging.loading || seriesPaging.loading
    val error: String? get() = selectedError ?: collectionPaging.error ?: seriesPaging.error
    val hasMore: Boolean get() = collectionPaging.hasMore
    val detailHasMore: Boolean get() = seriesPaging.hasMore
    private var currentCollectionId = ""
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
        collectionPaging.reset()
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch {
            collectionPaging.loadMore { page -> repo.getCollections(page, search) }
        }
    }

    fun loadCollection(id: String, refresh: Boolean = false) {
        if (currentCollectionId != id || refresh) {
            currentCollectionId = id
            selected = null
            selectedError = null
            seriesPaging.reset()
        }
        viewModelScope.launch {
            selectedLoading = true
            repo.getCollection(id)
                .onSuccess { selected = it }
                .onFailure { selectedError = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载集合失败" }
            selectedLoading = false
            loadMoreSeries()
        }
    }

    fun loadMoreSeries() {
        if (currentCollectionId.isEmpty()) return
        viewModelScope.launch {
            seriesPaging.loadMore { page -> repo.getSeries(currentCollectionId, page) }
        }
    }

    fun create(name: String, ordered: Boolean, seriesIds: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.create(name, ordered, seriesIds).onSuccess { refresh() }.isSuccess
            onDone(ok)
        }
    }

    fun update(id: String, name: String, ordered: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.update(id, name = name, ordered = ordered).onSuccess { loadCollection(id) }.isSuccess
            onDone(ok)
        }
    }

    fun delete(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.delete(id).onSuccess { refresh() }.isSuccess
            onDone(ok)
        }
    }

    class Factory(private val repo: CollectionRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(repo) as T
    }
}
