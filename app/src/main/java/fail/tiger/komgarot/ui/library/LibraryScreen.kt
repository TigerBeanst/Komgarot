package fail.tiger.komgarot.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.ui.components.AutoHideBottomBarOnLazyListScroll
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.SectionHeader
import fail.tiger.komgarot.ui.components.ThumbnailImage
import fail.tiger.komgarot.ui.components.topLevelScrollableContentPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onLibraryClick: (String?) -> Unit,
    onBookClick: (BookDto) -> Unit,
    onSeriesClick: (String, Int, Boolean) -> Unit,
    serverUrl: String,
    vm: LibraryViewModel,
    onBottomBarVisibleChange: (Boolean) -> Unit = {}
) {
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val continueReadingBooks by vm.continueReadingBooks.collectAsStateWithLifecycle()
    val latestBooks by vm.latestBooks.collectAsStateWithLifecycle()
    val updatedSeries by vm.updatedSeries.collectAsStateWithLifecycle()
    val newSeries by vm.newSeries.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val hasAnyContent = libraries.isNotEmpty() || continueReadingBooks.isNotEmpty() ||
        latestBooks.isNotEmpty() || updatedSeries.isNotEmpty() || newSeries.isNotEmpty()
    val listState = rememberLazyListState()
    AutoHideBottomBarOnLazyListScroll(listState, onBottomBarVisibleChange)
    val loadHome = { if (serverUrl.isNotBlank()) vm.load() }

    LaunchedEffect(serverUrl) {
        loadHome()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = loadHome,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (error != null && !hasAnyContent && !loading) {
                ErrorState(message = error ?: stringResource(R.string.error_connect_komga_failed), onRetry = loadHome)
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = topLevelScrollableContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (error != null && hasAnyContent) {
                        item {
                            Text(
                                stringResource(R.string.partial_content_load_failed, error.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (continueReadingBooks.isNotEmpty()) {
                        item {
                            BookSection(
                                title = stringResource(R.string.continue_reading_section),
                                books = continueReadingBooks,
                                serverUrl = serverUrl,
                                onBookClick = onBookClick
                            )
                        }
                    }

                    if (latestBooks.isNotEmpty()) {
                        item {
                            BookSection(
                                title = stringResource(R.string.recently_added),
                                books = latestBooks,
                                serverUrl = serverUrl,
                                onBookClick = onBookClick
                            )
                        }
                    }

                    if (updatedSeries.isNotEmpty()) {
                        item {
                            SeriesSection(
                                title = stringResource(R.string.recently_updated),
                                series = updatedSeries,
                                serverUrl = serverUrl,
                                onSeriesClick = onSeriesClick
                            )
                        }
                    }

                    if (newSeries.isNotEmpty()) {
                        item {
                            SeriesSection(
                                title = stringResource(R.string.new_series),
                                series = newSeries,
                                serverUrl = serverUrl,
                                onSeriesClick = onSeriesClick
                            )
                        }
                    }

                    item {
                        Text(stringResource(R.string.libraries), style = MaterialTheme.typography.titleLarge)
                    }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LibraryCard(name = stringResource(R.string.all_series), modifier = Modifier.width(160.dp)) { onLibraryClick(null) }
                            libraries.forEach { lib ->
                                LibraryCard(name = lib.name, modifier = Modifier.width(160.dp)) { onLibraryClick(lib.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSection(
    title: String,
    books: List<BookDto>,
    serverUrl: String,
    onBookClick: (BookDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(books, key = { it.id }) { book ->
                BookPosterCard(book = book, serverUrl = serverUrl, onClick = { onBookClick(book) })
            }
        }
    }
}

@Composable
private fun SeriesSection(
    title: String,
    series: List<SeriesDto>,
    serverUrl: String,
    onSeriesClick: (String, Int, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(series, key = { it.id }) { item ->
                SeriesPosterCard(
                    item = item,
                    serverUrl = serverUrl,
                    onClick = { onSeriesClick(item.id, item.booksCount, item.oneshot) }
                )
            }
        }
    }
}

@Composable
private fun BookPosterCard(book: BookDto, serverUrl: String, onClick: () -> Unit) {
    val thumbnailVersion = ThumbnailVersion.get(book.id)
    val thumbnailUrl = remember(book.id, serverUrl, thumbnailVersion) {
        KomgaUrls.bookThumbnail(serverUrl, book.id, thumbnailVersion)
    }
    Card(
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.width(112.dp).clickable(onClick = onClick)
    ) {
        PosterImage(
            imageUrl = thumbnailUrl,
            imageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(book.id)),
            title = book.metadata.title.ifEmpty { book.name },
            subtitle = book.seriesTitle ?: book.metadata.number,
            progress = book.readProgress?.takeIf { !it.completed && book.media.pagesCount > 0 }?.let {
                it.page.toFloat() / book.media.pagesCount
            }
        )
    }
}

@Composable
private fun SeriesPosterCard(item: SeriesDto, serverUrl: String, onClick: () -> Unit) {
    val thumbnailVersion = ThumbnailVersion.get(item.id)
    val thumbnailUrl = remember(item.id, serverUrl, thumbnailVersion) {
        KomgaUrls.seriesThumbnail(serverUrl, item.id, thumbnailVersion)
    }
    Card(
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.width(112.dp).clickable(onClick = onClick)
    ) {
        PosterImage(
            imageUrl = thumbnailUrl,
            imageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Series(item.id)),
            title = item.metadata.title.ifEmpty { item.name },
            subtitle = stringResource(R.string.books_short_count, item.booksCount)
        )
    }
}

@Composable
private fun PosterImage(
    imageUrl: String,
    imageCacheKey: String,
    title: String,
    subtitle: String,
    progress: Float? = null
) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
        ThumbnailImage(
            url = imageUrl,
            cacheKey = imageCacheKey,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.78f))))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 2)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.aspectRatio(1.5f).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
    }
}
