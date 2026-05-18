package fail.tiger.komgarot.ui.series

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.SeriesRepository
import kotlinx.coroutines.launch

class SeriesViewModel(private val repo: SeriesRepository, private val context: Context) : ViewModel() {
    val series = mutableStateListOf<SeriesDto>()
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var searchByAuthor by mutableStateOf(false)
    var currentSort by mutableStateOf(loadSortPreference())
    val displaySearchQuery: String
        get() = searchQuery.stripAuthorPrefix().substringBefore(',').trim()
    private var page = 0
    private var libraryId: String? = null
    private var initialized = false

    private fun loadSortPreference(): String {
        val prefs = context.getSharedPreferences("komgarot_prefs", Context.MODE_PRIVATE)
        return prefs.getString("series_sort", "metadata.titleSort,asc") ?: "metadata.titleSort,asc"
    }

    private fun saveSortPreference(sort: String) {
        val prefs = context.getSharedPreferences("komgarot_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("series_sort", sort).apply()
    }

    fun init(id: String?) {
        if (libraryId != id) {
            libraryId = id
            series.clear()
            page = 0
            hasMore = true
            initialized = false
            error = null
        }
        if (!initialized) {
            initialized = true
            loadMore()
        }
    }

    fun search(query: String, byAuthor: Boolean = false) {
        val trimmedQuery = query.trim()
        val hasAuthorPrefix = trimmedQuery.startsWith("author:", ignoreCase = true)
        val cleanQuery = trimmedQuery.stripAuthorPrefix()
        searchByAuthor = byAuthor || hasAuthorPrefix
        searchQuery = if (searchByAuthor && cleanQuery.isNotEmpty()) {
            "author:$cleanQuery"
        } else {
            cleanQuery
        }
        series.clear()
        page = 0
        hasMore = true
        error = null
        loadMore()
    }

    fun setSortBy(sort: String) {
        currentSort = sort
        saveSortPreference(sort)
        series.clear()
        page = 0
        hasMore = true
        error = null
        loadMore()
    }

    fun refresh() {
        series.clear()
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
            runCatching { repo.getSeries(libraryId, page, searchQuery.ifEmpty { null }, currentSort) }
                .onSuccess {
                    val existingIds = series.map { item -> item.id }.toSet()
                    val newItems = it.content.filter { item -> item.id !in existingIds }
                    series.addAll(newItems)
                    hasMore = page < it.totalPages - 1
                    page++
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: "加载系列失败"
                }
            loading = false
        }
    }

    class Factory(private val repo: SeriesRepository, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SeriesViewModel(repo, context) as T
    }
}

private fun String.stripAuthorPrefix(): String =
    if (startsWith("author:", ignoreCase = true)) substringAfter(':').trim() else trim()
