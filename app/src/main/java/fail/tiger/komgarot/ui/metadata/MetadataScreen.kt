package fail.tiger.komgarot.ui.metadata

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import androidx.compose.material3.AssistChip
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.AlternateTitleDto
import fail.tiger.komgarot.data.remote.dto.AuthorDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import fail.tiger.komgarot.data.remote.dto.WebLinkDto
import fail.tiger.komgarot.ui.cover.CoverCrop
import fail.tiger.komgarot.ui.cover.bitmapToJpegBytes
import fail.tiger.komgarot.ui.cover.cropCoverBitmap
import fail.tiger.komgarot.ui.cover.scaleCoverBitmapForUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MetadataScreen(
    type: String,
    id: String,
    serverUrl: String,
    coverUri: String?,
    coverFocus: Boolean,
    canEditMetadata: Boolean,
    onBack: () -> Unit,
    vm: MetadataViewModel
) {
    LaunchedEffect(id) {
        if (type == "series") vm.loadSeries(id) else vm.loadBook(id)
    }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(canEditMetadata) {
        if (!canEditMetadata) editing = false
    }
    val effectiveEditing = editing && canEditMetadata
    if (type == "series") {
        SeriesMetadataContent(
            id = id,
            serverUrl = serverUrl,
            incomingCoverUri = coverUri,
            coverFocus = coverFocus,
            canEditMetadata = canEditMetadata,
            onBack = onBack,
            vm = vm,
            editing = effectiveEditing,
            onEditToggle = { if (canEditMetadata) editing = !editing }
        )
    } else {
        BookMetadataContent(
            id = id,
            serverUrl = serverUrl,
            incomingCoverUri = coverUri,
            coverFocus = coverFocus,
            canEditMetadata = canEditMetadata,
            onBack = onBack,
            vm = vm,
            editing = effectiveEditing,
            onEditToggle = { if (canEditMetadata) editing = !editing }
        )
    }
}

