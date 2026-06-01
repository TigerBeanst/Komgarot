package fail.tiger.komgarot.ui.collection

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.CollectionDto
import fail.tiger.komgarot.ui.components.AutoHideBottomBarOnLazyListScroll
import fail.tiger.komgarot.ui.components.ConfirmActionDialog
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.InfoPill
import fail.tiger.komgarot.ui.components.PosterCard
import fail.tiger.komgarot.ui.components.topLevelScrollableContentPadding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    serverUrl: String,
    vm: CollectionViewModel,
    onCollectionClick: (String) -> Unit,
    onBack: () -> Unit,
    onBottomBarVisibleChange: (Boolean) -> Unit = {}
) {
    LaunchedEffect(vm) { vm.ensureListLoaded() }
    var showEditor by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(vm.search) }
    val listState = rememberLazyListState()
    AutoHideBottomBarOnLazyListScroll(listState, onBottomBarVisibleChange)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("集合") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建集合")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.updateSearch(it)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("搜索集合") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                when {
                    vm.collections.isEmpty() && !vm.loading && vm.error != null ->
                        ErrorState(message = vm.error ?: "加载集合失败", onRetry = vm::refresh)
                    vm.collections.isEmpty() && !vm.loading ->
                        EmptyState(message = "没有集合")
                    else ->
                        LazyColumn(
                            state = listState,
                            contentPadding = topLevelScrollableContentPadding(top = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(vm.collections, key = { it.id }) { collection ->
                                CollectionListItem(
                                    collection = collection,
                                    serverUrl = serverUrl,
                                    onClick = { onCollectionClick(collection.id) }
                                )
                            }
                            item {
                                LaunchedEffect(vm.collections.size) {
                                    if (vm.hasMore) vm.loadMore()
                                }
                            }
                        }
                }
            }
        }
    }

    if (showEditor) {
        CollectionEditorSheet(
            collection = null,
            onDismiss = { showEditor = false },
            onSave = { name, ordered, ids ->
                vm.create(name, ordered, ids) { showEditor = false }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    serverUrl: String,
    vm: CollectionViewModel,
    onSeriesClick: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(collectionId) { vm.loadCollection(collectionId) }
    var showEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, vm) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { if (it >= vm.series.size - 4) vm.loadMoreSeries() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.selected?.name ?: "集合") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑集合")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Delete, contentDescription = "删除集合")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.loadCollection(collectionId, refresh = true) },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when {
                vm.series.isEmpty() && !vm.loading && vm.error != null ->
                    ErrorState(message = vm.error ?: "加载集合失败", onRetry = { vm.loadCollection(collectionId, refresh = true) })
                vm.series.isEmpty() && !vm.loading ->
                    EmptyState(message = "这个集合还没有系列")
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                InfoPill("${vm.selected?.seriesIds?.size ?: vm.series.size} 个系列")
                                if (vm.selected?.ordered == true) InfoPill("有序")
                                if (vm.selected?.filtered == true) InfoPill("过滤集合")
                            }
                        }
                        items(vm.series, key = { it.id }) { series ->
                            val thumbnailVersion = ThumbnailVersion.get(series.id)
                            PosterCard(
                                title = series.metadata.title.ifEmpty { series.name },
                                subtitle = "${series.booksCount} 本",
                                imageUrl = KomgaUrls.seriesThumbnail(serverUrl, series.id, thumbnailVersion),
                                imageCacheKey = "series-thumb:${series.id}:$thumbnailVersion",
                                badge = if (series.booksUnreadCount > 0) "${series.booksUnreadCount} 未读" else null,
                                onClick = { onSeriesClick(series.id, series.booksCount) }
                            )
                        }
                    }
            }
        }
    }

    if (showEditor && vm.selected != null) {
        CollectionEditorSheet(
            collection = vm.selected,
            onDismiss = { showEditor = false },
            onSave = { name, ordered, _ ->
                vm.update(collectionId, name, ordered) { ok ->
                    Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
                    showEditor = false
                }
            }
        )
    }

    if (showDeleteConfirm && vm.selected != null) {
        ConfirmActionDialog(
            title = "删除集合",
            text = "确定删除集合「${vm.selected?.name}」？\nID: $collectionId",
            confirmText = "删除",
            onConfirm = {
                vm.delete(collectionId) { ok ->
                    Toast.makeText(context, if (ok) "已删除" else "删除失败", Toast.LENGTH_SHORT).show()
                    showDeleteConfirm = false
                    if (ok) onBack()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun CollectionListItem(
    collection: CollectionDto,
    serverUrl: String,
    onClick: () -> Unit
) {
    val thumbnailVersion = ThumbnailVersion.get(collection.id)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterCard(
                title = collection.name,
                subtitle = "",
                imageUrl = KomgaUrls.collectionThumbnail(serverUrl, collection.id, thumbnailVersion),
                imageCacheKey = "collection-thumb:${collection.id}:$thumbnailVersion",
                modifier = Modifier.width(72.dp),
                onClick = onClick
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium)
                Text("${collection.seriesIds.size} 个系列", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (collection.ordered) InfoPill("有序")
                    if (collection.filtered) InfoPill("过滤")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionEditorSheet(
    collection: CollectionDto?,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, List<String>) -> Unit
) {
    var name by remember(collection) { mutableStateOf(collection?.name.orEmpty()) }
    var ordered by remember(collection) { mutableStateOf(collection?.ordered ?: false) }
    var idsText by remember(collection) { mutableStateOf(collection?.seriesIds?.joinToString(", ").orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (collection == null) "新建集合" else "编辑集合", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("保持手动排序", modifier = Modifier.weight(1f))
                Switch(checked = ordered, onCheckedChange = { ordered = it })
            }
            if (collection == null) {
                OutlinedTextField(
                    value = idsText,
                    onValueChange = { idsText = it },
                    label = { Text("系列 ID，用英文逗号分隔") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = { onSave(name.trim(), ordered, idsText.toIds()) },
                enabled = name.isNotBlank() && (collection != null || idsText.toIds().isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }
}

private fun String.toIds(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }
