package fail.tiger.komgarot.ui.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.ThumbnailCacheTarget
import fail.tiger.komgarot.data.local.thumbnailCacheKey
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.FloatingDetailActions
import fail.tiger.komgarot.ui.components.FloatingDetailIconButton
import fail.tiger.komgarot.ui.components.ImmersiveDetailScaffold
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
    onBack: () -> Unit,
    onReadClick: (String, Boolean) -> Unit,
    onMetadataClick: (String) -> Unit,
    onAuthorClick: (String, String) -> Unit = { _, _ -> },
    vm: BookDetailViewModel,
    prefs: AuthPreferences
) {
    LaunchedEffect(bookId) { vm.load(bookId) }
    val book = vm.book
    val meta = vm.metadata

    BackHandler { onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val serverUrl by prefs.serverUrl.collectAsState(initial = "")
        val thumbnailVersion = ThumbnailVersion.get(bookId)
        val thumbnailUrl = remember(serverUrl, bookId, thumbnailVersion) {
            KomgaUrls.bookThumbnail(serverUrl, bookId, thumbnailVersion)
        }
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
            if (book == null && !vm.loading && vm.error != null) {
                ErrorState(message = vm.error ?: "加载书籍详情失败", onRetry = { vm.load(bookId) })
            } else {
                val context = LocalContext.current
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                ImmersiveDetailScaffold(
                    backgroundImageUrl = thumbnailUrl,
                    backgroundImageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(bookId)),
                    coverImageUrl = thumbnailUrl,
                    coverImageCacheKey = thumbnailCacheKey(ThumbnailCacheTarget.Book(bookId)),
                    contentDescription = bookName,
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
                                FloatingDetailIconButton(onClick = { onMetadataClick(bookId) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑元数据",
                                        tint = Color.White
                                    )
                                }
                            }
                        )
                    },
                    titleContent = {
                        Text(
                            book?.metadata?.title?.ifEmpty { bookName } ?: bookName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            pluralStringResource(R.plurals.pages_count, pageCount, pageCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        book?.readProgress?.let { progress ->
                            if (!progress.completed && progress.page > 0 && pageCount > 0) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.pages_remaining,
                                        pageCount - progress.page,
                                        pageCount - progress.page
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { progress.page.toFloat() / pageCount },
                                    modifier = Modifier.fillMaxWidth().height(3.dp)
                                )
                            }
                        }
                        Text(
                            "ID: $bookId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("id", bookId))
                                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        BookDetailReadingActions(
                            hasReadProgress = book?.readProgress != null,
                            onReadClick = { onReadClick(bookId, true) },
                            onIncognitoReadClick = { onReadClick(bookId, false) }
                        )
                    },
                    bodyContent = {
                        BookDetailReadStatusActions(
                            canMarkUnread = book?.readProgress != null,
                            canMarkRead = book != null,
                            onMarkUnread = { vm.markUnread() },
                            onMarkRead = { vm.markRead() }
                        )

                        HorizontalDivider()

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
                            InfoRow(stringResource(R.string.tags, "").dropLast(2), meta!!.tags.joinToString(", "))
                        }

                        HorizontalDivider()

                        if (book != null) {
                            InfoRow(stringResource(R.string.file_size), formatFileSize(book.sizeBytes))
                            InfoRow(stringResource(R.string.file_format), book.media.mediaType ?: stringResource(R.string.unknown))
                            InfoRow(stringResource(R.string.file_source), book.url ?: stringResource(R.string.unknown))
                            if (book.created != null) InfoRow(stringResource(R.string.created_at), formatDateTime(book.created))
                            if (book.fileLastModified != null) InfoRow(stringResource(R.string.last_modified), formatDateTime(book.fileLastModified))
                        }
                        if (meta != null && meta.summary.isNotEmpty()) {
                            Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium)
                            Text(meta.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
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

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "未知"
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
