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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
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
    val readListsTitle = stringResource(R.string.read_lists)
    val newReadList = stringResource(R.string.new_read_list)
    val searchReadLists = stringResource(R.string.search_read_lists)
    val loadReadListsFailed = stringResource(R.string.error_load_read_lists_failed)
    val emptyReadLists = stringResource(R.string.empty_read_lists)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(readListsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = newReadList)
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
                    label = { Text(searchReadLists) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                when {
                    vm.readLists.isEmpty() && !vm.loading && vm.error != null ->
                        ErrorState(message = vm.error ?: loadReadListsFailed, onRetry = vm::refresh)
                    vm.readLists.isEmpty() && !vm.loading ->
                        EmptyState(message = emptyReadLists)
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
    val readListTitle = stringResource(R.string.read_list)
    val editReadList = stringResource(R.string.edit_read_list)
    val deleteReadList = stringResource(R.string.delete_read_list)
    val loadReadListsFailed = stringResource(R.string.error_load_read_lists_failed)
    val emptyReadListBooks = stringResource(R.string.empty_read_list_books)
    val saved = stringResource(R.string.saved)
    val saveFailed = stringResource(R.string.save_failed)
    val deleted = stringResource(R.string.deleted)
    val deleteFailed = stringResource(R.string.delete_failed)

    LaunchedEffect(gridState, vm) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { if (it >= vm.books.size - 4) vm.loadMoreBooks() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.selected?.name ?: readListTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Edit, contentDescription = editReadList)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = vm.selected != null) {
                        Icon(Icons.Default.Delete, contentDescription = deleteReadList)
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
                    ErrorState(message = vm.error ?: loadReadListsFailed, onRetry = { vm.loadReadList(readListId, refresh = true) })
                vm.books.isEmpty() && !vm.loading ->
                    EmptyState(message = emptyReadListBooks)
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
                                    InfoPill(stringResource(R.string.books_count_label, vm.selected?.bookIds?.size ?: vm.books.size))
                                    if (vm.selected?.ordered == true) InfoPill(stringResource(R.string.ordered))
                                    if (vm.selected?.filtered == true) InfoPill(stringResource(R.string.filtered_read_list))
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
                                imageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(book.id)),
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
                    Toast.makeText(context, if (ok) saved else saveFailed, Toast.LENGTH_SHORT).show()
                    showEditor = false
                }
            }
        )
    }

    if (showDeleteConfirm && vm.selected != null) {
        ConfirmActionDialog(
            title = deleteReadList,
            text = stringResource(R.string.delete_read_list_message, vm.selected?.name.orEmpty(), readListId),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.delete(readListId) { ok ->
                    Toast.makeText(context, if (ok) deleted else deleteFailed, Toast.LENGTH_SHORT).show()
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterCard(
                title = readList.name,
                subtitle = "",
                imageUrl = KomgaUrls.readListThumbnail(serverUrl, readList.id, thumbnailVersion),
                imageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.ReadList(readList.id)),
                modifier = Modifier.width(68.dp),
                onClick = onClick
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(readList.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.books_count_label, readList.bookIds.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (readList.summary.isNotBlank()) {
                    Text(readList.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (readList.ordered) InfoPill(stringResource(R.string.ordered))
                    if (readList.filtered) InfoPill(stringResource(R.string.filtered))
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
            Text(stringResource(if (readList == null) R.string.new_read_list else R.string.edit_read_list), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text(stringResource(R.string.read_list_summary)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.manual_order), modifier = Modifier.weight(1f))
                Switch(checked = ordered, onCheckedChange = { ordered = it })
            }
            if (readList == null) {
                OutlinedTextField(
                    value = idsText,
                    onValueChange = { idsText = it },
                    label = { Text(stringResource(R.string.book_ids_comma)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = { onSave(name.trim(), summary.trim(), ordered, idsText.toIds()) },
                enabled = name.isNotBlank() && (readList != null || idsText.toIds().isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

private fun String.toIds(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }
