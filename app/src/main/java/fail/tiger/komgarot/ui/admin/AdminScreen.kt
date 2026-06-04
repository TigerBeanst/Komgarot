package fail.tiger.komgarot.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SettingsUpdateDto
import fail.tiger.komgarot.data.remote.dto.UserDto
import fail.tiger.komgarot.ui.components.ConfirmActionDialog
import fail.tiger.komgarot.ui.components.EmptyState
import fail.tiger.komgarot.ui.components.InfoPill
import fail.tiger.komgarot.ui.components.SectionHeader

private enum class AdminTab(@StringRes val labelRes: Int) {
    Overview(R.string.admin_tab_overview),
    Libraries(R.string.admin_tab_libraries),
    Users(R.string.admin_tab_users),
    Settings(R.string.admin_tab_settings),
    Integrity(R.string.admin_tab_integrity),
    Activity(R.string.admin_tab_activity)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    isAdmin: Boolean,
    vm: AdminViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(isAdmin) {
        if (isAdmin) vm.load()
    }
    var tab by remember { mutableStateOf(AdminTab.Overview) }

    if (!isAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.admin)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        ) { padding ->
            EmptyState(message = stringResource(R.string.admin_no_permission), modifier = Modifier.padding(padding))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = vm::load,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (vm.error != null) {
                    AssistChip(
                        onClick = { vm.load() },
                        label = { Text(stringResource(R.string.admin_partial_load_failed, vm.error.orEmpty())) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp) {
                    AdminTab.entries.forEach { item ->
                        Tab(
                            selected = tab == item,
                            onClick = { tab = item },
                            text = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
                when (tab) {
                    AdminTab.Overview -> AdminOverview(vm)
                    AdminTab.Libraries -> LibrariesAdmin(vm)
                    AdminTab.Users -> UsersAdmin(vm)
                    AdminTab.Settings -> SettingsAdmin(vm)
                    AdminTab.Integrity -> IntegrityAdmin(vm)
                    AdminTab.Activity -> ActivityAdmin(vm)
                }
            }
        }
    }

    vm.feedback?.let {
        AlertDialog(
            onDismissRequest = vm::clearFeedback,
            title = { Text(stringResource(R.string.admin_operation_complete)) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearFeedback) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun AdminOverview(vm: AdminViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SectionHeader(stringResource(R.string.admin_server_overview))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stringResource(R.string.admin_libraries), vm.libraries.size.toString(), Modifier.weight(1f))
                StatCard(stringResource(R.string.admin_users), vm.users.size.toString(), Modifier.weight(1f))
                StatCard(stringResource(R.string.admin_duplicate_books), vm.duplicateBooks.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val unknown = stringResource(R.string.unknown)
                    Text(stringResource(R.string.admin_server_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.admin_thumbnail_size, vm.settings?.thumbnailSize ?: unknown))
                    Text(stringResource(R.string.admin_task_threads, vm.settings?.taskPoolSize?.toString() ?: unknown))
                    Text(stringResource(R.string.admin_claim_status, if (vm.claimStatus?.claimed != false) stringResource(R.string.admin_claimed) else stringResource(R.string.admin_unclaimed)))
                    if (vm.oauthProviders.isNotEmpty()) {
                        Text(stringResource(R.string.admin_oauth_providers, vm.oauthProviders.joinToString { it.label.ifBlank { it.name } }))
                    }
                }
            }
        }
        item {
            SectionHeader(stringResource(R.string.admin_latest_activity))
        }
        items(vm.history.take(8), key = { it.id }) { event ->
            ListItem(
                headlineContent = { Text(event.type) },
                supportingContent = { Text(event.timestamp) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrariesAdmin(vm: AdminViewModel) {
    var editing by remember { mutableStateOf<LibraryDto?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LibraryDto?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.admin_libraries), modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_new))
                }
            }
        }
        items(vm.libraries, key = { it.id }) { library ->
            val scanActionText = stringResource(R.string.admin_action_scan_library, library.name, library.id)
            val analyzeActionText = stringResource(R.string.admin_action_analyze_library, library.name, library.id)
            val refreshMetadataActionText = stringResource(R.string.admin_action_refresh_library, library.name, library.id)
            val emptyTrashActionText = stringResource(R.string.admin_action_empty_trash, library.name, library.id)
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(library.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(library.root.ifBlank { library.id }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = library }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.admin_edit_library))
                        }
                        IconButton(onClick = { pendingDelete = library }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete_library), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (library.unavailable) InfoPill(stringResource(R.string.admin_library_unavailable))
                        InfoPill(library.scanInterval ?: stringResource(R.string.disabled))
                        if (library.scanOnStartup) InfoPill(stringResource(R.string.admin_library_scan_startup))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingAction = scanActionText to { vm.scanLibrary(library.id) } }) { Text(stringResource(R.string.admin_scan)) }
                        OutlinedButton(onClick = { pendingAction = analyzeActionText to { vm.analyzeLibrary(library.id) } }) { Text(stringResource(R.string.admin_analyze)) }
                        OutlinedButton(onClick = { pendingAction = refreshMetadataActionText to { vm.refreshLibraryMetadata(library.id) } }) { Text(stringResource(R.string.admin_refresh_metadata)) }
                        OutlinedButton(onClick = { pendingAction = emptyTrashActionText to { vm.emptyLibraryTrash(library.id) } }) { Text(stringResource(R.string.admin_empty_trash)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        LibraryEditorDialog(
            library = null,
            onDismiss = { showCreate = false },
            onSave = { name, root ->
                vm.createLibrary(name, root) { showCreate = false }
            }
        )
    }
    editing?.let { library ->
        LibraryEditorDialog(
            library = library,
            onDismiss = { editing = null },
            onSave = { name, root ->
                vm.updateLibrary(library, name, root) { editing = null }
            }
        )
    }
    pendingDelete?.let { library ->
        ConfirmActionDialog(
            title = stringResource(R.string.admin_delete_library),
            text = stringResource(R.string.admin_delete_library_message, library.name, library.id),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteLibrary(library.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
    pendingAction?.let { (text, action) ->
        ConfirmActionDialog(
            title = stringResource(R.string.admin_confirm_operation_title),
            text = text,
            confirmText = stringResource(R.string.admin_execute),
            destructive = false,
            onConfirm = {
                action()
                pendingAction = null
            },
            onDismiss = { pendingAction = null }
        )
    }
}

@Composable
private fun UsersAdmin(vm: AdminViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var showApiKeyCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UserDto?>(null) }
    var pendingApiKeyDelete by remember { mutableStateOf<String?>(null) }
    var passwordUser by remember { mutableStateOf<UserDto?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.admin_users), modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_new))
                }
            }
        }
        items(vm.users, key = { it.id }) { user ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(user.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.id_format, user.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            user.roles.forEach { InfoPill(it) }
                            InfoPill(if (user.sharedAllLibraries) stringResource(R.string.admin_all_libraries) else stringResource(R.string.admin_libraries_count, user.sharedLibrariesIds.size))
                        }
                    }
                    IconButton(onClick = { vm.updateUser(user, !user.isAdmin, user.sharedAllLibraries, user.sharedLibrariesIds) }) {
                        Icon(Icons.Default.Security, contentDescription = stringResource(R.string.admin_toggle_admin))
                    }
                    IconButton(onClick = { passwordUser = user }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.admin_change_password))
                    }
                    IconButton(onClick = { pendingDelete = user }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete_user), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.admin_current_user_api_keys), modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { showApiKeyCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_new))
                }
            }
        }
        items(vm.apiKeys, key = { it.id }) { key ->
            ListItem(
                headlineContent = { Text(key.comment) },
                supportingContent = { Text(stringResource(R.string.admin_id_created, key.id, key.createdDate.orEmpty())) },
                trailingContent = {
                    IconButton(onClick = { pendingApiKeyDelete = key.id }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete_api_key), tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }

    if (showCreate) {
        UserEditorDialog(
            onDismiss = { showCreate = false },
            onSave = { email, password, admin ->
                vm.createUser(email, password, admin, allLibraries = true, libraryIds = emptyList()) { showCreate = false }
            }
        )
    }
    passwordUser?.let { user ->
        PasswordDialog(
            title = stringResource(R.string.admin_update_user_password, user.email),
            onDismiss = { passwordUser = null },
            onSave = {
                vm.updateUserPassword(user.id, it)
                passwordUser = null
            }
        )
    }
    if (showApiKeyCreate) {
        ApiKeyDialog(
            onDismiss = { showApiKeyCreate = false },
            onSave = {
                vm.createApiKey(it)
                showApiKeyCreate = false
            }
        )
    }
    pendingApiKeyDelete?.let { keyId ->
        ConfirmActionDialog(
            title = stringResource(R.string.admin_delete_api_key),
            text = stringResource(R.string.admin_delete_api_key_message, keyId),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteApiKey(keyId)
                pendingApiKeyDelete = null
            },
            onDismiss = { pendingApiKeyDelete = null }
        )
    }
    pendingDelete?.let { user ->
        ConfirmActionDialog(
            title = stringResource(R.string.admin_delete_user),
            text = stringResource(R.string.admin_delete_user_message, user.email, user.id),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteUser(user.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ApiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_new_api_key)) },
        text = {
            OutlinedTextField(comment, { comment = it }, label = { Text(stringResource(R.string.admin_comment)) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(comment.trim()) }, enabled = comment.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAdmin(vm: AdminViewModel) {
    val settings = vm.settings
    var thumbnailSize by remember(settings) { mutableStateOf(settings?.thumbnailSize ?: "DEFAULT") }
    var taskPool by remember(settings) { mutableStateOf(settings?.taskPoolSize?.toString() ?: "1") }
    var deleteEmptyCollections by remember(settings) { mutableStateOf(settings?.deleteEmptyCollections ?: false) }
    var deleteEmptyReadLists by remember(settings) { mutableStateOf(settings?.deleteEmptyReadLists ?: false) }
    var koboProxy by remember(settings) { mutableStateOf(settings?.koboProxy ?: false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { SectionHeader(stringResource(R.string.admin_server_settings)) }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.admin_thumbnail_size_setting), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow {
                        listOf("DEFAULT", "MEDIUM", "LARGE", "XLARGE").forEachIndexed { index, value ->
                            SegmentedButton(
                                selected = thumbnailSize == value,
                                onClick = { thumbnailSize = value },
                                shape = SegmentedButtonDefaults.itemShape(index, 4)
                            ) { Text(value) }
                        }
                    }
                    OutlinedTextField(value = taskPool, onValueChange = { taskPool = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.admin_task_pool_size)) }, singleLine = true)
                    SettingSwitch(stringResource(R.string.admin_delete_empty_collections), deleteEmptyCollections) { deleteEmptyCollections = it }
                    SettingSwitch(stringResource(R.string.admin_delete_empty_read_lists), deleteEmptyReadLists) { deleteEmptyReadLists = it }
                    SettingSwitch(stringResource(R.string.admin_kobo_proxy), koboProxy) { koboProxy = it }
                    Button(
                        onClick = {
                            vm.updateSettings(
                                SettingsUpdateDto(
                                    thumbnailSize = thumbnailSize,
                                    taskPoolSize = taskPool.toIntOrNull(),
                                    deleteEmptyCollections = deleteEmptyCollections,
                                    deleteEmptyReadLists = deleteEmptyReadLists,
                                    koboProxy = koboProxy
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.admin_save_server_settings)) }
                }
            }
        }
    }
}

