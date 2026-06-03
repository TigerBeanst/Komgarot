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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    serverUrl: String,
    onSeriesClick: (String, Int) -> Unit,
    onMetadataClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: SeriesViewModel,
    onBottomBarVisibleChange: (Boolean) -> Unit = {}
) {
    LaunchedEffect(libraryId, initialSearch) { vm.init(libraryId, initialSearch) }
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

    val context = LocalContext.current

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
                                placeholder = { Text(if (searchByAuthor) "搜索作者" else stringResource(R.string.search_hint)) },
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
                                label = { Text("作者") }
                            )
                        }
                    } else {
                        val title = when {
                            vm.searchQuery.isNotEmpty() && vm.searchByAuthor -> "作者: ${vm.displaySearchQuery}"
                            vm.searchQuery.isNotEmpty() -> "搜索: ${vm.displaySearchQuery}"
                            libraryId == null -> "全部系列"
                            else -> "系列"
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = { vm.search(searchText, searchByAuthor) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = {
                            searchExpanded = false
                            searchText = ""
                            searchByAuthor = false
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = if (vm.currentSort != "metadata.titleSort,asc") MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("名称 ${if (sortField == "metadata.titleSort") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "metadata.titleSort") if (sortDirection == "asc") "desc" else "asc" else "asc"
                                    vm.setSortBy("metadata.titleSort,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("添加时间 ${if (sortField == "created") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "created") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("created,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("更新时间 ${if (sortField == "lastModified") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "lastModified") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastModified,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("阅读日期 ${if (sortField == "lastReadDate") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "lastReadDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastReadDate,$newDir"); sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("发布日期 ${if (sortField == "metadata.releaseDate") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "metadata.releaseDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("metadata.releaseDate,$newDir"); sortMenuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("随机") }, onClick = { vm.setSortBy("random,asc"); sortMenuExpanded = false })
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "筛选",
                                tint = if (vm.activeFilterCount > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = {
                            searchText = vm.displaySearchQuery
                            searchByAuthor = vm.searchByAuthor
                            searchExpanded = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
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
                ErrorState(message = vm.error ?: "加载系列失败", onRetry = vm::refresh)
            } else if (vm.series.isEmpty() && !vm.loading && vm.searchQuery.isNotEmpty()) {
                EmptyState(message = "无搜索结果")
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SeriesStatusChips(
                                sortField = sortField,
                                sortDirection = sortDirection,
                                searchQuery = vm.displaySearchQuery,
                                searchByAuthor = vm.searchByAuthor,
                                activeFilterCount = vm.activeFilterCount
                            )
                        }
                        items(vm.series, key = { it.id }) { series ->
                        val thumbnailVersion = ThumbnailVersion.get(series.id)
                        val thumbnailUrl = remember(serverUrl, series.id, thumbnailVersion) {
                            KomgaUrls.seriesThumbnail(serverUrl, series.id, thumbnailVersion)
                        }
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onSeriesClick(series.id, series.booksCount) }
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                                ThumbnailImage(
                                    url = thumbnailUrl,
                                    cacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Series(series.id)),
                                    contentDescription = series.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.fillMaxWidth().align(Alignment.BottomStart)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                                        .padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        series.metadata.title.ifEmpty { series.name },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesStatusChips(
    sortField: String,
    sortDirection: String,
    searchQuery: String,
    searchByAuthor: Boolean,
    activeFilterCount: Int
) {
    val sortLabel = when (sortField) {
        "metadata.titleSort" -> "名称"
        "created" -> "添加时间"
        "lastModified" -> "更新时间"
        "lastReadDate" -> "阅读日期"
        "metadata.releaseDate" -> "发布日期"
        "random" -> "随机"
        else -> sortField
    }
    val showSort = sortField != "metadata.titleSort" || sortDirection != "asc"
    val hasStatus = showSort || searchQuery.isNotBlank() || activeFilterCount > 0
    if (!hasStatus) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
    ) {
        if (showSort) {
            AssistChip(
                onClick = {},
                label = { Text("排序：$sortLabel ${if (sortDirection == "asc") "↑" else "↓"}") }
            )
        }
        if (searchQuery.isNotBlank()) {
            AssistChip(
                onClick = {},
                label = { Text("${if (searchByAuthor) "作者" else "搜索"}：$searchQuery") }
            )
        }
        if (activeFilterCount > 0) {
            AssistChip(
                onClick = {},
                label = { Text("筛选：$activeFilterCount 项") }
            )
        }
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
            Text("筛选系列", style = MaterialTheme.typography.titleLarge)
            FilterGroup("阅读状态") {
                FilterChip(selected = readStatus == null, onClick = { readStatus = null }, label = { Text("全部") })
                FilterChip(selected = readStatus == "UNREAD", onClick = { readStatus = "UNREAD" }, label = { Text("未读") })
                FilterChip(selected = readStatus == "IN_PROGRESS", onClick = { readStatus = "IN_PROGRESS" }, label = { Text("阅读中") })
                FilterChip(selected = readStatus == "READ", onClick = { readStatus = "READ" }, label = { Text("已读") })
            }
            FilterGroup("系列状态") {
                FilterChip(selected = status == null, onClick = { status = null }, label = { Text("全部") })
                FilterChip(selected = status == "ONGOING", onClick = { status = "ONGOING" }, label = { Text("连载中") })
                FilterChip(selected = status == "ENDED", onClick = { status = "ENDED" }, label = { Text("已完结") })
                FilterChip(selected = status == "HIATUS", onClick = { status = "HIATUS" }, label = { Text("暂停") })
                FilterChip(selected = status == "ABANDONED", onClick = { status = "ABANDONED" }, label = { Text("放弃") })
            }
            FilterGroup("内容") {
                FilterChip(selected = complete == true, onClick = { complete = if (complete == true) null else true }, label = { Text("完整") })
                FilterChip(selected = complete == false, onClick = { complete = if (complete == false) null else false }, label = { Text("不完整") })
                FilterChip(selected = oneshot == true, onClick = { oneshot = if (oneshot == true) null else true }, label = { Text("单本") })
                FilterChip(selected = recentRelease, onClick = { recentRelease = !recentRelease }, label = { Text("最近一年发布") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清除") }
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
                ) { Text("应用") }
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
