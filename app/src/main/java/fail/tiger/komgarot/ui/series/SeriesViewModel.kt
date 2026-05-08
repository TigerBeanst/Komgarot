package fail.tiger.komgarot.ui.series

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.SeriesRepository
import kotlinx.coroutines.launch

class SeriesViewModel(private val repo: SeriesRepository, private val context: Context) : ViewModel() {
    val series = mutableStateListOf<SeriesDto>()
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var currentSort by mutableStateOf(loadSortPreference())
    private var page = 0
    private var libraryId: String? = null

    private fun loadSortPreference(): String {
        val prefs = context.getSharedPreferences("komgarot_prefs", Context.MODE_PRIVATE)
        return prefs.getString("series_sort", "metadata.titleSort,asc") ?: "metadata.titleSort,asc"
    }

    private fun saveSortPreference(sort: String) {
        val prefs = context.getSharedPreferences("komgarot_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("series_sort", sort).apply()
    }

    fun init(id: String?) {
        if (libraryId != id) { libraryId = id; series.clear(); page = 0; hasMore = true }
        if (series.isEmpty()) loadMore()
    }

    fun search(query: String) {
        searchQuery = if (query.startsWith("author:")) {
            query
        } else {
            query.trim()
        }
        series.clear()
        page = 0
        hasMore = true
        loadMore()
    }

    fun setSortBy(sort: String) {
        currentSort = sort
        saveSortPreference(sort)
        series.clear()
        page = 0
        hasMore = true
        loadMore()
    }

    fun refresh() {
        series.clear()
        page = 0
        hasMore = true
        loadMore()
    }

    fun loadMore() {
        if (!hasMore || loading) return
        viewModelScope.launch {
            loading = true
            runCatching { repo.getSeries(libraryId, page, searchQuery.ifEmpty { null }, currentSort) }.onSuccess {
                series.addAll(it.content)
                hasMore = page < it.totalPages - 1
                page++
            }
            loading = false
        }
    }

    class Factory(private val repo: SeriesRepository, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SeriesViewModel(repo, context) as T
    }
}