@Composable
private fun IntegrityAdmin(vm: AdminViewModel) {
    var clearTasks by remember { mutableStateOf(false) }
    var deleteHash by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.admin_integrity), modifier = Modifier.weight(1f))
                Button(onClick = { clearTasks = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.admin_clear_tasks))
                }
            }
        }
        item { SectionHeader(stringResource(R.string.admin_duplicate_books)) }
        items(vm.duplicateBooks.take(20), key = { it.id }) { book ->
            ListItem(
                headlineContent = { Text(book.metadata.title.ifEmpty { book.name }) },
                supportingContent = { Text(stringResource(R.string.admin_bytes, book.seriesTitle.orEmpty(), book.sizeBytes ?: 0)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        item { SectionHeader(stringResource(R.string.admin_known_duplicate_pages)) }
        items(vm.knownHashes.take(20), key = { it.hash }) { hash ->
            ListItem(
                headlineContent = { Text(hash.hash) },
                supportingContent = { Text(stringResource(R.string.admin_matches_action, hash.matchCount, hash.action.orEmpty())) },
                trailingContent = {
                    TextButton(onClick = { deleteHash = hash.hash }) { Text(stringResource(R.string.admin_delete_all)) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        item { SectionHeader(stringResource(R.string.admin_unknown_duplicate_pages)) }
        items(vm.unknownHashes.take(20), key = { it.hash }) { hash ->
            ListItem(
                headlineContent = { Text(hash.hash) },
                supportingContent = { Text(stringResource(R.string.admin_matches, hash.matchCount)) },
                trailingContent = {
                    TextButton(onClick = { vm.markPageHashKnown(hash.hash, "IGNORE") }) { Text(stringResource(R.string.admin_ignore)) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
    if (clearTasks) {
        ConfirmActionDialog(
            title = stringResource(R.string.admin_clear_task_queue_title),
            text = stringResource(R.string.admin_clear_task_queue_message),
            confirmText = stringResource(R.string.admin_clear_tasks),
            onConfirm = {
                vm.clearTaskQueue()
                clearTasks = false
            },
            onDismiss = { clearTasks = false }
        )
    }
    deleteHash?.let { hash ->
        ConfirmActionDialog(
            title = stringResource(R.string.admin_delete_duplicate_pages_title),
            text = stringResource(R.string.admin_delete_duplicate_pages_message, hash),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteAllDuplicatePages(hash)
                deleteHash = null
            },
            onDismiss = { deleteHash = null }
        )
    }
}

@Composable
private fun ActivityAdmin(vm: AdminViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { SectionHeader(stringResource(R.string.admin_auth_activity)) }
        items(vm.authActivity.take(30)) { item ->
            ListItem(
                headlineContent = { Text(item.email ?: item.userId ?: stringResource(R.string.admin_unknown_user)) },
                supportingContent = { Text("${item.dateTime} · ${item.ip.orEmpty()} · ${item.source.orEmpty()}") },
                trailingContent = { InfoPill(if (item.success) stringResource(R.string.admin_success) else stringResource(R.string.admin_failure)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        item { SectionHeader(stringResource(R.string.admin_history)) }
        items(vm.history.take(30), key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.type) },
                supportingContent = { Text("${item.timestamp}\n${item.properties.entries.joinToString { "${it.key}: ${it.value}" }}") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        if (vm.announcements.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.admin_announcements), modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::markAnnouncementsRead) { Text(stringResource(R.string.admin_mark_all_read)) }
                }
            }
            items(vm.announcements, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.message) },
                    trailingContent = { if (!item.read) InfoPill(stringResource(R.string.admin_unread)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LibraryEditorDialog(
    library: LibraryDto?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(library) { mutableStateOf(library?.name.orEmpty()) }
    var root by remember(library) { mutableStateOf(library?.root.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (library == null) R.string.admin_new_library else R.string.admin_edit_library)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(root, { root = it }, label = { Text(stringResource(R.string.path)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), root.trim()) }, enabled = name.isNotBlank() && root.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun UserEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_new_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true)
                SettingSwitch(stringResource(R.string.admin_admin_role), admin) { admin = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(email.trim(), password, admin) }, enabled = email.isNotBlank() && password.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.new_password)) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(password) }, enabled = password.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
