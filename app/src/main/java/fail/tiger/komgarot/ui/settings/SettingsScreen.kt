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
    var showPreloadDialog by remember { mutableStateOf(false) }

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
        }
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
