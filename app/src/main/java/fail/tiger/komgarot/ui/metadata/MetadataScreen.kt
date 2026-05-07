package fail.tiger.komgarot.ui.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto

@Composable
fun MetadataScreen(type: String, id: String, onBack: () -> Unit, vm: MetadataViewModel) {
    LaunchedEffect(id) {
        if (type == "series") vm.loadSeries(id) else vm.loadBook(id)
    }
    var editing by remember { mutableStateOf(false) }
    if (type == "series") {
        SeriesMetadataContent(id, onBack, vm, editing) { editing = !editing }
    } else {
        BookMetadataContent(id, onBack, vm, editing) { editing = !editing }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesMetadataContent(id: String, onBack: () -> Unit, vm: MetadataViewModel, editing: Boolean, onEditToggle: () -> Unit) {
    val meta = vm.seriesMeta
    var title by remember(meta) { mutableStateOf(meta?.title ?: "") }
    var status by remember(meta) { mutableStateOf(meta?.status ?: "ONGOING") }
    var summary by remember(meta) { mutableStateOf(meta?.summary ?: "") }
    var publisher by remember(meta) { mutableStateOf(meta?.publisher ?: "") }
    var language by remember(meta) { mutableStateOf(meta?.language ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series Metadata") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (editing) {
                        IconButton(onClick = {
                            vm.saveSeriesMeta(id, SeriesMetadataDto(title, title, status, summary, publisher, meta?.ageRating, language, meta?.genres ?: emptyList(), meta?.tags ?: emptyList()))
                            onEditToggle()
                        }) { Icon(Icons.Default.Check, null) }
                    } else {
                        IconButton(onClick = onEditToggle) { Icon(Icons.Default.Edit, null) }
                    }
                }
            )
        }
    ) { padding ->
        if (meta == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetaField("Title", title, editing) { title = it }
                MetaField("Publisher", publisher, editing) { publisher = it }
                MetaField("Language", language, editing) { language = it }
                MetaField("Summary", summary, editing, maxLines = 5) { summary = it }
                if (editing) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(status, {}, label = { Text("Status") }, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("ONGOING", "ENDED", "ABANDONED", "HIATUS").forEach { s ->
                                DropdownMenuItem(text = { Text(s) }, onClick = { status = s; expanded = false })
                            }
                        }
                    }
                } else {
                    MetaField("Status", status, false)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMetadataContent(id: String, onBack: () -> Unit, vm: MetadataViewModel, editing: Boolean, onEditToggle: () -> Unit) {
    val meta = vm.bookMeta
    var title by remember(meta) { mutableStateOf(meta?.title ?: "") }
    var summary by remember(meta) { mutableStateOf(meta?.summary ?: "") }
    var number by remember(meta) { mutableStateOf(meta?.number ?: "") }
    var releaseDate by remember(meta) { mutableStateOf(meta?.releaseDate ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Metadata") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (editing) {
                        IconButton(onClick = {
                            vm.saveBookMeta(id, BookMetadataDto(title, summary, number, releaseDate.ifEmpty { null }, meta?.authors ?: emptyList(), meta?.tags ?: emptyList()))
                            onEditToggle()
                        }) { Icon(Icons.Default.Check, null) }
                    } else {
                        IconButton(onClick = onEditToggle) { Icon(Icons.Default.Edit, null) }
                    }
                }
            )
        }
    ) { padding ->
        if (meta == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetaField("Title", title, editing) { title = it }
                MetaField("Number", number, editing) { number = it }
                MetaField("Release Date", releaseDate, editing) { releaseDate = it }
                MetaField("Summary", summary, editing, maxLines = 5) { summary = it }
            }
        }
    }
}

@Composable
private fun MetaField(label: String, value: String, editing: Boolean, maxLines: Int = 1, onValueChange: (String) -> Unit = {}) {
    if (editing) {
        OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), maxLines = maxLines)
    } else {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
