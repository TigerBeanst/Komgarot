package fail.tiger.komgarot.ui.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fail.tiger.komgarot.BuildConfig
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.ui.book.BookDetailLoadingSkeleton
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.FloatingDetailActions
import fail.tiger.komgarot.ui.components.FloatingDetailIconButton
import fail.tiger.komgarot.ui.components.ImmersiveDetailScaffold
import fail.tiger.komgarot.ui.components.ThumbnailImage
import fail.tiger.komgarot.ui.metadata.normalizeExternalUrl
import fail.tiger.komgarot.ui.metadata.openExternalUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    bookName: String,
    pageCount: Int,
    isOneShot: Boolean = false,
    seriesIdToResolve: String? = null,
    onBack: () -> Unit,
    onReadClick: (String, Boolean) -> Unit,
    onPageThumbnailClick: (String, Int) -> Unit,
    onMetadataClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onAuthorClick: (String, String) -> Unit = { _, _ -> },
    onTagClick: (String) -> Unit = {},
    aiTranslationAvailable: Boolean = BuildConfig.AI_TRANSLATION_AVAILABLE,
    vm: BookDetailViewModel,
    prefs: AuthPreferences
) {
    val book = vm.book
    val meta = vm.metadata
    val resolvedBookId = book?.id ?: bookId
    val resolvedBookName = book?.metadata?.title?.ifEmpty { book.name } ?: bookName
    val resolvedPageCount = book?.media?.pagesCount ?: pageCount
    val initialLoading = book == null && vm.error == null
    val loadBookDetailFailed = stringResource(R.string.error_load_book_detail_failed)
    val editMetadata = stringResource(R.string.edit_metadata)
    val copied = stringResource(R.string.copied)
    val unknown = stringResource(R.string.unknown)
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val showBookThumbnails by prefs.showBookThumbnails.collectAsStateWithLifecycle(initialValue = true)

    BackHandler { onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val serverUrl by prefs.serverUrl.collectAsStateWithLifecycle()
        LaunchedEffect(bookId, seriesIdToResolve, serverUrl) {
            if (serverUrl.isNotBlank()) {
                if (seriesIdToResolve.isNullOrBlank()) {
                    vm.load(bookId, serverUrl)
                } else {
                    vm.loadSingleBookSeries(seriesIdToResolve, serverUrl)
                }
            }
        }
        val thumbnailVersion = ThumbnailVersion.get(resolvedBookId)
        val thumbnailUrl = remember(serverUrl, resolvedBookId, thumbnailVersion) {
            KomgaUrls.bookThumbnail(serverUrl, resolvedBookId, thumbnailVersion)
        }
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.loading || initialLoading,
            onRefresh = { vm.refresh() },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = vm.loading || initialLoading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding())
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                initialLoading -> BookDetailLoadingSkeleton(onBack = onBack)
                book == null && vm.error != null -> {
                    ErrorState(message = vm.error ?: loadBookDetailFailed, onRetry = vm::refresh)
                }
                else -> {
                val context = LocalContext.current
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                ImmersiveDetailScaffold(
                    backgroundImageUrl = thumbnailUrl,
                    backgroundImageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(resolvedBookId)),
                    coverImageUrl = thumbnailUrl,
                    coverImageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(resolvedBookId)),
                    contentDescription = resolvedBookName,
                    padding = padding,
                    actions = {
                        FloatingDetailActions(
                            onBack = onBack,
                            backIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = Color.White
                                )
                            },
                            trailingActions = {
                                FloatingDetailIconButton(onClick = { onMetadataClick(resolvedBookId) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = editMetadata,
                                        tint = Color.White
                                    )
                                }
                            }
                        )
                    },
                    titleContent = {
                        Text(
                            resolvedBookName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            pluralStringResource(R.plurals.pages_count, resolvedPageCount, resolvedPageCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        book?.readProgress?.let { progress ->
                            if (!progress.completed && progress.page > 0 && resolvedPageCount > 0) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.pages_remaining,
                                        resolvedPageCount - progress.page,
                                        resolvedPageCount - progress.page
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { progress.page.toFloat() / resolvedPageCount },
                                    modifier = Modifier.fillMaxWidth().height(3.dp)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.id_format, resolvedBookId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("id", resolvedBookId))
                                android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    bodyContent = {
                        BookDetailReadingActions(
                            hasReadProgress = book?.readProgress != null,
                            onReadClick = { onReadClick(resolvedBookId, true) },
                            onIncognitoReadClick = { onReadClick(resolvedBookId, false) }
                        )

                        BookDownloadAction(
                            state = vm.downloadState,
                            onClick = {
                                when (vm.downloadState) {
                                    is BookDownloadState.Cached,
                                    is BookDownloadState.Partial -> showClearCacheDialog = true
                                    else -> vm.downloadForOffline(serverUrl)
                                }
                            }
                        )

                        BookDetailReadStatusActions(
                            canMarkUnread = book?.readProgress != null,
                            canMarkRead = book != null,
                            onMarkUnread = { vm.markUnread() },
                            onMarkRead = { vm.markRead() }
                        )

                        HorizontalDivider()

                        if (book != null && !book.oneshot && book.seriesId.isNotBlank()) {
                            val seriesTitle = book.seriesTitle?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.series)
                            MetadataChipRows(
                                title = stringResource(R.string.book_belongs_to_series),
                                items = listOf(
                                    seriesTitle to { onSeriesClick(book.seriesId) }
                                )
                            )
                        }

                        if (!meta?.authors.isNullOrEmpty()) {
                            meta!!.authors.forEach { author ->
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        translateAuthorRole(context, author.role),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        author.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onAuthorClick(author.name, author.role) }
                                    )
                                }
                            }
                        }

                        if (!meta?.tags.isNullOrEmpty()) {
                            MetadataChipRows(
                                title = stringResource(R.string.metadata_label),
                                items = meta!!.tags.map { tag -> tag to { onTagClick(tag) } }
                            )
                        }

                        if (!meta?.links.isNullOrEmpty()) {
                            val visibleLinks = meta!!.links.filter { normalizeExternalUrl(it.url) != null }
                            if (visibleLinks.isNotEmpty()) {
                                MetadataChipRows(
                                    title = stringResource(R.string.metadata_links),
                                    items = visibleLinks.map { link ->
                                        link.label.ifBlank { link.url } to { openExternalUrl(context, link.url) }
                                    }
                                )
                            }
                        }

                        if (meta != null && meta.summary.isNotEmpty()) {
                            Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium)
                            Text(meta.summary, style = MaterialTheme.typography.bodyMedium)
                        }

                        HorizontalDivider()

                        if (book != null) {
                            InfoRow(stringResource(R.string.file_size), formatFileSize(book.sizeBytes, unknown))
                            InfoRow(stringResource(R.string.file_format), book.media.mediaType ?: unknown)
                            InfoRow(stringResource(R.string.file_source), book.url ?: unknown)
                            if (book.created != null) InfoRow(stringResource(R.string.created_at), formatDateTime(book.created))
                            if (book.fileLastModified != null) InfoRow(stringResource(R.string.last_modified), formatDateTime(book.fileLastModified))
                        }
                    },
                    gridContent = {
                        if (showBookThumbnails && resolvedBookId.isNotBlank() && resolvedPageCount > 0) {
                            item(
                                key = "page-preview-title",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Text(
                                    stringResource(R.string.book_page_preview),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(
                                count = resolvedPageCount,
                                key = { pageIndex -> "$resolvedBookId-page-${pageIndex + 1}" },
                                contentType = { "book-page-thumbnail" }
                            ) { pageIndex ->
                                val pageNumber = pageIndex + 1
                                BookPageThumbnail(
                                    serverUrl = serverUrl,
                                    bookId = resolvedBookId,
                                    pageNumber = pageNumber,
                                    onClick = { onPageThumbnailClick(resolvedBookId, pageNumber) }
                                )
                            }
                        }
                    }
                )
            }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.clear_book_cache_title)) },
            text = { Text(stringResource(R.string.clear_book_cache_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearOfflineCache()
                    showClearCacheDialog = false
                }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}

@Composable
private fun BookPageThumbnail(
    serverUrl: String,
    bookId: String,
    pageNumber: Int,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ThumbnailImage(
                    url = KomgaUrls.pageThumbnail(serverUrl, bookId, pageNumber),
                    cacheKey = "book-page-thumbnail:$bookId:$pageNumber",
                    contentDescription = stringResource(R.string.reader_page_description, pageNumber),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = pageNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetadataChipRows(
    title: String,
    items: List<Pair<String, () -> Unit>>
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (label, onClick) ->
                AssistChip(onClick = onClick, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun BookDetailReadingActions(
    hasReadProgress: Boolean,
    onReadClick: () -> Unit,
    onIncognitoReadClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onReadClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (hasReadProgress) R.string.continue_reading else R.string.read),
                style = MaterialTheme.typography.titleSmall
            )
        }
        FilledTonalButton(
            onClick = onIncognitoReadClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.incognito_reading),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun BookDownloadAction(
    state: BookDownloadState,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !state.isRunning,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        val text = when (state) {
            BookDownloadState.Idle -> stringResource(R.string.download_for_offline)
            is BookDownloadState.Partial -> stringResource(
                R.string.download_for_offline_partial,
                state.completedPages,
                state.totalPages
            )
            is BookDownloadState.Downloading -> stringResource(
                R.string.download_for_offline_progress,
                state.completedPages,
                state.totalPages
            )
            is BookDownloadState.Cached -> stringResource(R.string.download_for_offline_cached, state.totalPages)
            is BookDownloadState.Failed -> stringResource(R.string.download_for_offline_failed)
        }
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BookDetailReadStatusActions(
    canMarkUnread: Boolean,
    canMarkRead: Boolean,
    onMarkUnread: () -> Unit,
    onMarkRead: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.reading_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onMarkUnread,
            enabled = canMarkUnread,
            modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.mark_unread))
        }
        TextButton(
            onClick = onMarkRead,
            enabled = canMarkRead,
            modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.mark_read))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatFileSize(bytes: Long?, unknown: String): String {
    if (bytes == null) return unknown
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GiB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatDateTime(dateTime: String): String {
    return try {
        val instant = Instant.parse(dateTime)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        dateTime
    }
}

private fun translateAuthorRole(context: android.content.Context, role: String): String {
    return when (role.lowercase()) {
        "writer" -> context.getString(R.string.author_role_writer)
        "penciller" -> context.getString(R.string.author_role_penciller)
        "inker" -> context.getString(R.string.author_role_inker)
        "colorist" -> context.getString(R.string.author_role_colorist)
        "letterer" -> context.getString(R.string.author_role_letterer)
        "cover" -> context.getString(R.string.author_role_cover)
        "editor" -> context.getString(R.string.author_role_editor)
        "translator" -> context.getString(R.string.author_role_translator)
        else -> role
    }
}
