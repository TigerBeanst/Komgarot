package fail.tiger.komgarot.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.CollectionDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.CollectionRepository
import kotlinx.coroutines.launch

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    val collections = mutableStateListOf<CollectionDto>()
    val series = mutableStateListOf<SeriesDto>()
    var selected by mutableStateOf<CollectionDto?>(null)
    var search by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    var detailHasMore by mutableStateOf(true)
    private var page = 0
    private var detailPage = 0
    private var currentCollectionId = ""

    init { refresh() }

    fun updateSearch(value: String) {
        search = value
        refresh()
    }

    fun refresh() {
        collections.clear()
        page = 0
        hasMore = true
        loadMore()
    }

    fun loadMore() {
        if (!hasMore || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.getCollections(page, search) }
                .onSuccess {
                    collections.addAll(it.content.filter { item -> collections.none { existing -> existing.id == item.id } })
                    hasMore = page < it.totalPages - 1
                    page++
                }
                .onFailure { error = it.message ?: "加载集合失败" }
            loading = false
        }
    }

    fun loadCollection(id: String) {
        if (currentCollectionId != id) {
            currentCollectionId = id
            selected = null
            series.clear()
            detailPage = 0
            detailHasMore = true
        }
        viewModelScope.launch {
            loading = true
            error = null
            repo.getCollection(id).onSuccess { selected = it }.onFailure { error = it.message ?: "加载集合失败" }
            loading = false
            loadMoreSeries()
        }
    }

    fun loadMoreSeries() {
        if (currentCollectionId.isEmpty() || !detailHasMore || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.getSeries(currentCollectionId, detailPage) }
                .onSuccess {
                    series.addAll(it.content.filter { item -> series.none { existing -> existing.id == item.id } })
                    detailHasMore = detailPage < it.totalPages - 1
                    detailPage++
                }
                .onFailure { error = it.message ?: "加载集合内容失败" }
            loading = false
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
