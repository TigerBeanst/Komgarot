package fail.tiger.komgarot.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.LazyGridScrollbar
import fail.tiger.komgarot.ui.components.rememberStableImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    val context = LocalContext.current

    LaunchedEffect(seriesId) { vm.init(seriesId) }

    LaunchedEffect(vm.books.size, vm.loading, hasNavigated) {
        if (vm.books.size == 1 && !vm.hasMore && !vm.loading && !hasNavigated) {
            hasNavigated = true
            val book = vm.books.first()
            onBookClick(book.id, book.metadata.title.ifEmpty { book.name }, book.media.pagesCount, true)
        }
    }

    if ((vm.books.size == 1 && !vm.hasMore) || (vm.loading && vm.series == null && vm.error == null)) {
        Box(Modifier.fillMaxSize())
        return
    }

    val listState = rememberLazyGridState()
    LaunchedEffect(listState, vm) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= vm.books.size - 4) vm.loadMore()
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        vm.series?.metadata?.title?.ifEmpty { vm.series?.name.orEmpty() }.orEmpty(),
                        maxLines = 1
                    )
                },
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
            modifier = Modifier.fillMaxSize()
        ) {
        if (vm.books.isEmpty() && !vm.loading && vm.error != null) {
            ErrorState(message = vm.error ?: "加载书籍失败", onRetry = vm::refresh)
        } else if (vm.books.isEmpty() && !vm.loading) {
            EmptyState(message = "这个系列还没有书籍")
        } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            vm.series?.let { series ->
                val seriesThumbnailUrl = remember(series.id) {
                    "$serverUrl/api/v1/series/${series.id}/thumbnail?v=${ThumbnailVersion.get(series.id)}"
                }
                AsyncImage(
                    model = rememberStableImageRequest(
                        seriesThumbnailUrl,
                        "series-thumb:${series.id}:${ThumbnailVersion.get(series.id)}"
                    ),
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        vm.series?.let { series ->
                            val seriesThumbnailUrl = remember(series.id) {
                                "$serverUrl/api/v1/series/${series.id}/thumbnail?v=${ThumbnailVersion.get(series.id)}"
                            }
                            Column(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding() + 180.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    AsyncImage(
                                        model = rememberStableImageRequest(
                                            seriesThumbnailUrl,
                                            "series-thumb:${series.id}:${ThumbnailVersion.get(series.id)}"
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.width(120.dp).aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            series.metadata.title.ifEmpty { series.name },
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.books_count, series.booksCount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (series.metadata.publisher.isNotEmpty()) {
                                            Text(
                                                stringResource(R.string.publisher, series.metadata.publisher),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (series.metadata.status.isNotEmpty()) {
                                            Text(
                                                stringResource(R.string.status, series.metadata.status),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        TextButton(onClick = { onMetadataClick(series.id) }) {
                                            Text("编辑系列元数据")
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
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                Text(
                                    "ID: ${series.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("id", series.id))
                                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    items(vm.books, key = { it.id }) { book ->
                        val thumbnailUrl = remember(book.id) {
                            "$serverUrl/api/v1/books/${book.id}/thumbnail?v=${ThumbnailVersion.get(book.id)}"
                        }
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onBookClick(book.id, book.metadata.title.ifEmpty { book.name }, book.media.pagesCount, false)
                            }
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                                AsyncImage(
                                    model = rememberStableImageRequest(
                                        thumbnailUrl,
                                        "book-thumb:${book.id}:${ThumbnailVersion.get(book.id)}"
                                    ),
                                    contentDescription = book.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.fillMaxWidth().align(Alignment.BottomStart)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Column {
                                        Text(
                                            book.metadata.title.ifEmpty { book.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            "#${book.metadata.number.toInt()} · ${book.media.pagesCount}页",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        book.readProgress?.let { progress ->
                                            if (progress.page > 0 && !progress.completed) {
                                                LinearProgressIndicator(
                                                    progress = { progress.page.toFloat() / book.media.pagesCount },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(2.dp),
                                                    color = Color.White,
                                                    trackColor = Color.White.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
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
        } // Box
        }
        } // PullToRefreshBox
    }
}
