package fail.tiger.komgarot.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fail.tiger.komgarot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    seriesId: String,
    serverUrl: String,
    onBookClick: (String, String, Int, Boolean) -> Unit,
    onMetadataClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: BookViewModel
) {
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) { vm.init(seriesId) }

    LaunchedEffect(vm.books.size, vm.loading, hasNavigated) {
        if (vm.books.size == 1 && !vm.hasMore && !vm.loading && !hasNavigated) {
            hasNavigated = true
            val book = vm.books.first()
            onBookClick(book.id, book.metadata.title.ifEmpty { book.name }, book.media.pagesCount, true)
        }
    }

    if ((vm.books.size == 1 && !vm.hasMore) || (vm.loading && vm.series == null)) {
        Box(Modifier.fillMaxSize())
        return
    }

    val listState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.books.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            vm.series?.let { series ->
                AsyncImage(
                    model = "$serverUrl/api/v1/series/${series.id}/thumbnail",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(330.dp)
                )
                Box(
                    Modifier.fillMaxWidth().height(330.dp).background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.surface
                            ),
                            startY = 200f
                        )
                    )
                )
            }

            LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding)
        ) {
            item(span = { GridItemSpan(3) }) {
                vm.series?.let { series ->
                    Column(Modifier.fillMaxWidth().padding(top = 200.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AsyncImage(
                                model = "$serverUrl/api/v1/series/${series.id}/thumbnail",
                                contentDescription = null,
                                modifier = Modifier.width(100.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(series.metadata.title.ifEmpty { series.name }, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.books_count, series.booksCount), style = MaterialTheme.typography.bodyMedium)
                                if (series.metadata.publisher.isNotEmpty()) {
                                    Text(stringResource(R.string.publisher, series.metadata.publisher), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (series.metadata.status.isNotEmpty()) {
                                    Text(stringResource(R.string.status, series.metadata.status), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (series.metadata.summary.isNotEmpty()) {
                            Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium)
                            Text(series.metadata.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (series.metadata.tags.isNotEmpty()) {
                            Text(stringResource(R.string.tags, series.metadata.tags.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            items(vm.books, key = { it.id }) { book ->
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    onBookClick(book.id, book.metadata.title.ifEmpty { book.name }, book.media.pagesCount, false)
                }) {
                    Box {
                        Column {
                            AsyncImage(
                                model = "$serverUrl/api/v1/books/${book.id}/thumbnail",
                                contentDescription = book.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                            )
                            Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        book.metadata.title.ifEmpty { book.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onMetadataClick(book.id) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    "#${book.metadata.number.toInt()} · ${book.media.pagesCount}页",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                book.readProgress?.let { progress ->
                                    if (progress.page > 0 && !progress.completed) {
                                        LinearProgressIndicator(
                                            progress = { progress.page.toFloat() / book.media.pagesCount },
                                            modifier = Modifier.fillMaxWidth().height(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
