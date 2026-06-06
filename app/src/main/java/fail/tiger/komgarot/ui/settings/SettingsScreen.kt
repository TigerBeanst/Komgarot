package fail.tiger.komgarot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.CacheClearTarget
import fail.tiger.komgarot.data.local.CacheMaintenance
import fail.tiger.komgarot.data.local.CacheSizeOption
import fail.tiger.komgarot.data.local.ReaderPageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: AuthPreferences) {
    SettingsContent(
        prefs = prefs,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun MeScreen(
    userEmail: String?,
    isAdmin: Boolean,
    onCachedBooksClick: () -> Unit,
    onAdminClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.me)) })
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(userEmail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown)) },
                supportingContent = { Text(stringResource(if (isAdmin) R.string.admin_admin_role else R.string.user_role)) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.cached_books)) },
                supportingContent = { Text(stringResource(R.string.cached_books_entry_desc)) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCachedBooksClick)
            )
            if (isAdmin) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.admin)) },
                    supportingContent = { Text(stringResource(R.string.admin_entry_desc)) },
                    leadingContent = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onAdminClick)
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings)) },
                supportingContent = { Text(stringResource(R.string.settings_entry_desc)) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSettingsClick)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onLogout)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
private fun SettingsContent(
    prefs: AuthPreferences,
    topBar: @Composable () -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf(CacheSizeUi.loading()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCoverCacheSizeDialog by remember { mutableStateOf(false) }
    var showReaderCacheSizeDialog by remember { mutableStateOf(false) }
    val alwaysIncognito by prefs.alwaysIncognito.collectAsState(initial = false)
    val preloadPages by prefs.preloadPages.collectAsState(initial = 5)
    val readingDirection by prefs.readingDirection.collectAsState(initial = "LTR")
    val pageFit by prefs.pageFit.collectAsState(initial = "FIT")
    val keepScreenOn by prefs.keepScreenOn.collectAsState(initial = true)
    val coverCacheSizeMb by prefs.coverCacheSizeMb.collectAsState(initial = CacheSizeOption.default.sizeMb)
    val readerCacheSizeMb by prefs.readerCacheSizeMb.collectAsState(initial = CacheSizeOption.default.sizeMb)
    val clearCacheOnStartup by prefs.clearCacheOnStartup.collectAsState(initial = false)
    var showPreloadDialog by remember { mutableStateOf(false) }
    var showReadingDialog by remember { mutableStateOf(false) }
    var showFitDialog by remember { mutableStateOf(false) }
    val appLockEnabled by prefs.appLockEnabled.collectAsState(initial = false)
    val appLockTimeout by prefs.appLockTimeout.collectAsState(initial = 0)
    var showLockTimeoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = getCacheSize(context)
    }

    Scaffold(topBar = topBar) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            headerContent()
            SettingsSectionHeader(stringResource(R.string.settings_section_cache))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                supportingContent = { Text(cacheSize.displayText()) },
                modifier = Modifier.clickable { showClearDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_cover_cache_size)) },
                supportingContent = { Text(formatCacheSize(CacheSizeOption.fromMb(coverCacheSizeMb).bytes)) },
                modifier = Modifier.clickable { showCoverCacheSizeDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reader_cache_size)) },
                supportingContent = { Text(formatCacheSize(CacheSizeOption.fromMb(readerCacheSizeMb).bytes)) },
                modifier = Modifier.clickable { showReaderCacheSizeDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_cache_on_startup)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_cache_on_startup_desc)) },
                trailingContent = {
                    Switch(
                        checked = clearCacheOnStartup,
                        onCheckedChange = { scope.launch { prefs.setClearCacheOnStartup(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setClearCacheOnStartup(!clearCacheOnStartup) }
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_reading))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_always_incognito)) },
                supportingContent = { Text(stringResource(R.string.settings_always_incognito_desc)) },
                trailingContent = {
                    Switch(
                        checked = alwaysIncognito,
                        onCheckedChange = { scope.launch { prefs.setAlwaysIncognito(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setAlwaysIncognito(!alwaysIncognito) } }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_preload_pages)) },
                supportingContent = { Text(stringResource(R.string.settings_preload_pages_desc, preloadPages)) },
                modifier = Modifier.clickable { showPreloadDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reading_direction)) },
                supportingContent = {
                    Text(stringResource(if (readingDirection == "RTL") R.string.settings_reading_rtl_desc else R.string.settings_reading_ltr_desc))
                },
                modifier = Modifier.clickable { showReadingDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_page_fit)) },
                supportingContent = {
                    Text(stringResource(if (pageFit == "WIDTH") R.string.settings_page_fit_width else R.string.settings_page_fit_fit))
                },
                modifier = Modifier.clickable { showFitDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_desc)) },
                trailingContent = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { scope.launch { prefs.setKeepScreenOn(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setKeepScreenOn(!keepScreenOn) } }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_security))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_app_lock_desc)) },
                trailingContent = {
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { scope.launch { prefs.setAppLockEnabled(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setAppLockEnabled(!appLockEnabled) } }
            )
            if (appLockEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lock_timeout)) },
                    supportingContent = {
                        Text(
                            if (appLockTimeout == 0) {
                                stringResource(R.string.settings_lock_every_time)
                            } else {
                                stringResource(R.string.settings_lock_after_minutes, appLockTimeout)
                            }
                        )
                    },
                    modifier = Modifier.clickable { showLockTimeoutDialog = true }
                )
            }
        }
    }

    if (showLockTimeoutDialog) {
        var sliderValue by remember { mutableFloatStateOf(appLockTimeout.toFloat()) }
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text(stringResource(R.string.settings_lock_timeout)) },
            text = {
                Column {
                    Text(
                        if (sliderValue.toInt() == 0) {
                            stringResource(R.string.settings_lock_every_time)
                        } else {
                            stringResource(R.string.settings_lock_after_minutes, sliderValue.toInt())
                        }
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..60f,
                        steps = 11
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setAppLockTimeout(sliderValue.toInt()) }
                    showLockTimeoutDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showPreloadDialog) {
        var sliderValue by remember { mutableFloatStateOf(preloadPages.toFloat()) }
        AlertDialog(
            onDismissRequest = { showPreloadDialog = false },
            title = { Text(stringResource(R.string.settings_preload_pages)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_preload_slider, sliderValue.toInt()))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setPreloadPages(sliderValue.toInt()) }
                    showPreloadDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPreloadDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReadingDialog) {
        AlertDialog(
            onDismissRequest = { showReadingDialog = false },
            title = { Text(stringResource(R.string.settings_reading_direction)) },
            text = {
                Column {
                    RadioOption("LTR", stringResource(R.string.settings_reading_ltr), readingDirection) {
                        scope.launch { prefs.setReadingDirection(it) }
                        showReadingDialog = false
                    }
                    RadioOption("RTL", stringResource(R.string.settings_reading_rtl), readingDirection) {
                        scope.launch { prefs.setReadingDirection(it) }
                        showReadingDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showFitDialog) {
        AlertDialog(
            onDismissRequest = { showFitDialog = false },
            title = { Text(stringResource(R.string.settings_page_fit)) },
            text = {
                Column {
                    RadioOption("FIT", stringResource(R.string.settings_page_fit_fit), pageFit) {
                        scope.launch { prefs.setPageFit(it) }
                        showFitDialog = false
                    }
                    RadioOption("WIDTH", stringResource(R.string.settings_page_fit_width), pageFit) {
                        scope.launch { prefs.setPageFit(it) }
                        showFitDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = {
                Column {
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_all),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.All)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_covers),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.Covers)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_reader_pages),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.ReaderPages)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCoverCacheSizeDialog) {
        CacheSizeDialog(
            title = stringResource(R.string.settings_cover_cache_size),
            selectedSizeMb = coverCacheSizeMb,
            onSelect = {
                scope.launch { prefs.setCoverCacheSizeMb(it) }
                showCoverCacheSizeDialog = false
            },
            onDismiss = { showCoverCacheSizeDialog = false }
        )
    }

    if (showReaderCacheSizeDialog) {
        CacheSizeDialog(
            title = stringResource(R.string.settings_reader_cache_size),
            selectedSizeMb = readerCacheSizeMb,
            onSelect = {
                scope.launch {
                    prefs.setReaderCacheSizeMb(it)
                    ReaderPageCache.prune(context, CacheSizeOption.fromMb(it).bytes)
                    cacheSize = getCacheSize(context)
                }
                showReaderCacheSizeDialog = false
            },
            onDismiss = { showReaderCacheSizeDialog = false }
        )
    }
}

@Composable
private fun RadioOption(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalCoilApi::class)
private suspend fun getCacheSize(context: android.content.Context): CacheSizeUi = withContext(Dispatchers.IO) {
    val diskCache = context.imageLoader.diskCache
    val imageBytes = diskCache?.size ?: 0L
    val readerBytes = ReaderPageCache.size(context)
    CacheSizeUi(
        imageBytes = imageBytes,
        readerBytes = readerBytes
    )
}

private data class CacheSizeUi(
    val imageBytes: Long,
    val readerBytes: Long
) {
    @Composable
    fun displayText(): String =
        stringResource(
            R.string.settings_cache_size,
            formatCacheSize(imageBytes + readerBytes),
            formatCacheSize(imageBytes),
            formatCacheSize(readerBytes)
        )

    companion object {
        fun loading(): CacheSizeUi = CacheSizeUi(imageBytes = -1L, readerBytes = -1L)
    }
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes < 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

@Composable
private fun CacheClearOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
private fun CacheSizeDialog(
    title: String,
    selectedSizeMb: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                CacheSizeOption.values.forEach { option ->
                    RadioOption(
                        value = option.sizeMb.toString(),
                        label = formatCacheSize(option.bytes),
                        selected = CacheSizeOption.fromMb(selectedSizeMb).sizeMb.toString(),
                        onSelect = { onSelect(option.sizeMb) }
                    )
                }
            }
        },
        confirmButton = {}
    )
}
