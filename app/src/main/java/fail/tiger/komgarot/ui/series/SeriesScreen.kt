package fail.tiger.komgarot.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fail.tiger.komgarot.R

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
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortField = vm.currentSort.substringBefore(",")
    val sortDirection = vm.currentSort.substringAfter(",")

    val listState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.series.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchExpanded) {
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search(searchText) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val title = when {
                            vm.searchQuery.isNotEmpty() -> "搜索: ${vm.searchQuery}"
                            libraryId == null -> "All Series"
                            else -> "Series"
                        }
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = {
                            vm.search(searchText)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = {
                            searchExpanded = false
                            searchText = ""
                            vm.search("")
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
                                    vm.setSortBy("metadata.titleSort,$newDir")
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("添加时间 ${if (sortField == "created") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "created") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("created,$newDir")
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("更新时间 ${if (sortField == "lastModified") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "lastModified") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastModified,$newDir")
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("阅读日期 ${if (sortField == "lastReadDate") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "lastReadDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("lastReadDate,$newDir")
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("发布日期 ${if (sortField == "metadata.releaseDate") if (sortDirection == "asc") "↑" else "↓" else ""}") },
                                onClick = {
                                    val newDir = if (sortField == "metadata.releaseDate") if (sortDirection == "asc") "desc" else "asc" else "desc"
                                    vm.setSortBy("metadata.releaseDate,$newDir")
                                    sortMenuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("随机") }, onClick = { vm.setSortBy("random,asc"); sortMenuExpanded = false })
                        }
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (vm.series.isEmpty() && !vm.loading && vm.searchQuery.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("无搜索结果", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
            items(vm.series, key = { it.id }) { series ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onSeriesClick(series.id, series.booksCount) }) {
                    Column {
                        AsyncImage(
                            model = "$serverUrl/api/v1/series/${series.id}/thumbnail",
                            contentDescription = series.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                series.metadata.title.ifEmpty { series.name },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onMetadataClick(series.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit metadata", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
