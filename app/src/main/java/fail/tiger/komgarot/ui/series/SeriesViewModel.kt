package fail.tiger.komgarot.ui.series

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.SeriesFilters
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.ui.state.PagedListState
import kotlinx.coroutines.launch

interface SeriesSortStore {
    fun load(): String
    fun save(sort: String)
}

class SharedPreferencesSeriesSortStore(context: Context) : SeriesSortStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("komgarot_prefs", Context.MODE_PRIVATE)

    override fun load(): String =
        prefs.getString(SERIES_SORT_KEY, DEFAULT_SERIES_SORT) ?: DEFAULT_SERIES_SORT

    override fun save(sort: String) {
        prefs.edit { putString(SERIES_SORT_KEY, sort) }
    }

    private companion object {
        const val SERIES_SORT_KEY = "series_sort"
    }
}

class SeriesViewModel(private val repo: SeriesRepository, private val sortStore: SeriesSortStore) : ViewModel() {
    private val paging = PagedListState<SeriesDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = "加载系列失败"
    )
    val series = paging.items
    val hasMore: Boolean get() = paging.hasMore
    val loading: Boolean get() = paging.loading
    val error: String? get() = paging.error
    var searchQuery by mutableStateOf("")
    var searchByAuthor by mutableStateOf(false)
    var currentSort by mutableStateOf(sortStore.load())
    var filters by mutableStateOf(SeriesFilters())
    val displaySearchQuery: String
        get() = searchQuery.stripAuthorPrefix().substringBefore(',').trim()
    val activeFilterCount: Int get() = filters.activeCount
    private var libraryId: String? = null
    private var initialized = false

    fun init(id: String?, initialSearch: String? = null) {
        val normalizedInitialSearch = initialSearch?.trim().orEmpty()
        if (libraryId != id) {
            libraryId = id
            paging.reset()
            initialized = false
        }
        if (normalizedInitialSearch != searchQuery) {
            initialized = false
            paging.reset()
            applySearchState(normalizedInitialSearch)
        }
        if (!initialized) {
            initialized = true
            loadMore()
        }
    }

    fun search(query: String, byAuthor: Boolean = false) {
        applySearchState(query, byAuthor)
        paging.reset()
        loadMore()
    }

    private fun applySearchState(query: String, byAuthor: Boolean = false) {
        val trimmedQuery = query.trim()
        val hasAuthorPrefix = trimmedQuery.startsWith("author:", ignoreCase = true)
        val cleanQuery = trimmedQuery.stripAuthorPrefix()
        searchByAuthor = byAuthor || hasAuthorPrefix
        searchQuery = if (searchByAuthor && cleanQuery.isNotEmpty()) {
            "author:$cleanQuery"
        } else {
            cleanQuery
        }
    }

    fun setSortBy(sort: String) {
        currentSort = sort
        sortStore.save(sort)
        paging.reset()
        loadMore()
    }

    fun applyFilters(value: SeriesFilters) {
        filters = value
        paging.reset()
        loadMore()
    }

    fun clearFilters() {
        applyFilters(SeriesFilters())
    }

    fun refresh() {
        paging.reset()
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch {
            paging.loadMore { page ->
                repo.getSeries(libraryId, page, searchQuery.ifEmpty { null }, currentSort, filters)
            }
        }
    }

    class Factory(private val repo: SeriesRepository, private val sortStore: SeriesSortStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SeriesViewModel(repo, sortStore) as T
    }
}

private const val DEFAULT_SERIES_SORT = "metadata.titleSort,asc"

private fun String.stripAuthorPrefix(): String =
    if (startsWith("author:", ignoreCase = true)) substringAfter(':').trim() else trim()
