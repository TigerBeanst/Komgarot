package fail.tiger.komgarot.ui.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.data.remote.dto.AuthorDto
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
                            vm.saveSeriesMeta(
                                id,
                                SeriesMetadataDto(
                                    title = title,
                                    titleSort = title,
                                    status = status,
                                    summary = summary,
                                    publisher = publisher,
                                    ageRating = meta?.ageRating,
                                    language = language,
                                    readingDirection = meta?.readingDirection,
                                    genres = meta?.genres ?: emptyList(),
                                    tags = meta?.tags ?: emptyList()
                                )
                            )
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
    var tagsText by remember(meta) { mutableStateOf(meta?.tags?.joinToString(", ") ?: "") }
    var authors by remember(meta) { mutableStateOf(meta?.authors ?: emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Metadata") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (editing) {
                        IconButton(onClick = {
                            vm.saveBookMeta(
                                id,
                                BookMetadataDto(
                                    title = title,
                                    summary = summary,
                                    number = number,
                                    releaseDate = releaseDate.ifEmpty { null },
                                    authors = authors.filter { it.name.isNotBlank() || it.role.isNotBlank() },
                                    tags = tagsText.toListField()
                                )
                            )
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
                MetaField("Tags", tagsText, editing) { tagsText = it }
                AuthorsEditor(
                    authors = authors,
                    editing = editing,
                    onAuthorsChange = { authors = it }
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
        Text("Authors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            val editableAuthors = authors.ifEmpty { listOf(AuthorDto()) }
            editableAuthors.forEachIndexed { index, author ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = author.name,
                        onValueChange = { value ->
                            onAuthorsChange(editableAuthors.updated(index, author.copy(name = value)))
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = author.role,
                        onValueChange = { value ->
                            onAuthorsChange(editableAuthors.updated(index, author.copy(role = value)))
                        },
                        label = { Text("Role") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    IconButton(
                        onClick = { onAuthorsChange(editableAuthors.filterIndexed { itemIndex, _ -> itemIndex != index }) },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            OutlinedButton(onClick = { onAuthorsChange(editableAuthors + AuthorDto()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add author")
            }
        } else if (authors.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyMedium)
        } else {
            authors.forEach { author ->
                Text("${author.name} (${author.role})", style = MaterialTheme.typography.bodyMedium)
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

private fun String.toListField(): List<String> =
    split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun List<AuthorDto>.updated(index: Int, author: AuthorDto): List<AuthorDto> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) author else item }