@Composable
fun SeriesMetadataContent(
    id: String,
    serverUrl: String,
    incomingCoverUri: String?,
    coverFocus: Boolean,
    canEditMetadata: Boolean,
    onBack: () -> Unit,
    vm: MetadataViewModel,
    editing: Boolean,
    onEditToggle: () -> Unit
) {
    val meta = vm.seriesMeta
    var title by remember(meta) { mutableStateOf(meta?.title ?: "") }
    var status by remember(meta) { mutableStateOf(meta?.status ?: "ONGOING") }
    var summary by remember(meta) { mutableStateOf(meta?.summary ?: "") }
    var publisher by remember(meta) { mutableStateOf(meta?.publisher ?: "") }
    var language by remember(meta) { mutableStateOf(meta?.language ?: "") }
    var ageRating by remember(meta) { mutableStateOf(meta?.ageRating?.toString() ?: "") }
    var readingDirection by remember(meta) { mutableStateOf(meta?.readingDirection ?: "") }
    var genresText by remember(meta) { mutableStateOf(meta?.genres?.joinToString(", ") ?: "") }
    var tagsText by remember(meta) { mutableStateOf(meta?.tags?.joinToString(", ") ?: "") }
    var sharingLabelsText by remember(meta) { mutableStateOf(meta?.sharingLabels?.joinToString(", ") ?: "") }
    var alternateTitles by remember(meta) { mutableStateOf(meta?.alternateTitles ?: emptyList()) }
    var links by remember(meta) { mutableStateOf(meta?.links ?: emptyList()) }
    var titleLock by remember(meta) { mutableStateOf(meta?.titleLock ?: false) }
    var summaryLock by remember(meta) { mutableStateOf(meta?.summaryLock ?: false) }
    var publisherLock by remember(meta) { mutableStateOf(meta?.publisherLock ?: false) }
    var tagsLock by remember(meta) { mutableStateOf(meta?.tagsLock ?: false) }
    var genresLock by remember(meta) { mutableStateOf(meta?.genresLock ?: false) }
    var linksLock by remember(meta) { mutableStateOf(meta?.linksLock ?: false) }
    val thumbnailVersion = ThumbnailVersion.get(id)
    val thumbnailUrl = remember(serverUrl, id, thumbnailVersion) {
        KomgaUrls.seriesThumbnail(serverUrl, id, thumbnailVersion)
    }
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.save_failed)

    MetadataScaffold(
        title = stringResource(R.string.metadata_series_title),
        onBack = onBack,
        onRefresh = { vm.refreshMetadata("series", id) },
        editing = editing,
        onEditToggle = onEditToggle,
        canEditMetadata = canEditMetadata,
        saving = vm.saving,
        onSave = {
            vm.saveSeriesMeta(
                id,
                SeriesMetadataDto(
                    title = title,
                    titleSort = title,
                    status = status,
                    summary = summary,
                    publisher = publisher,
                    ageRating = ageRating.toIntOrNull(),
                    language = language,
                    readingDirection = readingDirection.ifBlank { null },
                    alternateTitles = alternateTitles.filter { it.label.isNotBlank() && it.title.isNotBlank() },
                    genres = genresText.toListField(),
                    tags = tagsText.toListField(),
                    sharingLabels = sharingLabelsText.toListField(),
                    links = links.filter { it.label.isNotBlank() && it.url.isNotBlank() },
                    titleLock = titleLock,
                    titleSortLock = meta?.titleSortLock ?: false,
                    statusLock = meta?.statusLock ?: false,
                    summaryLock = summaryLock,
                    publisherLock = publisherLock,
                    ageRatingLock = meta?.ageRatingLock ?: false,
                    languageLock = meta?.languageLock ?: false,
                    readingDirectionLock = meta?.readingDirectionLock ?: false,
                    alternateTitlesLock = meta?.alternateTitlesLock ?: false,
                    genresLock = genresLock,
                    tagsLock = tagsLock,
                    sharingLabelsLock = meta?.sharingLabelsLock ?: false,
                    linksLock = linksLock,
                    totalBookCount = meta?.totalBookCount,
                    totalBookCountLock = meta?.totalBookCountLock ?: false
                )
            ) { ok ->
                if (ok) {
                    onEditToggle()
                } else {
                    Toast.makeText(context, vm.saveError ?: saveFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    ) { padding ->
        if (meta == null) {
            LoadingMetadata(padding)
        } else {
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetadataCoverSection(
                    targetType = "series",
                    id = id,
                    thumbnailUrl = thumbnailUrl,
                    incomingCoverUri = incomingCoverUri,
                    coverFocus = coverFocus,
                    canEditMetadata = canEditMetadata,
                    vm = vm
                )
                MetadataSection(stringResource(R.string.metadata_basic_info)) {
                    MetaField(stringResource(R.string.metadata_title), title, editing) { title = it }
                    MetaField(stringResource(R.string.metadata_publisher), publisher, editing) { publisher = it }
                    MetaField(stringResource(R.string.metadata_language), language, editing) { language = it }
                    MetaField(stringResource(R.string.metadata_age_rating), ageRating, editing) { ageRating = it.filter(Char::isDigit) }
                    MetaField(stringResource(R.string.metadata_reading_direction), readingDirection, editing) { readingDirection = it }
                    SeriesStatusField(status = status, editing = editing, onChange = { status = it })
                }
                MetadataSection(stringResource(R.string.summary)) {
                    MetaField(stringResource(R.string.summary), summary, editing, maxLines = 6) { summary = it }
                }
                MetadataSection(stringResource(R.string.metadata_classification)) {
                    MetaField(stringResource(R.string.metadata_genres), genresText, editing) { genresText = it }
                    MetaField(stringResource(R.string.metadata_label), tagsText, editing) { tagsText = it }
                    MetaField(stringResource(R.string.metadata_sharing_labels), sharingLabelsText, editing) { sharingLabelsText = it }
                }
                MetadataSection(stringResource(R.string.metadata_alternate_titles)) {
                    AlternateTitlesEditor(alternateTitles, editing, showTitle = false) { alternateTitles = it }
                }
                MetadataSection(stringResource(R.string.metadata_links)) {
                    LinksEditor(links, editing, showTitle = false) { links = it }
                }
                if (editing) {
                    MetadataSection(stringResource(R.string.metadata_lock_fields)) {
                        LockSwitch(stringResource(R.string.metadata_title), titleLock) { titleLock = it }
                        LockSwitch(stringResource(R.string.summary), summaryLock) { summaryLock = it }
                        LockSwitch(stringResource(R.string.metadata_publisher), publisherLock) { publisherLock = it }
                        LockSwitch(stringResource(R.string.metadata_genres), genresLock) { genresLock = it }
                        LockSwitch(stringResource(R.string.metadata_label), tagsLock) { tagsLock = it }
                        LockSwitch(stringResource(R.string.metadata_links), linksLock) { linksLock = it }
                    }
                }
            }
        }
    }
}

@Composable
fun BookMetadataContent(
    id: String,
    serverUrl: String,
    incomingCoverUri: String?,
    coverFocus: Boolean,
    canEditMetadata: Boolean,
    onBack: () -> Unit,
    vm: MetadataViewModel,
    editing: Boolean,
    onEditToggle: () -> Unit
) {
    val meta = vm.bookMeta
    var title by remember(meta) { mutableStateOf(meta?.title ?: "") }
    var summary by remember(meta) { mutableStateOf(meta?.summary ?: "") }
    var number by remember(meta) { mutableStateOf(meta?.number ?: "") }
    var numberSort by remember(meta) { mutableStateOf(meta?.numberSort?.toString() ?: "") }
    var releaseDate by remember(meta) { mutableStateOf(meta?.releaseDate ?: "") }
    var isbn by remember(meta) { mutableStateOf(meta?.isbn ?: "") }
    var tagsText by remember(meta) { mutableStateOf(meta?.tags?.joinToString(", ") ?: "") }
    var links by remember(meta) { mutableStateOf(meta?.links ?: emptyList()) }
    var authors by remember(meta) { mutableStateOf(meta?.authors ?: emptyList()) }
    var titleLock by remember(meta) { mutableStateOf(meta?.titleLock ?: false) }
    var summaryLock by remember(meta) { mutableStateOf(meta?.summaryLock ?: false) }
    var numberLock by remember(meta) { mutableStateOf(meta?.numberLock ?: false) }
    var authorsLock by remember(meta) { mutableStateOf(meta?.authorsLock ?: false) }
    var tagsLock by remember(meta) { mutableStateOf(meta?.tagsLock ?: false) }
    var linksLock by remember(meta) { mutableStateOf(meta?.linksLock ?: false) }
    val thumbnailVersion = ThumbnailVersion.get(id)
    val thumbnailUrl = remember(serverUrl, id, thumbnailVersion) {
        KomgaUrls.bookThumbnail(serverUrl, id, thumbnailVersion)
    }
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.save_failed)

    MetadataScaffold(
        title = stringResource(R.string.metadata_book_title),
        onBack = onBack,
        onRefresh = { vm.refreshMetadata("book", id) },
        editing = editing,
        onEditToggle = onEditToggle,
        canEditMetadata = canEditMetadata,
        saving = vm.saving,
        onSave = {
            vm.saveBookMeta(
                id,
                BookMetadataDto(
                    title = title,
                    summary = summary,
                    number = number,
                    numberSort = numberSort.toFloatOrNull(),
                    releaseDate = releaseDate.ifEmpty { null },
                    isbn = isbn,
                    authors = authors.filter { it.name.isNotBlank() || it.role.isNotBlank() },
                    tags = tagsText.toListField(),
                    links = links.filter { it.label.isNotBlank() && it.url.isNotBlank() },
                    titleLock = titleLock,
                    summaryLock = summaryLock,
                    numberLock = numberLock,
                    numberSortLock = meta?.numberSortLock ?: false,
                    releaseDateLock = meta?.releaseDateLock ?: false,
                    isbnLock = meta?.isbnLock ?: false,
                    authorsLock = authorsLock,
                    tagsLock = tagsLock,
                    linksLock = linksLock
                )
            ) { ok ->
                if (ok) {
                    onEditToggle()
                } else {
                    Toast.makeText(context, vm.saveError ?: saveFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    ) { padding ->
        if (meta == null) {
            LoadingMetadata(padding)
        } else {
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetadataCoverSection(
                    targetType = "book",
                    id = id,
                    thumbnailUrl = thumbnailUrl,
                    incomingCoverUri = incomingCoverUri,
                    coverFocus = coverFocus,
                    canEditMetadata = canEditMetadata,
                    vm = vm
                )
                MetadataSection(stringResource(R.string.metadata_basic_info)) {
                    MetaField(stringResource(R.string.metadata_title), title, editing) { title = it }
                    MetaField(stringResource(R.string.metadata_number), number, editing) { number = it }
                    MetaField(stringResource(R.string.metadata_number_sort), numberSort, editing) { numberSort = it }
                    MetaField(stringResource(R.string.metadata_release_date), releaseDate, editing) { releaseDate = it }
                    MetaField(stringResource(R.string.metadata_isbn), isbn, editing) { isbn = it }
                }
                MetadataSection(stringResource(R.string.summary)) {
                    MetaField(stringResource(R.string.summary), summary, editing, maxLines = 6) { summary = it }
                }
                MetadataSection(stringResource(R.string.metadata_authors)) {
                    AuthorsEditor(authors = authors, editing = editing, onAuthorsChange = { authors = it })
                }
                MetadataSection(stringResource(R.string.metadata_label)) {
                    MetaField(stringResource(R.string.metadata_label), tagsText, editing, showLabel = false) { tagsText = it }
                }
                MetadataSection(stringResource(R.string.metadata_links)) {
                    LinksEditor(links, editing, showTitle = false) { links = it }
                }
                if (editing) {
                    MetadataSection(stringResource(R.string.metadata_lock_fields)) {
                        LockSwitch(stringResource(R.string.metadata_title), titleLock) { titleLock = it }
                        LockSwitch(stringResource(R.string.summary), summaryLock) { summaryLock = it }
                        LockSwitch(stringResource(R.string.metadata_number), numberLock) { numberLock = it }
                        LockSwitch(stringResource(R.string.metadata_authors), authorsLock) { authorsLock = it }
                        LockSwitch(stringResource(R.string.metadata_label), tagsLock) { tagsLock = it }
                        LockSwitch(stringResource(R.string.metadata_links), linksLock) { linksLock = it }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    editing: Boolean,
    onEditToggle: () -> Unit,
    canEditMetadata: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (canEditMetadata) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.metadata_refresh))
                        }
                    }
                    if (editing) {
                        IconButton(onClick = onSave, enabled = !saving) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.metadata_save))
                            }
                        }
                    } else if (canEditMetadata) {
                        IconButton(onClick = onEditToggle) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.metadata_edit))
                        }
                    }
                }
            )
        },
        content = content
    )
}

