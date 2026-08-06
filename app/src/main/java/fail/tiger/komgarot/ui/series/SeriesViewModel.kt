package fail.tiger.komgarot.ui.series

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.BookRepository
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
    private val bookRepo: BookRepository,
    private val sortStore: SeriesSortStore,
    fallbackErrorMessage: String,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val paging = PagedListState<SeriesDto, String>(
        keySelector = { it.id },
        fallbackErrorMessage = fallbackErrorMessage
    )
    val series = paging.items
    val hasMore: Boolean get() = paging.hasMore
    val loading: Boolean get() = paging.loading
    val error: String? get() = paging.error
    val hasLoadedOnce: Boolean get() = paging.hasLoadedOnce
    var searchQuery by mutableStateOf("")
    var searchByAuthor by mutableStateOf(false)
    var currentSort by mutableStateOf(sortStore.load())
    var filters by mutableStateOf(SeriesFilters())
    val displaySearchQuery: String
        get() = searchQuery.stripAuthorPrefix().substringBefore(',').trim()
    val activeFilterCount: Int get() = filters.activeCount
    private var libraryId: String? = null
    private var initialized = false
    private var scrollRestorationPending = savedScrollIndex > 0 || savedScrollOffset > 0
    private val oneShotTitleOverrides = mutableStateMapOf<String, String>()
    private val requestedOneShotTitles = mutableSetOf<String>()

    fun init(id: String?, initialSearch: String? = null, initialTag: String? = null) {
        val normalizedInitialSearch = initialSearch?.trim().orEmpty()
        val normalizedInitialTag = initialTag?.trim()?.takeIf { it.isNotEmpty() }
        val libraryChanged = libraryId != id
        if (libraryChanged) {
            libraryId = id
            resetPaging()
            initialized = false
        }
        if (shouldApplySeriesInitialSearch(libraryChanged, initialSearch, searchQuery)) {
            initialized = false
            resetPaging()
            applySearchState(normalizedInitialSearch)
        }
        if (shouldApplySeriesInitialTag(libraryChanged, initialTag, filters.tag)) {
            initialized = false
            resetPaging()
            filters = filters.copy(tag = normalizedInitialTag)
        }
        if (!initialized) {
            initialized = true
            loadMore()
        }
    }

    fun search(query: String, byAuthor: Boolean = false) {
        applySearchState(query, byAuthor)
        resetPaging()
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
        resetPaging()
        loadMore()
    }

    fun applyFilters(value: SeriesFilters) {
        filters = value
        resetPaging()
        loadMore()
    }

    fun clearFilters() {
        applyFilters(SeriesFilters())
    }

    fun refresh() {
        resetPaging()
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch {
            paging.loadMore { page ->
                repo.getSeries(libraryId, page, searchQuery.ifEmpty { null }, currentSort, filters)
                    .also { pageResult -> requestOneShotTitles(pageResult.content) }
            }
            if (scrollRestorationPending && series.size <= savedScrollIndex && hasMore) {
                loadMore()
            }
        }
    }

    val savedScrollIndex: Int
        get() = savedStateHandle[SERIES_SCROLL_INDEX_KEY] ?: 0

    val savedScrollOffset: Int
        get() = savedStateHandle[SERIES_SCROLL_OFFSET_KEY] ?: 0

    internal fun pendingScrollRestoration(): SeriesScrollPosition? {
        if (!scrollRestorationPending) return null
        return restoredSeriesScrollPosition(
            savedIndex = savedScrollIndex,
            savedOffset = savedScrollOffset,
            itemCount = series.size,
            hasMore = hasMore
        )
    }

    fun markScrollRestored() {
        scrollRestorationPending = false
    }

    fun updateScrollPosition(index: Int, offset: Int) {
        if (scrollRestorationPending) return
        savedStateHandle[SERIES_SCROLL_INDEX_KEY] = index.coerceAtLeast(0)
        savedStateHandle[SERIES_SCROLL_OFFSET_KEY] = offset.coerceAtLeast(0)
    }

    fun displayTitle(series: SeriesDto): String =
        seriesDisplayTitle(series, oneShotTitleOverrides)

    fun refreshVisibleOneShotTitles() {
        val currentOneShots = series.filter { it.shouldResolveOneShotBookTitle() }
        currentOneShots.forEach {
            requestedOneShotTitles.remove(it.id)
            oneShotTitleOverrides.remove(it.id)
        }
        requestOneShotTitles(currentOneShots)
    }

    fun resumeAfterBackground() {
        if (series.isEmpty() && !hasLoadedOnce) {
            resetPaging()
            loadMore()
        } else {
            refreshVisibleOneShotTitles()
        }
    }

    private fun resetPaging() {
        paging.reset()
        oneShotTitleOverrides.clear()
        requestedOneShotTitles.clear()
    }

    private fun requestOneShotTitles(items: List<SeriesDto>) {
        items
            .filter { it.shouldResolveOneShotBookTitle() }
            .filter { requestedOneShotTitles.add(it.id) }
            .forEach { series ->
                viewModelScope.launch {
                    val title = runCatching {
                        bookRepo.getBooks(series.id, 0).content.firstOrNull()?.displayTitle()
                    }.getOrNull()
                    if (title.isNullOrBlank()) {
                        requestedOneShotTitles.remove(series.id)
                    } else {
                        oneShotTitleOverrides[series.id] = title
                    }
                }
            }
    }

    class Factory(
        repo: SeriesRepository,
        bookRepo: BookRepository,
        sortStore: SeriesSortStore,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            SeriesViewModel(
                repo,
                bookRepo,
                sortStore,
                textProvider.get(R.string.error_load_series_failed),
                createSavedStateHandle()
            )
        }
    })
}

private const val DEFAULT_SERIES_SORT = "metadata.titleSort,asc"
private const val SERIES_SCROLL_INDEX_KEY = "series_scroll_index"
private const val SERIES_SCROLL_OFFSET_KEY = "series_scroll_offset"

internal data class SeriesScrollPosition(val index: Int, val offset: Int)

internal fun restoredSeriesScrollPosition(
    savedIndex: Int,
    savedOffset: Int,
    itemCount: Int,
    hasMore: Boolean
): SeriesScrollPosition? {
    if (itemCount <= 0) return null
    val normalizedIndex = savedIndex.coerceAtLeast(0)
    if (normalizedIndex < itemCount) {
        return SeriesScrollPosition(normalizedIndex, savedOffset.coerceAtLeast(0))
    }
    if (hasMore) return null
    return SeriesScrollPosition(itemCount - 1, 0)
}

internal fun seriesDisplayTitle(series: SeriesDto, oneShotTitleOverrides: Map<String, String>): String {
    val oneShotTitle = if (series.shouldResolveOneShotBookTitle()) {
        oneShotTitleOverrides[series.id]?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return oneShotTitle ?: series.metadata.title.ifEmpty { series.name }
}

private fun SeriesDto.shouldResolveOneShotBookTitle(): Boolean =
    oneshot && booksCount == 1

private fun BookDto.displayTitle(): String =
    metadata.title.ifEmpty { name }

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
