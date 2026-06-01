package fail.tiger.komgarot.ui.readlist

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
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.ReadListDto
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
fun ReadListScreen(
    serverUrl: String,
    vm: ReadListViewModel,
    onReadListClick: (String) -> Unit,
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
                title = { Text("阅读列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建阅读列表")
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
                    label = { Text("搜索阅读列表") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                when {
                    vm.readLists.isEmpty() && !vm.loading && vm.error != null ->
                        ErrorState(message = vm.error ?: "加载阅读列表失败", onRetry = vm::refresh)
                    vm.readLists.isEmpty() && !vm.loading ->
                        EmptyState(message = "没有阅读列表")
                    else ->
                        LazyColumn(
                            state = listState,
                            contentPadding = topLevelScrollableContentPadding(top = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(vm.readLists, key = { it.id }) { readList ->
                                ReadListItem(
                                    readList = readList,
                                    serverUrl = serverUrl,
                                    onClick = { onReadListClick(readList.id) }
                                )
                            }
                            item {
                                LaunchedEffect(vm.readLists.size) {
                                    if (vm.hasMore) vm.loadMore()
                                }
                            }
                        }
                }
            }
        }
    }

    if (showEditor) {
        ReadListEditorSheet(
            readList = null,
            onDismiss = { showEditor = false },
            onSave = { name, summary, ordered, ids ->
                vm.create(name, summary, ordered, ids) { showEditor = false }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadListDetailScreen(
    readListId: String,
    serverUrl: String,
    vm: ReadListViewModel,
    onBookClick: (BookDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(readListId) { vm.loadReadList(readListId) }
    var showEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, vm) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { if (it >= vm.books.size - 4) vm.loadMoreBooks() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.selected?.name ?: "阅读列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑阅读列表")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Delete, contentDescription = "删除阅读列表")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.loadReadList(readListId, refresh = true) },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when {
                vm.books.isEmpty() && !vm.loading && vm.error != null ->
                    ErrorState(message = vm.error ?: "加载阅读列表失败", onRetry = { vm.loadReadList(readListId, refresh = true) })
                vm.books.isEmpty() && !vm.loading ->
                    EmptyState(message = "这个阅读列表还没有书籍")
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoPill("${vm.selected?.bookIds?.size ?: vm.books.size} 本")
                                    if (vm.selected?.ordered == true) InfoPill("有序")
                                    if (vm.selected?.filtered == true) InfoPill("过滤列表")
                                }
                                if (!vm.selected?.summary.isNullOrBlank()) {
                                    Text(vm.selected?.summary.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        items(vm.books, key = { it.id }) { book ->
                            val thumbnailVersion = ThumbnailVersion.get(book.id)
                            PosterCard(
                                title = book.metadata.title.ifEmpty { book.name },
                                subtitle = book.seriesTitle ?: book.metadata.number,
                                imageUrl = KomgaUrls.bookThumbnail(serverUrl, book.id, thumbnailVersion),
                                imageCacheKey = "book-thumb:${book.id}:$thumbnailVersion",
                                progress = book.readProgress?.takeIf { !it.completed && book.media.pagesCount > 0 }?.let {
                                    it.page.toFloat() / book.media.pagesCount
                                },
                                onClick = { onBookClick(book) }
                            )
                        }
                    }
            }
        }
    }

    if (showEditor && vm.selected != null) {
        ReadListEditorSheet(
            readList = vm.selected,
            onDismiss = { showEditor = false },
            onSave = { name, summary, ordered, _ ->
                vm.update(readListId, name, summary, ordered) { ok ->
                    Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
                    showEditor = false
                }
            }
        )
    }

    if (showDeleteConfirm && vm.selected != null) {
        ConfirmActionDialog(
            title = "删除阅读列表",
            text = "确定删除阅读列表「${vm.selected?.name}」？\nID: $readListId",
            confirmText = "删除",
            onConfirm = {
                vm.delete(readListId) { ok ->
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
private fun ReadListItem(readList: ReadListDto, serverUrl: String, onClick: () -> Unit) {
    val thumbnailVersion = ThumbnailVersion.get(readList.id)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterCard(
                title = readList.name,
                subtitle = "",
                imageUrl = KomgaUrls.readListThumbnail(serverUrl, readList.id, thumbnailVersion),
                imageCacheKey = "readlist-thumb:${readList.id}:$thumbnailVersion",
                modifier = Modifier.width(72.dp),
                onClick = onClick
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(readList.name, style = MaterialTheme.typography.titleMedium)
                Text("${readList.bookIds.size} 本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (readList.summary.isNotBlank()) {
                    Text(readList.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (readList.ordered) InfoPill("有序")
                    if (readList.filtered) InfoPill("过滤")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadListEditorSheet(
    readList: ReadListDto?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, List<String>) -> Unit
) {
    var name by remember(readList) { mutableStateOf(readList?.name.orEmpty()) }
    var summary by remember(readList) { mutableStateOf(readList?.summary.orEmpty()) }
    var ordered by remember(readList) { mutableStateOf(readList?.ordered ?: false) }
    var idsText by remember(readList) { mutableStateOf(readList?.bookIds?.joinToString(", ").orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (readList == null) "新建阅读列表" else "编辑阅读列表", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("简介") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("保持手动排序", modifier = Modifier.weight(1f))
                Switch(checked = ordered, onCheckedChange = { ordered = it })
            }
            if (readList == null) {
                OutlinedTextField(
                    value = idsText,
                    onValueChange = { idsText = it },
                    label = { Text("书籍 ID，用英文逗号分隔") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = { onSave(name.trim(), summary.trim(), ordered, idsText.toIds()) },
                enabled = name.isNotBlank() && (readList != null || idsText.toIds().isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }
}

private fun String.toIds(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }
