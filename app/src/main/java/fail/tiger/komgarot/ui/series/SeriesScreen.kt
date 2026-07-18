package fail.tiger.komgarot.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shadow
import fail.tiger.komgarot.ui.components.LazyGridScrollbar
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.ThumbnailImage
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.repository.SeriesFilters
import fail.tiger.komgarot.ui.components.AutoHideBottomBarOnLazyGridScroll
import fail.tiger.komgarot.ui.components.topLevelScrollableContentPadding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    libraryId: String?,
    initialSearch: String? = null,
    initialTag: String? = null,
    serverUrl: String,
    onSeriesClick: (String, Int, Boolean) -> Unit,
    onMetadataClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: SeriesViewModel,
    onBottomBarVisibleChange: (Boolean) -> Unit = {}
) {
    LaunchedEffect(libraryId, initialSearch, initialTag) { vm.init(libraryId, initialSearch, initialTag) }
    var hasSeenInitialResume by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasSeenInitialResume) {
            vm.refreshVisibleOneShotTitles()
        } else {
            hasSeenInitialResume = true
        }
    }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchByAuthor by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val sortField = vm.currentSort.substringBefore(",")
    val sortDirection = vm.currentSort.substringAfter(",")

    val listState = rememberLazyGridState()
    AutoHideBottomBarOnLazyGridScroll(listState, onBottomBarVisibleChange)
    LaunchedEffect(listState, vm) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= vm.series.size - 4) vm.loadMore()
            }
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    if (searchExpanded) {
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { Text(stringResource(if (searchByAuthor) R.string.search_author else R.string.search_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.search(searchText, searchByAuthor) }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                            )
                            FilterChip(
                                selected = searchByAuthor,
                                onClick = { searchByAuthor = !searchByAuthor },
                                label = { Text(stringResource(R.string.author_filter)) }
                            )
                        }
                    } else {
                        val title = when {
                            vm.searchQuery.isNotEmpty() && vm.searchByAuthor ->
                                stringResource(R.string.author_title, vm.displaySearchQuery)
                            vm.searchQuery.isNotEmpty() ->
                                stringResource(R.string.search_title, vm.displaySearchQuery)
                            libraryId == null -> stringResource(R.string.all_series)
                            else -> stringResource(R.string.series)
                        }
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchExpanded) {
                            searchExpanded = false
                            searchText = ""
                            searchByAuthor = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = { vm.search(searchText, searchByAuthor) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = {
                            searchExpanded = false
                            searchText = ""
                            searchByAuthor = false
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    } else {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort),
                                tint = if (vm.currentSort != "metadata.titleSort,asc") MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sort_menu_item,
                                            seriesSortLabel("metadata.titleSort"),
                                            activeSortIndicator(sortField, sortDirection, "metadata.titleSort")
                                        )
                                    )
                                },
                                onClick = {
                                    val newDir = if (sortField == "metadata.titleSort") if (sortDirection == "asc") "desc" else "asc" else "asc"
                                    vm.setSortBy("metadata.titleSort,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sort_menu_item,
                                            seriesSortLabel("created"),
                                            activeSortIndicator(sortField, sortDirection, "created")
                                        )
                                    )
                                },
                                onClick = {
                                    val newDir = if (sortField == "created") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("created,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sort_menu_item,
                                            seriesSortLabel("lastModified"),
                                            activeSortIndicator(sortField, sortDirection, "lastModified")
                                        )
                                    )
                                },
                                onClick = {
                                    val newDir = if (sortField == "lastModified") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastModified,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sort_menu_item,
                                            seriesSortLabel("lastReadDate"),
                                            activeSortIndicator(sortField, sortDirection, "lastReadDate")
                                        )
                                    )
                                },
                                onClick = {
                                    val newDir = if (sortField == "lastReadDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastReadDate,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sort_menu_item,
                                            seriesSortLabel("metadata.releaseDate"),
                                            activeSortIndicator(sortField, sortDirection, "metadata.releaseDate")
                                        )
                                    )
                                },
                                onClick = {
                                    val newDir = if (sortField == "metadata.releaseDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("metadata.releaseDate,$newDir"); sortMenuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_random)) }, onClick = { vm.setSortBy("random,asc"); sortMenuExpanded = false })
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter),
                                tint = if (vm.activeFilterCount > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = {
                            searchText = vm.displaySearchQuery
                            searchByAuthor = vm.searchByAuthor
                            searchExpanded = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = vm.loading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding())
                )
            },
            modifier = Modifier.padding(padding)
        ) {
            if (vm.series.isEmpty() && !vm.loading && vm.error != null) {
                ErrorState(message = vm.error ?: stringResource(R.string.error_load_series_failed), onRetry = vm::refresh)
            } else if (vm.series.isEmpty() && !vm.loading && vm.searchQuery.isNotEmpty()) {
                EmptyState(message = stringResource(R.string.no_search_results))
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        state = listState,
                        contentPadding = topLevelScrollableContentPadding(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vm.series, key = { it.id }) { series ->
                            val displayTitle = vm.displayTitle(series)
                            val thumbnailVersion = ThumbnailVersion.get(series.id)
                            val thumbnailUrl = remember(serverUrl, series.id, thumbnailVersion) {
                                KomgaUrls.seriesThumbnail(serverUrl, series.id, thumbnailVersion)
                            }
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSeriesClick(series.id, series.booksCount, series.oneshot)
                                }
                            ) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                                    ThumbnailImage(
                                        url = thumbnailUrl,
                                        cacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Series(series.id)),
                                        contentDescription = displayTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        Modifier.fillMaxWidth().align(Alignment.BottomStart)
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                                            .padding(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            displayTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                shadow = Shadow(
                                                    color = Color.Black,
                                                    blurRadius = 4f
                                                )
                                            ),
                                            color = Color.White,
                                            maxLines = 2
                                        )
                                    }
                                    if (series.booksCount > 1) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "${series.booksCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                LazyGridScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp)
                )
            }
            }
        }
    }

    if (showFilterSheet) {
        SeriesFilterSheet(
            filters = vm.filters,
            onDismiss = { showFilterSheet = false },
            onApply = {
                vm.applyFilters(it)
                showFilterSheet = false
            },
            onClear = {
                vm.clearFilters()
                showFilterSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SeriesFilterSheet(
    filters: SeriesFilters,
    onDismiss: () -> Unit,
    onApply: (SeriesFilters) -> Unit,
    onClear: () -> Unit
) {
    var readStatus by remember(filters) { mutableStateOf(filters.readStatus) }
    var status by remember(filters) { mutableStateOf(filters.status) }
    var complete by remember(filters) { mutableStateOf(filters.complete) }
    var oneshot by remember(filters) { mutableStateOf(filters.oneshot) }
    var recentRelease by remember(filters) { mutableStateOf(filters.releaseDateInLast != null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.filter_series_title), style = MaterialTheme.typography.titleLarge)
            FilterGroup(stringResource(R.string.filter_read_status)) {
                FilterChip(selected = readStatus == null, onClick = { readStatus = null }, label = { Text(stringResource(R.string.all)) })
                FilterChip(selected = readStatus == "UNREAD", onClick = { readStatus = "UNREAD" }, label = { Text(stringResource(R.string.read_status_unread)) })
                FilterChip(selected = readStatus == "IN_PROGRESS", onClick = { readStatus = "IN_PROGRESS" }, label = { Text(stringResource(R.string.read_status_in_progress)) })
                FilterChip(selected = readStatus == "READ", onClick = { readStatus = "READ" }, label = { Text(stringResource(R.string.read_status_read)) })
            }
            FilterGroup(stringResource(R.string.filter_series_status)) {
                FilterChip(selected = status == null, onClick = { status = null }, label = { Text(stringResource(R.string.all)) })
                FilterChip(selected = status == "ONGOING", onClick = { status = "ONGOING" }, label = { Text(stringResource(R.string.series_status_ongoing)) })
                FilterChip(selected = status == "ENDED", onClick = { status = "ENDED" }, label = { Text(stringResource(R.string.series_status_ended)) })
                FilterChip(selected = status == "HIATUS", onClick = { status = "HIATUS" }, label = { Text(stringResource(R.string.series_status_hiatus)) })
                FilterChip(selected = status == "ABANDONED", onClick = { status = "ABANDONED" }, label = { Text(stringResource(R.string.series_status_abandoned)) })
            }
            FilterGroup(stringResource(R.string.filter_content)) {
                FilterChip(selected = complete == true, onClick = { complete = if (complete == true) null else true }, label = { Text(stringResource(R.string.complete)) })
                FilterChip(selected = complete == false, onClick = { complete = if (complete == false) null else false }, label = { Text(stringResource(R.string.incomplete)) })
                FilterChip(selected = oneshot == true, onClick = { oneshot = if (oneshot == true) null else true }, label = { Text(stringResource(R.string.oneshot)) })
                FilterChip(selected = recentRelease, onClick = { recentRelease = !recentRelease }, label = { Text(stringResource(R.string.released_last_year)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear)) }
                Button(
                    onClick = {
                        onApply(
                            filters.copy(
                                readStatus = readStatus,
                                status = status,
                                complete = complete,
                                oneshot = oneshot,
                                releaseDateInLast = if (recentRelease) "P365D" else null
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.apply)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun seriesSortLabel(sortField: String): String =
    when (sortField) {
        "metadata.titleSort" -> stringResource(R.string.sort_name)
        "created" -> stringResource(R.string.sort_created)
        "lastModified" -> stringResource(R.string.sort_updated)
        "lastReadDate" -> stringResource(R.string.sort_last_read)
        "metadata.releaseDate" -> stringResource(R.string.sort_release_date)
        "random" -> stringResource(R.string.sort_random)
        else -> sortField
    }

private fun activeSortIndicator(sortField: String, sortDirection: String, field: String): String =
    if (sortField == field) sortArrow(sortDirection) else ""

private fun sortArrow(sortDirection: String): String =
    if (sortDirection == "asc") "↑" else "↓"
