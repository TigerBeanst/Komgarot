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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.SeriesFilters
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
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

class SeriesViewModel(
    private val repo: SeriesRepository,
    private val sortStore: SeriesSortStore,
    fallbackErrorMessage: String
) : ViewModel() {
    private val paging = PagedListState<SeriesDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = fallbackErrorMessage
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

    fun init(id: String?, initialSearch: String? = null, initialTag: String? = null) {
        val normalizedInitialSearch = initialSearch?.trim().orEmpty()
        val normalizedInitialTag = initialTag?.trim()?.takeIf { it.isNotEmpty() }
        val libraryChanged = libraryId != id
        if (libraryChanged) {
            libraryId = id
            paging.reset()
            initialized = false
        }
        if (shouldApplySeriesInitialSearch(libraryChanged, initialSearch, searchQuery)) {
            initialized = false
            paging.reset()
            applySearchState(normalizedInitialSearch)
        }
        if (shouldApplySeriesInitialTag(libraryChanged, initialTag, filters.tag)) {
            initialized = false
            paging.reset()
            filters = filters.copy(tag = normalizedInitialTag)
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

    class Factory(
        repo: SeriesRepository,
        sortStore: SeriesSortStore,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer { SeriesViewModel(repo, sortStore, textProvider.get(R.string.error_load_series_failed)) }
    })
}

private const val DEFAULT_SERIES_SORT = "metadata.titleSort,asc"

internal fun shouldApplySeriesInitialSearch(
    libraryChanged: Boolean,
    initialSearch: String?,
    currentSearch: String
): Boolean {
    if (libraryChanged) return true
    return initialSearch != null && initialSearch.trim() != currentSearch
}

internal fun shouldApplySeriesInitialTag(
    libraryChanged: Boolean,
    initialTag: String?,
    currentTag: String?
): Boolean {
    if (libraryChanged) return true
    return initialTag != null && initialTag.trim().takeIf { it.isNotEmpty() } != currentTag
}

private fun String.stripAuthorPrefix(): String =
    if (startsWith("author:", ignoreCase = true)) substringAfter(':').trim() else trim()