@Composable
private fun LoadingMetadata(padding: PaddingValues) {
    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MetadataCoverSection(
    targetType: String,
    id: String,
    thumbnailUrl: String,
    incomingCoverUri: String?,
    coverFocus: Boolean,
    canEditMetadata: Boolean,
    vm: MetadataViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidate by remember(incomingCoverUri, thumbnailUrl, canEditMetadata) {
        mutableStateOf(
            if (canEditMetadata) incomingCoverUri?.let { CoverCandidate(it.toUri(), R.string.metadata_cover_from_reader) } else null
        )
    }
    var crop by remember { mutableStateOf(CoverCrop.Full) }
    var previewBitmap by remember(candidate, crop) { mutableStateOf<Bitmap?>(null) }
    var loadingPreview by remember(candidate, crop) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) candidate = CoverCandidate(uri, R.string.metadata_cover_candidate)
    }
    val savedMessage = stringResource(R.string.metadata_cover_saved)
    val failedMessage = stringResource(R.string.metadata_cover_save_failed)
    val imageFailedMessage = stringResource(R.string.metadata_cover_image_failed)

    LaunchedEffect(candidate, crop) {
        val selected = candidate ?: return@LaunchedEffect
        loadingPreview = true
        previewBitmap = loadBitmap(context, selected.uri)?.let { cropCoverBitmap(it, crop) }
        loadingPreview = false
    }

    MetadataSection(stringResource(R.string.metadata_cover_section)) {
        if (coverFocus && canEditMetadata) {
            Text(stringResource(R.string.metadata_cover_from_reader), color = MaterialTheme.colorScheme.primary)
        }
        if (canEditMetadata) {
            Text(stringResource(R.string.metadata_cover_supporting), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(112.dp).aspectRatio(0.7f), contentAlignment = Alignment.Center) {
                when {
                    previewBitmap != null -> Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(candidate?.labelRes ?: R.string.metadata_cover_candidate),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    loadingPreview -> CircularProgressIndicator()
                    else -> SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = stringResource(R.string.metadata_cover_current),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { CircularProgressIndicator(Modifier.size(24.dp)) }
                    )
                }
            }
            if (canEditMetadata) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.metadata_cover_upload))
                    }
                    OutlinedButton(
                        onClick = { candidate = CoverCandidate(thumbnailUrl.toUri(), R.string.metadata_cover_current) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.metadata_cover_edit_current))
                    }
                }
            }
        }
        if (canEditMetadata && candidate != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CoverCrop.entries.forEach { option ->
                    FilterChip(
                        selected = crop == option,
                        onClick = { crop = option },
                        label = { Text(stringResource(option.labelRes)) }
                    )
                }
            }
            FilledTonalButton(
                enabled = candidate != null && !vm.coverSaving,
                onClick = {
                    val selected = candidate ?: return@FilledTonalButton
                    scope.launch {
                        val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, selected.uri) }
                        if (bitmap == null) {
                            Toast.makeText(context, imageFailedMessage, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val bytes = withContext(Dispatchers.Default) {
                            bitmapToJpegBytes(scaleCoverBitmapForUpload(cropCoverBitmap(bitmap, crop)))
                        }
                        val onDone: (Boolean) -> Unit = { ok ->
                            Toast.makeText(context, if (ok) savedMessage else failedMessage, Toast.LENGTH_SHORT).show()
                        }
                        if (targetType == "series") {
                            vm.uploadSeriesCover(id, bytes, onDone)
                        } else {
                            vm.uploadBookCover(id, bytes, onDone)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (vm.coverSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.metadata_cover_save))
                }
            }
        }
    }
}

