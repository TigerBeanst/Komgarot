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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.SubcomposeAsyncImageScope
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.ByteArrayOutputStream

private fun readerPageMemoryCacheKey(url: String, allowHardware: Boolean): String =
    "reader-page:${if (allowHardware) "hardware" else "software"}:$url"

private fun readerPageRequest(
    context: Context,
    url: String,
    allowHardware: Boolean = true
): ImageRequest {
    val memoryKey = readerPageMemoryCacheKey(url, allowHardware)
    return ImageRequest.Builder(context)
        .data(url)
        .size(Size.ORIGINAL)
        .memoryCacheKey(memoryKey)
        .placeholderMemoryCacheKey(memoryKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .allowHardware(allowHardware)
        .build()
}

@Composable
private fun SubcomposeAsyncImageScope.CachedPageLoadingContent(
    state: AsyncImagePainter.State.Loading,
    modifier: Modifier = Modifier
) {
    if (state.painter != null) {
        SubcomposeAsyncImageContent()
    } else {
        PageLoadingPlaceholder(modifier)
    }
}

@Composable
private fun SubcomposeAsyncImageScope.CachedPageErrorContent(
    state: AsyncImagePainter.State.Error,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    if (state.painter != null) {
        SubcomposeAsyncImageContent()
    } else {
        Box(modifier) {
            ReaderPageError(onRetry = onRetry)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    startPage: Int,
    trackProgress: Boolean = true,
    onBack: () -> Unit,
    onOpenBook: (String, Int, Boolean) -> Unit,
    vm: ReaderViewModel
) {
    LaunchedEffect(bookId) { vm.load(bookId, startPage, trackProgress) }

    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    DisposableEffect(Unit) {
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        onDispose {
            vm.flushProgress()
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

        if (vm.pageUrls.isEmpty()) {
            ReaderStatusOverlay(
                loading = vm.loading,
                error = vm.error,
                onRetry = { vm.load(bookId, startPage, trackProgress) }
            )
        }

        AnimatedVisibility(
            visible = vm.showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            vm.previousBook?.let { onOpenBook(it.id, vm.startPageFor(it), vm.trackProgress) }
                        },
                        enabled = vm.previousBook != null
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一册", tint = Color.White)
                    }
                    IconButton(onClick = { vm.toggleMode() }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换阅读模式（翻页/滚动）", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            vm.nextBook?.let { onOpenBook(it.id, vm.startPageFor(it), vm.trackProgress) }
                        },
                        enabled = vm.nextBook != null
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一册", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = vm.showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Surface(color = Color.Black.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (vm.pageUrls.isNotEmpty()) {
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
                                steps = 0,
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

        // 页面指示器（始终显示，不受工具栏影响）
        if (vm.pageUrls.isNotEmpty()) {
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (vm.showControls) 0.85f else 0.45f,
                label = "page_indicator_alpha"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .alpha(indicatorAlpha)
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

@Composable
private fun ReaderStatusOverlay(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
            error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(error, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry) {
                    Text("重试", color = Color.White)
                }
            }
            else -> Text("没有可显示的页面", color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerReader(vm: ReaderViewModel) {
    if (vm.pageUrls.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = vm.currentPage) { vm.pageUrls.size }
    val context = LocalContext.current
    var longPressUrl by remember { mutableStateOf<String?>(null) }
    val preloadPages by vm.prefs.preloadPages.collectAsState(initial = 5)
    val imageLoader = coil.Coil.imageLoader(context)

    LaunchedEffect(pagerState.currentPage) { vm.updatePage(pagerState.currentPage) }
    LaunchedEffect(vm.currentPage) {
        if (pagerState.currentPage != vm.currentPage) pagerState.scrollToPage(vm.currentPage)
    }
    LaunchedEffect(pagerState.currentPage, preloadPages, vm.pageUrls) {
        val from = (pagerState.currentPage - 1).coerceAtLeast(0)
        val to = (pagerState.currentPage + preloadPages).coerceAtMost(vm.pageUrls.lastIndex)
        if (from <= to) {
            for (index in from..to) {
                if (index != pagerState.currentPage) {
                    imageLoader.enqueue(readerPageRequest(context, vm.pageUrls[index]))
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
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
                model = readerPageRequest(context, vm.pageUrls[page]),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoomState, onTap = { vm.toggleControls() })
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        CachedPageLoadingContent(
                            state = state,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AsyncImagePainter.State.Empty -> {
                        PageLoadingPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                    is AsyncImagePainter.State.Error -> {
                        CachedPageErrorContent(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onRetry = { vm.goToPage(page) }
                        )
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }

    longPressUrl?.let { url ->
        PageContextMenu(
            url = url,
            context = context,
            vm = vm,
            onDismiss = { longPressUrl = null },
        )
    }
}

@Composable
private fun PageLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
    }
}

@Composable
private fun ReaderPageError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("图片加载失败", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) {
            Text("重试", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContextMenu(
    url: String,
    context: Context,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    val imageLoader = coil.Coil.imageLoader(context)

    suspend fun loadBitmap(pageUrl: String): Bitmap? {
        val req = readerPageRequest(context, pageUrl, allowHardware = false)
        val result = imageLoader.execute(req)
        return (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
    }

    fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    fun doAction(pageUrl: String, action: suspend (Bitmap) -> Unit) {
        onDismiss()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val bitmap = loadBitmap(pageUrl) ?: return@launch
                action(bitmap)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text("保存") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val pageUrl = url
                    doAction(pageUrl) { bitmap ->
                        saveBitmapToGallery(context, bitmap)
                        withContext(Dispatchers.Main) { Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show() }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("分享") },
                leadingContent = { Icon(Icons.Default.Share, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val pageUrl = url
                    doAction(pageUrl) { bitmap ->
                        val uri = saveBitmapToCache(context, bitmap)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        withContext(Dispatchers.Main) {
                            context.startActivity(Intent.createChooser(intent, "分享图片"))
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("设置为书籍海报") },
                leadingContent = { Icon(Icons.Default.Book, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val pageUrl = url
                    doAction(pageUrl) { bitmap ->
                        vm.uploadBookThumbnail(bitmapToBytes(bitmap)) { ok ->
                            Toast.makeText(context, if (ok) "已设置为书籍海报" else "设置失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("设置为系列海报") },
                leadingContent = { Icon(Icons.Default.Collections, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val pageUrl = url
                    doAction(pageUrl) { bitmap ->
                        vm.uploadSeriesThumbnail(bitmapToBytes(bitmap)) { ok ->
                            Toast.makeText(context, if (ok) "已设置为系列海报" else "设置失败", Toast.LENGTH_SHORT).show()
                        }
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
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)
    val preloadPages by vm.prefs.preloadPages.collectAsState(initial = 5)

    LaunchedEffect(vm.currentPage) {
        val currentPageVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == vm.currentPage }
        if (!listState.isScrollInProgress && !currentPageVisible) {
            listState.scrollToItem(vm.currentPage)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { visibleItems ->
                visibleItems.maxByOrNull { item ->
                    val visibleTop = item.offset.coerceAtLeast(0)
                    val visibleBottom = (item.offset + item.size).coerceAtMost(listState.layoutInfo.viewportEndOffset)
                    visibleBottom - visibleTop
                }?.index
            }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null) vm.updatePage(index)
            }
    }

    LaunchedEffect(vm.currentPage, preloadPages, vm.pageUrls) {
        val from = (vm.currentPage - 1).coerceAtLeast(0)
        val to = (vm.currentPage + preloadPages).coerceAtMost(vm.pageUrls.lastIndex)
        if (from <= to) {
            for (index in from..to) {
                if (index != vm.currentPage) {
                    imageLoader.enqueue(readerPageRequest(context, vm.pageUrls[index]))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { vm.toggleControls() }
        }
    ) {
        itemsIndexed(vm.pageUrls, key = { _, url -> url }) { index, url ->
            Box(Modifier.fillMaxWidth().wrapContentHeight(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = readerPageRequest(context, url),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (val state = painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            CachedPageLoadingContent(
                                state = state,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                        AsyncImagePainter.State.Empty -> {
                            PageLoadingPlaceholder(modifier = Modifier.fillMaxWidth().height(400.dp))
                        }
                        is AsyncImagePainter.State.Error -> {
                            CachedPageErrorContent(
                                state = state,
                                modifier = Modifier.fillMaxWidth().height(400.dp),
                                onRetry = { vm.goToPage(index) }
                            )
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }
}
