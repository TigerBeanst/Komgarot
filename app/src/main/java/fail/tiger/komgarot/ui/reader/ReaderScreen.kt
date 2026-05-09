package fail.tiger.komgarot.ui.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    startPage: Int,
    trackProgress: Boolean = true,
    onBack: () -> Unit,
    vm: ReaderViewModel
) {
    LaunchedEffect(bookId) { vm.load(bookId, startPage, trackProgress) }

    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    DisposableEffect(Unit) {
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        onDispose {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(vm.showControls) {
        window?.let { w ->
            val ctrl = WindowInsetsControllerCompat(w, view)
            if (vm.showControls) {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            } else {
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (vm.mode) {
            ReadingMode.PAGER -> PagerReader(vm)
            ReadingMode.SCROLL -> ScrollReader(vm)
        }

        if (vm.showControls) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(color = Color.Black.copy(alpha = 0.6f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${vm.currentPage + 1} / ${vm.pageUrls.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleMode() }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "切换阅读模式（翻页/滚动）", tint = Color.White)
                        }
                    }
                }

                Surface(color = Color.Black.copy(alpha = 0.8f)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (vm.pageUrls.isNotEmpty()) {
                            Text(
                                "${vm.currentPage + 1} / ${vm.pageUrls.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (vm.currentPage > 0) vm.goToPage(vm.currentPage - 1) },
                                    enabled = vm.currentPage > 0
                                ) {
                                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous", tint = Color.White)
                                }
                                Slider(
                                    value = vm.currentPage.toFloat(),
                                    onValueChange = { vm.goToPage(it.toInt()) },
                                    valueRange = 0f..(vm.pageUrls.size - 1).toFloat(),
                                    steps = (vm.pageUrls.size - 2).coerceAtLeast(0),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { if (vm.currentPage < vm.pageUrls.size - 1) vm.goToPage(vm.currentPage + 1) },
                                    enabled = vm.currentPage < vm.pageUrls.size - 1
                                ) {
                                    Icon(Icons.Default.NavigateNext, contentDescription = "Next", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 独立的页面指示器（始终显示）
            if (vm.pageUrls.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${vm.currentPage + 1} / ${vm.pageUrls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerReader(vm: ReaderViewModel) {
    if (vm.pageUrls.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = vm.currentPage) { vm.pageUrls.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var longPressUrl by remember { mutableStateOf<String?>(null) }
    val preloadPages by vm.prefs.preloadPages.collectAsState(initial = 5)

    LaunchedEffect(pagerState.currentPage) { vm.updatePage(pagerState.currentPage) }
    LaunchedEffect(vm.currentPage) {
        if (pagerState.currentPage != vm.currentPage) pagerState.scrollToPage(vm.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = preloadPages,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val zoomState = rememberZoomState(maxScale = 5f)
        LaunchedEffect(page) { zoomState.reset() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(page) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val lp = awaitLongPressOrCancellation(down.id)
                        if (lp != null) longPressUrl = vm.pageUrls[page]
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(vm.pageUrls[page]).size(Size.ORIGINAL).build(),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoomState, onTap = { vm.toggleControls() })
            ) {
                if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                    }
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }

    longPressUrl?.let { url ->
        PageContextMenu(
            url = url,
            context = context,
            onDismiss = { longPressUrl = null },
            onSetBookPoster = { bytes ->
                vm.uploadBookThumbnail(bytes) { ok ->
                    Toast.makeText(context, if (ok) "已设置为书籍海报" else "设置失败", Toast.LENGTH_SHORT).show()
                }
            },
            onSetSeriesPoster = { bytes ->
                vm.uploadSeriesThumbnail(bytes) { ok ->
                    Toast.makeText(context, if (ok) "已设置为系列海报" else "设置失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContextMenu(
    url: String,
    context: Context,
    onDismiss: () -> Unit,
    onSetBookPoster: (ByteArray) -> Unit,
    onSetSeriesPoster: (ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()

    suspend fun loadBitmap(): Bitmap? {
        val loader = ImageLoader(context)
        val req = ImageRequest.Builder(context).data(url).build()
        val result = loader.execute(req)
        return (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
    }

    fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text("保存") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onDismiss()
                    scope.launch {
                        val bitmap = loadBitmap() ?: return@launch
                        withContext(Dispatchers.IO) { saveBitmapToGallery(context, bitmap) }
                        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            ListItem(
                headlineContent = { Text("分享") },
                leadingContent = { Icon(Icons.Default.Share, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onDismiss()
                    scope.launch {
                        val bitmap = loadBitmap() ?: return@launch
                        val uri = withContext(Dispatchers.IO) { saveBitmapToCache(context, bitmap) }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享图片"))
                    }
                }
            )
            ListItem(
                headlineContent = { Text("设置为书籍海报") },
                leadingContent = { Icon(Icons.Default.Book, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onDismiss()
                    scope.launch {
                        val bitmap = loadBitmap() ?: return@launch
                        onSetBookPoster(bitmapToBytes(bitmap))
                    }
                }
            )
            ListItem(
                headlineContent = { Text("设置为系列海报") },
                leadingContent = { Icon(Icons.Default.Collections, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onDismiss()
                    scope.launch {
                        val bitmap = loadBitmap() ?: return@launch
                        onSetSeriesPoster(bitmapToBytes(bitmap))
                    }
                }
            )
        }
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "komgarot_${System.currentTimeMillis()}.jpg"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): android.net.Uri {
    val file = java.io.File(context.cacheDir, "share_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@Composable
fun ScrollReader(vm: ReaderViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { vm.toggleControls() }
        }
    ) {
        items(vm.pageUrls.indices.toList()) { index ->
            Box(Modifier.fillMaxWidth().wrapContentHeight(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = vm.pageUrls[index],
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
                        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }
}
