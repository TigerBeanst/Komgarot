package fail.tiger.komgarot.ui.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.ui.components.ErrorState
import fail.tiger.komgarot.ui.components.rememberStableImageRequest
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
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        val serverUrl by prefs.serverUrl.collectAsState(initial = "")
        val thumbnailUrl = "$serverUrl/api/v1/books/$bookId/thumbnail?v=${ThumbnailVersion.get(bookId)}"
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AsyncImage(
                model = rememberStableImageRequest(thumbnailUrl, "book-thumb:$bookId:${ThumbnailVersion.get(bookId)}"),
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

            Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 180.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(shape = RoundedCornerShape(6.dp), modifier = Modifier.width(120.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                        AsyncImage(
                            model = rememberStableImageRequest(thumbnailUrl, "book-thumb:$bookId:${ThumbnailVersion.get(bookId)}"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column {
                                book?.readProgress?.let { progress ->
                                    if (!progress.completed && progress.page > 0) {
                                        Text(
                                            stringResource(R.string.pages_remaining, pageCount - progress.page),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        LinearProgressIndicator(
                                            progress = { progress.page.toFloat() / pageCount },
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
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(bookName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.pages_count, pageCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    meta?.authors?.firstOrNull()?.let {
                        Text(stringResource(R.string.author, it.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onReadClick(bookId, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(if (book?.readProgress != null) R.string.continue_reading else R.string.read))
                }
                OutlinedButton(
                    onClick = { onReadClick(bookId, false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.incognito_reading))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.markUnread() },
                    enabled = book?.readProgress != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("标记未读")
                }
                OutlinedButton(
                    onClick = { vm.markRead() },
                    enabled = book != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("标记已读")
                }
            }

            OutlinedButton(
                onClick = { onMetadataClick(bookId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("编辑元数据")
            }

            HorizontalDivider()

            val context = LocalContext.current
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            Text(
                "ID: $bookId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("id", bookId))
                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            if (!meta?.authors.isNullOrEmpty()) {
                for (author in meta!!.authors) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(translateAuthorRole(context, author.role), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        } // Box
        }
        } // PullToRefreshBox
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ClickableInfoRow(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClick)
        )
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