private data class CoverCandidate(val uri: Uri, val labelRes: Int)

private val CoverCrop.labelRes: Int
    get() = when (this) {
        CoverCrop.Full -> R.string.metadata_cover_full
        CoverCrop.LeftHalf -> R.string.metadata_cover_left
        CoverCrop.RightHalf -> R.string.metadata_cover_right
    }

private suspend fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .allowHardware(false)
        .size(Size.ORIGINAL)
        .build()
    val result = context.imageLoader.execute(request)
    return (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesStatusField(status: String, editing: Boolean, onChange: (String) -> Unit) {
    if (editing) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = status,
                onValueChange = {},
                label = { Text(stringResource(R.string.metadata_status)) },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("ONGOING", "ENDED", "ABANDONED", "HIATUS").forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { onChange(value); expanded = false })
                }
            }
        }
    } else {
        MetaField(stringResource(R.string.metadata_status), status, false)
    }
}

@Composable
private fun MetadataSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LockSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AlternateTitlesEditor(
    titles: List<AlternateTitleDto>,
    editing: Boolean,
    showTitle: Boolean = true,
    onChange: (List<AlternateTitleDto>) -> Unit
) {
    StructuredPairsEditor(
        title = stringResource(R.string.metadata_alternate_titles),
        showTitle = showTitle,
        firstLabel = stringResource(R.string.metadata_label),
        secondLabel = stringResource(R.string.metadata_title),
        items = titles.map { it.label to it.title },
        editing = editing,
        emptyText = stringResource(R.string.metadata_no_alternate_titles),
        onChange = { onChange(it.map { pair -> AlternateTitleDto(pair.first, pair.second) }) }
    )
}

