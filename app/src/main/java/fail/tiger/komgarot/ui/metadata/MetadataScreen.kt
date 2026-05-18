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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.data.remote.dto.AlternateTitleDto
import fail.tiger.komgarot.data.remote.dto.AuthorDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import fail.tiger.komgarot.data.remote.dto.WebLinkDto

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series Metadata") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.refreshMetadata("series", id) }) { Icon(Icons.Default.Refresh, null) }
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
                MetadataSection("基础信息") {
                    MetaField("标题", title, editing) { title = it }
                    MetaField("出版社", publisher, editing) { publisher = it }
                    MetaField("语言", language, editing) { language = it }
                    MetaField("年龄分级", ageRating, editing) { ageRating = it.filter(Char::isDigit) }
                    MetaField("阅读方向", readingDirection, editing) { readingDirection = it }
                    if (editing) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = it }) {
                            OutlinedTextField(status, {}, label = { Text("状态") }, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                            ExposedDropdownMenu(expanded, { expanded = false }) {
                                listOf("ONGOING", "ENDED", "ABANDONED", "HIATUS").forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { status = s; expanded = false })
                                }
                            }
                        }
                    } else {
                        MetaField("状态", status, false)
                    }
                }
                MetadataSection("简介") {
                    MetaField("简介", summary, editing, maxLines = 6) { summary = it }
                }
                MetadataSection("分类") {
                    MetaField("类型", genresText, editing) { genresText = it }
                    MetaField("标签", tagsText, editing) { tagsText = it }
                    MetaField("分享标签", sharingLabelsText, editing) { sharingLabelsText = it }
                }
                MetadataSection("别名与外链") {
                    AlternateTitlesEditor(alternateTitles, editing) { alternateTitles = it }
                    LinksEditor(links, editing) { links = it }
                }
                if (editing) {
                    MetadataSection("锁定字段") {
                        LockSwitch("标题", titleLock) { titleLock = it }
                        LockSwitch("简介", summaryLock) { summaryLock = it }
                        LockSwitch("出版社", publisherLock) { publisherLock = it }
                        LockSwitch("类型", genresLock) { genresLock = it }
                        LockSwitch("标签", tagsLock) { tagsLock = it }
                        LockSwitch("外链", linksLock) { linksLock = it }
                    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Metadata") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.refreshMetadata("book", id) }) { Icon(Icons.Default.Refresh, null) }
                    if (editing) {
                        IconButton(onClick = {
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
                MetadataSection("基础信息") {
                    MetaField("标题", title, editing) { title = it }
                    MetaField("册号", number, editing) { number = it }
                    MetaField("排序册号", numberSort, editing) { numberSort = it }
                    MetaField("发布日期", releaseDate, editing) { releaseDate = it }
                    MetaField("ISBN", isbn, editing) { isbn = it }
                }
                MetadataSection("简介") {
                    MetaField("简介", summary, editing, maxLines = 6) { summary = it }
                }
                MetadataSection("作者") {
                    AuthorsEditor(
                        authors = authors,
                        editing = editing,
                        onAuthorsChange = { authors = it }
                    )
                }
                MetadataSection("标签与外链") {
                    MetaField("标签", tagsText, editing) { tagsText = it }
                    LinksEditor(links, editing) { links = it }
                }
                if (editing) {
                    MetadataSection("锁定字段") {
                        LockSwitch("标题", titleLock) { titleLock = it }
                        LockSwitch("简介", summaryLock) { summaryLock = it }
                        LockSwitch("册号", numberLock) { numberLock = it }
                        LockSwitch("作者", authorsLock) { authorsLock = it }
                        LockSwitch("标签", tagsLock) { tagsLock = it }
                        LockSwitch("外链", linksLock) { linksLock = it }
                    }
                }
            }
        }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AlternateTitlesEditor(
    titles: List<AlternateTitleDto>,
    editing: Boolean,
    onChange: (List<AlternateTitleDto>) -> Unit
) {
    StructuredPairsEditor(
        title = "别名",
        firstLabel = "标签",
        secondLabel = "标题",
        items = titles.map { it.label to it.title },
        editing = editing,
        emptyText = "没有别名",
        onChange = { onChange(it.map { pair -> AlternateTitleDto(pair.first, pair.second) }) }
    )
}

@Composable
private fun LinksEditor(
    links: List<WebLinkDto>,
    editing: Boolean,
    onChange: (List<WebLinkDto>) -> Unit
) {
    StructuredPairsEditor(
        title = "外链",
        firstLabel = "名称",
        secondLabel = "URL",
        items = links.map { it.label to it.url },
        editing = editing,
        emptyText = "没有外链",
        onChange = { onChange(it.map { pair -> WebLinkDto(pair.first, pair.second) }) }
    )
}

@Composable
private fun StructuredPairsEditor(
    title: String,
    firstLabel: String,
    secondLabel: String,
    items: List<Pair<String, String>>,
    editing: Boolean,
    emptyText: String,
    onChange: (List<Pair<String, String>>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            OutlinedButton(onClick = { onChange(editable + ("" to "")) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加$title")
            }
        } else if (items.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium)
        } else {
            items.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.second.ifBlank { "—" }) },
                    supportingContent = { Text(item.first.ifBlank { "—" }) },
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
        Text("作者", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            val editableAuthors = authors.ifEmpty { listOf(AuthorDto()) }
            editableAuthors.forEachIndexed { index, author ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = author.name,
                        onValueChange = { value ->
                            onAuthorsChange(editableAuthors.updated(index, author.copy(name = value)))
                        },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = author.role,
                        onValueChange = { value ->
                            onAuthorsChange(editableAuthors.updated(index, author.copy(role = value)))
                        },
                        label = { Text("角色") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    IconButton(
                        onClick = { onAuthorsChange(editableAuthors.filterIndexed { itemIndex, _ -> itemIndex != index }) },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            OutlinedButton(onClick = { onAuthorsChange(editableAuthors + AuthorDto()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加作者")
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

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }
