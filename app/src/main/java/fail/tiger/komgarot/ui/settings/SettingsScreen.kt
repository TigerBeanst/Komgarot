package fail.tiger.komgarot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import fail.tiger.komgarot.data.local.AuthPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: AuthPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf("计算中...") }
    var showClearDialog by remember { mutableStateOf(false) }
    val alwaysIncognito by prefs.alwaysIncognito.collectAsState(initial = false)
    val preloadPages by prefs.preloadPages.collectAsState(initial = 5)
    val readingDirection by prefs.readingDirection.collectAsState(initial = "LTR")
    val pageFit by prefs.pageFit.collectAsState(initial = "FIT")
    val keepScreenOn by prefs.keepScreenOn.collectAsState(initial = true)
    var showPreloadDialog by remember { mutableStateOf(false) }
    var showReadingDialog by remember { mutableStateOf(false) }
    var showFitDialog by remember { mutableStateOf(false) }
    val appLockEnabled by prefs.appLockEnabled.collectAsState(initial = false)
    val appLockTimeout by prefs.appLockTimeout.collectAsState(initial = 0)
    var showLockTimeoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = getCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text("清除图片缓存") },
                supportingContent = { Text("当前缓存: $cacheSize") },
                modifier = Modifier.clickable { showClearDialog = true }
            )
            ListItem(
                headlineContent = { Text("始终无痕") },
                supportingContent = { Text("阅读和继续阅读均不记录进度") },
                trailingContent = {
                    Switch(
                        checked = alwaysIncognito,
                        onCheckedChange = { scope.launch { prefs.setAlwaysIncognito(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setAlwaysIncognito(!alwaysIncognito) } }
            )
            ListItem(
                headlineContent = { Text("预加载页数") },
                supportingContent = { Text("阅读时向后预加载 $preloadPages 页") },
                modifier = Modifier.clickable { showPreloadDialog = true }
            )
            ListItem(
                headlineContent = { Text("阅读方向") },
                supportingContent = { Text(if (readingDirection == "RTL") "从右向左翻页" else "从左向右翻页") },
                modifier = Modifier.clickable { showReadingDialog = true }
            )
            ListItem(
                headlineContent = { Text("页面适配") },
                supportingContent = { Text(if (pageFit == "WIDTH") "适合宽度" else "完整显示页面") },
                modifier = Modifier.clickable { showFitDialog = true }
            )
            ListItem(
                headlineContent = { Text("阅读时保持亮屏") },
                supportingContent = { Text("打开阅读器时不自动息屏") },
                trailingContent = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { scope.launch { prefs.setKeepScreenOn(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setKeepScreenOn(!keepScreenOn) } }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("应用锁定") },
                supportingContent = { Text("进入应用时需要验证身份") },
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
                    headlineContent = { Text("锁定宽限时间") },
                    supportingContent = { Text(if (appLockTimeout == 0) "每次回到应用都锁定" else "离开 $appLockTimeout 分钟后锁定") },
                    modifier = Modifier.clickable { showLockTimeoutDialog = true }
                )
            }
        }
    }

    if (showLockTimeoutDialog) {
        var sliderValue by remember { mutableFloatStateOf(appLockTimeout.toFloat()) }
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text("锁定宽限时间") },
            text = {
                Column {
                    Text(if (sliderValue.toInt() == 0) "每次回到应用都锁定" else "离开 ${sliderValue.toInt()} 分钟后锁定")
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
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) { Text("取消") }
            }
        )
    }

    if (showPreloadDialog) {
        var sliderValue by remember { mutableFloatStateOf(preloadPages.toFloat()) }
        AlertDialog(
            onDismissRequest = { showPreloadDialog = false },
            title = { Text("预加载页数") },
            text = {
                Column {
                    Text("向后预加载 ${sliderValue.toInt()} 页")
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
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPreloadDialog = false }) { Text("取消") }
            }
        )
    }

    if (showReadingDialog) {
        AlertDialog(
            onDismissRequest = { showReadingDialog = false },
            title = { Text("阅读方向") },
            text = {
                Column {
                    RadioOption("LTR", "从左向右", readingDirection) {
                        scope.launch { prefs.setReadingDirection(it) }
                        showReadingDialog = false
                    }
                    RadioOption("RTL", "从右向左", readingDirection) {
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
            title = { Text("页面适配") },
            text = {
                Column {
                    RadioOption("FIT", "完整显示页面", pageFit) {
                        scope.launch { prefs.setPageFit(it) }
                        showFitDialog = false
                    }
                    RadioOption("WIDTH", "适合宽度", pageFit) {
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
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有图片缓存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                        cacheSize = getCacheSize(context)
                        showClearDialog = false
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
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

private suspend fun getCacheSize(context: android.content.Context): String = withContext(Dispatchers.IO) {
    val diskCache = context.imageLoader.diskCache
    val size = diskCache?.size ?: 0L
    formatFileSize(size)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
