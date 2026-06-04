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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.FloatingDetailActions
import fail.tiger.komgarot.ui.components.FloatingDetailIconButton
import fail.tiger.komgarot.ui.components.ImmersiveDetailBackground
import fail.tiger.komgarot.ui.components.ImmersiveDetailDefaults
import fail.tiger.komgarot.ui.components.ImmersiveDetailIdentityRow
import fail.tiger.komgarot.ui.components.LazyGridScrollbar
import fail.tiger.komgarot.ui.components.ThumbnailImage
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
    val loadBooksFailed = stringResource(R.string.error_load_books_failed)
    val emptyBooksInSeries = stringResource(R.string.empty_books_in_series)
    val copied = stringResource(R.string.copied)

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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                ErrorState(message = vm.error ?: loadBooksFailed, onRetry = vm::refresh)
            } else if (vm.books.isEmpty() && !vm.loading) {
                EmptyState(message = emptyBooksInSeries)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    vm.series?.let { series ->
                        val seriesThumbnailVersion = ThumbnailVersion.get(series.id)
                        val seriesThumbnailUrl = remember(serverUrl, series.id, seriesThumbnailVersion) {
                            KomgaUrls.seriesThumbnail(serverUrl, series.id, seriesThumbnailVersion)
                        }
                        ImmersiveDetailBackground(
                            imageUrl = seriesThumbnailUrl,
                            imageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Series(series.id))
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
                                val seriesThumbnailVersion = ThumbnailVersion.get(series.id)
                                val seriesThumbnailUrl = remember(serverUrl, series.id, seriesThumbnailVersion) {
                                    KomgaUrls.seriesThumbnail(serverUrl, series.id, seriesThumbnailVersion)
                                }
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = ImmersiveDetailDefaults.IdentityTopPadding),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ImmersiveDetailIdentityRow(
                                        coverImageUrl = seriesThumbnailUrl,
                                        coverImageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Series(series.id)),
                                        contentDescription = series.name
                                    ) {
                                        Text(
                                            series.metadata.title.ifEmpty { series.name },
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            pluralStringResource(R.plurals.books_count, series.booksCount, series.booksCount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (series.booksUnreadCount > 0) {
                                            Text(
                                                stringResource(R.string.unread_count, series.booksUnreadCount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (series.metadata.publisher.isNotEmpty()) {
                                            Text(
                                                stringResource(R.string.publisher, series.metadata.publisher),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (series.metadata.status.isNotEmpty()) {
                                            Text(
                                                stringResource(R.string.status, series.metadata.status),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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
                                    stringResource(R.string.id_format, series.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("id", series.id))
                                        android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                }
                        }
                    }
                    items(vm.books, key = { it.id }) { book ->
                        val thumbnailVersion = ThumbnailVersion.get(book.id)
                        val thumbnailUrl = remember(serverUrl, book.id, thumbnailVersion) {
                            KomgaUrls.bookThumbnail(serverUrl, book.id, thumbnailVersion)
                        }
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onBookClick(book.id, book.metadata.title.ifEmpty { book.name }, book.media.pagesCount, false)
                            }
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                                ThumbnailImage(
                                    url = thumbnailUrl,
                                    cacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(book.id)),
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
                                            stringResource(
                                                R.string.book_number_pages,
                                                book.metadata.number,
                                                stringResource(R.string.page_count_short, book.media.pagesCount)
                                            ),
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
                    FloatingDetailActions(
                        onBack = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        backIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        },
                        trailingActions = {
                            vm.series?.let { series ->
                                FloatingDetailIconButton(onClick = { onMetadataClick(series.id) }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit_series_metadata),
                                            tint = Color.White
                                        )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
