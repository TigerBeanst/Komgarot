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
import coil.compose.AsyncImage
import fail.tiger.komgarot.ui.components.LazyGridScrollbar
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.ErrorState
import coil.request.ImageRequest
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    libraryId: String?,
    serverUrl: String,
    onSeriesClick: (String, Int) -> Unit,
    onMetadataClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: SeriesViewModel
) {
    LaunchedEffect(libraryId) { vm.init(libraryId) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchByAuthor by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortField = vm.currentSort.substringBefore(",")
    val sortDirection = vm.currentSort.substringAfter(",")

    val listState = rememberLazyGridState()
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
                            libraryId == null -> "All Series"
                            else -> "Series"
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
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vm.series, key = { it.id }) { series ->
                        val thumbnailUrl = remember(series.id) {
                            "$serverUrl/api/v1/series/${series.id}/thumbnail?v=${ThumbnailVersion.get(series.id)}"
                        }
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onSeriesClick(series.id, series.booksCount) }
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
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
}