@Composable
private fun LinksEditor(
    links: List<WebLinkDto>,
    editing: Boolean,
    showTitle: Boolean = true,
    onChange: (List<WebLinkDto>) -> Unit
) {
    if (!editing) {
        val context = LocalContext.current
        val visibleLinks = links.filter { normalizeExternalUrl(it.url) != null }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showTitle) {
                Text(
                    stringResource(R.string.metadata_links),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visibleLinks.isEmpty()) {
                Text(stringResource(R.string.metadata_no_links), style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleLinks.forEach { link ->
                        AssistChip(
                            onClick = { openExternalUrl(context, link.url) },
                            label = { Text(link.label.ifBlank { link.url }) }
                        )
                    }
                }
            }
        }
        return
    }

    StructuredPairsEditor(
        title = stringResource(R.string.metadata_links),
        showTitle = showTitle,
        firstLabel = stringResource(R.string.metadata_link_name),
        secondLabel = stringResource(R.string.metadata_url),
        items = links.map { it.label to it.url },
        editing = editing,
        emptyText = stringResource(R.string.metadata_no_links),
        onChange = { onChange(it.map { pair -> WebLinkDto(pair.first, pair.second) }) }
    )
}

@Composable
private fun StructuredPairsEditor(
    title: String,
    showTitle: Boolean = true,
    firstLabel: String,
    secondLabel: String,
    items: List<Pair<String, String>>,
    editing: Boolean,
    emptyText: String,
    onChange: (List<Pair<String, String>>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editing) {
            val editable = items.ifEmpty { listOf("" to "") }
            editable.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = item.first,
                        onValueChange = { value -> onChange(editable.updated(index, value to item.second)) },
                        label = { Text(firstLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = item.second,
                        onValueChange = { value -> onChange(editable.updated(index, item.first to value)) },
                        label = { Text(secondLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    IconButton(
                        onClick = { onChange(editable.filterIndexed { itemIndex, _ -> itemIndex != index }) },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.metadata_delete_item))
                    }
                }
            }
            OutlinedButton(onClick = { onChange(editable + ("" to "")) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.metadata_add_item, title))
            }
        } else if (items.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium)
        } else {
            items.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.second.ifBlank { stringResource(R.string.empty_dash) }) },
                    supportingContent = { Text(item.first.ifBlank { stringResource(R.string.empty_dash) }) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
    }
}

