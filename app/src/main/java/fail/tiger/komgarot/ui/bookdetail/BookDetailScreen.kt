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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AuthPreferences
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
    onAuthorClick: (String) -> Unit = {},
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AsyncImage(
                model = "${prefs.serverUrlBlocking}/api/v1/books/$bookId/thumbnail",
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
                Box {
                    AsyncImage(
                        model = "${prefs.serverUrlBlocking}/api/v1/books/$bookId/thumbnail",
                        contentDescription = null,
                        modifier = Modifier.width(100.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    book?.readProgress?.let { progress ->
                        if (!progress.completed && progress.page > 0) {
                            Column(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .width(100.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                                    .padding(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress.page.toFloat() / pageCount },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                )
                                Text(
                                    stringResource(R.string.pages_remaining, pageCount - progress.page),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            HorizontalDivider()

            meta?.authors?.let { authors ->
                if (authors.isNotEmpty()) {
                    val context = LocalContext.current
                    authors.forEach { author ->
                        ClickableInfoRow(
                            label = translateAuthorRole(context, author.role),
                            value = author.name,
                            onClick = { onAuthorClick(author.name) }
                        )
                    }
                }
            }

            meta?.tags?.let { tags ->
                if (tags.isNotEmpty()) {
                    InfoRow(stringResource(R.string.tags, "").dropLast(2), tags.joinToString(", "))
                }
            }

            HorizontalDivider()

            book?.let { b ->
                InfoRow(stringResource(R.string.file_size), formatFileSize(b.sizeBytes))
                InfoRow(stringResource(R.string.file_format), b.media.mediaType ?: stringResource(R.string.unknown))
                InfoRow(stringResource(R.string.file_source), b.url ?: stringResource(R.string.unknown))
                b.created?.let { InfoRow(stringResource(R.string.created_at), formatDateTime(it)) }
                b.fileLastModified?.let { InfoRow(stringResource(R.string.last_modified), formatDateTime(it)) }
            }
            meta?.let { m ->
                if (m.summary.isNotEmpty()) {
                    Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium)
                    Text(m.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