@Composable
private fun AuthorsEditor(
    authors: List<AuthorDto>,
    editing: Boolean,
    onAuthorsChange: (List<AuthorDto>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.metadata_authors), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            val editableAuthors = authors.ifEmpty { listOf(AuthorDto()) }
            editableAuthors.forEachIndexed { index, author ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = author.name,
                        onValueChange = { value -> onAuthorsChange(editableAuthors.updated(index, author.copy(name = value))) },
                        label = { Text(stringResource(R.string.metadata_author_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = author.role,
                        onValueChange = { value -> onAuthorsChange(editableAuthors.updated(index, author.copy(role = value))) },
                        label = { Text(stringResource(R.string.metadata_author_role)) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    IconButton(
                        onClick = { onAuthorsChange(editableAuthors.filterIndexed { itemIndex, _ -> itemIndex != index }) },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.metadata_delete_item))
                    }
                }
            }
            OutlinedButton(onClick = { onAuthorsChange(editableAuthors + AuthorDto()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.metadata_add_author))
            }
        } else if (authors.isEmpty()) {
            Text(stringResource(R.string.empty_dash), style = MaterialTheme.typography.bodyMedium)
        } else {
            authors.forEach { author ->
                Text("${author.name} (${author.role})", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MetaField(
    label: String,
    value: String,
    editing: Boolean,
    maxLines: Int = 1,
    showLabel: Boolean = true,
    onValueChange: (String) -> Unit = {}
) {
    if (editing) {
        OutlinedTextField(
            value,
            onValueChange,
            label = if (showLabel) {
                { Text(label) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines
        )
    } else {
        Column {
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value.ifEmpty { stringResource(R.string.empty_dash) }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun String.toListField(): List<String> =
    split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }
