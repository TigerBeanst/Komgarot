package fail.tiger.komgarot.ui.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
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
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
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
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.remote.ImageDownloadProgressListener
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.SubcomposeAsyncImageScope
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.SuccessResult
import fail.tiger.komgarot.data.remote.dto.BookDto
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
import kotlin.math.roundToInt

private class ReaderPageProgressState {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = true

    var progress by mutableFloatStateOf(0f)
        private set
    var hasPercent by mutableStateOf(false)
        private set

    val listener = ImageDownloadProgressListener { bytesRead, contentLength ->
        val totalKnown = contentLength > 0L
        val nextProgress = if (totalKnown) {
            (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        mainHandler.post {
            if (active) {
                hasPercent = totalKnown
                progress = nextProgress
            }
        }
    }

    fun reset() {
        hasPercent = false
        progress = 0f
    }

    fun complete() {
        hasPercent = true
        progress = 1f
    }

    fun dispose() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
    }
}

@Composable
private fun rememberReaderPageRequest(
    url: String,
    seriesId: String,
    bookId: String,
    allowHardware: Boolean = false,
    retryKey: Int = 0
): ReaderPageImageRequestState {
    val context = LocalContext.current
    val progressState = remember(url) { ReaderPageProgressState() }
    val cacheVersion = ThumbnailVersion.get(bookId)
    val isLocalCacheHit = remember(context, seriesId, bookId, url, retryKey) {
        retryKey == 0 && ReaderPageCache.hasCachedFile(context, seriesId, bookId, url)
    }
    DisposableEffect(progressState) {
        onDispose { progressState.dispose() }
    }

    val request = remember(context, url, allowHardware, retryKey, progressState, cacheVersion) {
        readerPageRequest(
            context = context,
            url = url,
            seriesId = seriesId,
            bookId = bookId,
            cacheVersion = cacheVersion,
            allowHardware = allowHardware,
            retryKey = retryKey,
            progressListener = progressState.listener,
            listener = object : ImageRequest.Listener {
                override fun onStart(request: ImageRequest) {
                    progressState.reset()
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    progressState.complete()
                }

                override fun onError(request: ImageRequest, result: coil.request.ErrorResult) {
                    progressState.reset()
                }
            }
        )
    }
    return ReaderPageImageRequestState(
        request = request,
        progressState = progressState,
        isLocalCacheHit = isLocalCacheHit
    )
}

private data class ReaderPageImageRequestState(
    val request: ImageRequest,
    val progressState: ReaderPageProgressState,
    val isLocalCacheHit: Boolean
)

@Composable
private fun SubcomposeAsyncImageScope.CachedPageLoadingContent(
    state: AsyncImagePainter.State.Loading,
    progressState: ReaderPageProgressState,
    isLocalCacheHit: Boolean,
    modifier: Modifier = Modifier
) {
    if (state.painter != null) {
        SubcomposeAsyncImageContent()
    } else if (shouldShowReaderPageLoadingPlaceholder(isLocalCacheHit, hasPreviousPainter = false)) {
        PageLoadingPlaceholder(progressState = progressState, modifier = modifier)
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

private fun BookDto.displayTitle(): String = metadata.title.ifEmpty { name }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    startPage: Int,
    trackProgress: Boolean = true,
    onBack: () -> Unit,
    onOpenBook: (BookDto, Boolean) -> Unit,
    vm: ReaderViewModel
) {
    LaunchedEffect(bookId) { vm.load(bookId, startPage, trackProgress) }

    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    val keepScreenOn by vm.prefs.keepScreenOn.collectAsState(initial = true)

    DisposableEffect(keepScreenOn) {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            if (keepScreenOn) it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            vm.flushProgress()
            window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(it, false)
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
            ReadingMode.PAGER -> PagerReader(vm, onOpenBook)
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
                    Text(
                        text = vm.book?.displayTitle() ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.toggleMode() }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换阅读模式（翻页/滚动）", tint = Color.White)
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
                        val context = LocalContext.current
                        val preloadPages by vm.prefs.preloadPages.collectAsState(initial = 5)
                        val currentPageUrl = vm.pageUrls.getOrNull(vm.currentPage)
                        val currentPageCached = currentPageUrl != null &&
                            ReaderPageCache.hasCachedFile(context, vm.currentSeriesId, vm.currentBookId, currentPageUrl)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (vm.currentPage > 0) vm.goToPage(vm.currentPage - 1) },
                                enabled = vm.currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous", tint = Color.White)
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
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next", tint = Color.White)
                            }
                        }
                        Text(
                            text = "${vm.currentPage + 1} / ${vm.pageUrls.size} · 预加载 $preloadPages 页 · ${if (currentPageCached) "已缓存" else "联网加载"}",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
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
fun PagerReader(vm: ReaderViewModel, onOpenBook: (BookDto, Boolean) -> Unit) {
    if (vm.pageUrls.isEmpty()) return
    val pagerPages = remember(vm.pageUrls, vm.previousBook, vm.nextBook) {
        buildReaderPagerPages(vm.pageUrls.size, vm.previousBook, vm.nextBook)
    }
    val initialPage = remember(vm.currentBookId, pagerPages) {
        pagerPages.pagerIndexForActualPage(vm.currentPage)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pagerPages.size }
    val context = LocalContext.current
    var longPressUrl by remember { mutableStateOf<String?>(null) }
    var openedBoundaryBookId by remember(vm.currentBookId) { mutableStateOf<String?>(null) }
    val preloadPages by vm.prefs.preloadPages.collectAsState(initial = 5)
    val readingDirection by vm.prefs.readingDirection.collectAsState(initial = "LTR")
    val pageFit by vm.prefs.pageFit.collectAsState(initial = "FIT")
    val imageLoader = coil.Coil.imageLoader(context)

    LaunchedEffect(pagerState.currentPage, pagerPages) {
        when (val page = pagerPages.getOrNull(pagerState.currentPage)) {
            is ReaderPagerPage.Actual -> vm.updatePage(page.pageIndex)
            is ReaderPagerPage.Trigger -> {
                if (openedBoundaryBookId != page.target.id) {
                    openedBoundaryBookId = page.target.id
                    onOpenBook(page.target, vm.trackProgress)
                }
            }
            else -> Unit
        }
    }
    LaunchedEffect(vm.currentPage) {
        val currentPagerPage = pagerPages.getOrNull(pagerState.currentPage)
        val targetPage = pagerPages.pagerIndexForActualPage(vm.currentPage)
        if (currentPagerPage is ReaderPagerPage.Actual && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerPages, preloadPages, vm.pageUrls) {
        readerPagerActualPreloadRange(
            pagerPages = pagerPages,
            currentPagerIndex = pagerState.currentPage,
            preloadPages = preloadPages
        ).forEach { pageIndex ->
            val pageUrl = vm.pageUrls.getOrNull(pageIndex)
            if (pageUrl != null) {
                imageLoader.enqueue(
                    readerPageRequest(
                        context = context,
                        url = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        cacheVersion = ThumbnailVersion.get(vm.currentBookId)
                    )
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
        reverseLayout = readingDirection == "RTL",
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (val readerPage = pagerPages[page]) {
            is ReaderPagerPage.Actual -> {
                val actualPageIndex = readerPage.pageIndex
                val zoomState = rememberZoomState(maxScale = 5f)
                LaunchedEffect(actualPageIndex) { zoomState.reset() }
                val pageUrl = vm.pageUrls[actualPageIndex]
                var retryKey by remember(pageUrl) { mutableIntStateOf(0) }

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(actualPageIndex) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val lp = awaitLongPressOrCancellation(down.id)
                                if (lp != null) longPressUrl = pageUrl
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val pageRequestState = rememberReaderPageRequest(
                        url = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        retryKey = retryKey
                    )
                    SubcomposeAsyncImage(
                        model = pageRequestState.request,
                        contentDescription = "Page ${actualPageIndex + 1}",
                        contentScale = if (pageFit == "WIDTH") ContentScale.FillWidth else ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomable(zoomState, onTap = { vm.toggleControls() })
                    ) {
                        when (val state = painter.state) {
                            is AsyncImagePainter.State.Loading -> {
                                CachedPageLoadingContent(
                                    state = state,
                                    progressState = pageRequestState.progressState,
                                    isLocalCacheHit = pageRequestState.isLocalCacheHit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AsyncImagePainter.State.Empty -> {
                                if (!pageRequestState.isLocalCacheHit) {
                                    PageLoadingPlaceholder(
                                        progressState = pageRequestState.progressState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                CachedPageErrorContent(
                                    state = state,
                                    modifier = Modifier.fillMaxSize(),
                                    onRetry = { retryKey += 1 }
                                )
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
            is ReaderPagerPage.Boundary -> {
                ReaderBoundaryPage(
                    direction = readerPage.direction,
                    target = readerPage.target,
                    opening = false,
                    onTap = { vm.toggleControls() }
                )
            }
            is ReaderPagerPage.Trigger -> {
                ReaderBoundaryPage(
                    direction = readerPage.direction,
                    target = readerPage.target,
                    opening = true,
                    onTap = { vm.toggleControls() }
                )
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
private fun ReaderBoundaryPage(
    direction: ReaderBoundaryDirection,
    target: BookDto?,
    opening: Boolean,
    onTap: () -> Unit
) {
    val isNext = direction == ReaderBoundaryDirection.NEXT
    val title = when {
        opening && isNext -> "正在打开下一本"
        opening -> "正在打开上一本"
        isNext -> "本书已看完"
        else -> "已到达第一页"
    }
    val message = when {
        target != null && isNext -> "继续翻下一页打开"
        target != null -> "继续往前翻打开"
        isNext -> "已到最后一本书"
        else -> "已到第一本书"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onTap)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(message, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
            target?.let {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = it.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageLoadingPlaceholder(
    progressState: ReaderPageProgressState,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (progressState.hasPercent) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressState.progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${(progressState.progress * 100).roundToInt().coerceIn(0, 100)}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
        }
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
        val req = readerPageRequest(
            context = context,
            url = pageUrl,
            seriesId = vm.currentSeriesId,
            bookId = vm.currentBookId,
            cacheVersion = ThumbnailVersion.get(vm.currentBookId),
            allowHardware = false,
            originalSize = true
        )
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
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
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
                    imageLoader.enqueue(
                        readerPageRequest(
                            context = context,
                            url = vm.pageUrls[index],
                            seriesId = vm.currentSeriesId,
                            bookId = vm.currentBookId,
                            cacheVersion = ThumbnailVersion.get(vm.currentBookId)
                        )
                    )
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
                var retryKey by remember(url) { mutableIntStateOf(0) }
                val pageRequestState = rememberReaderPageRequest(
                    url = url,
                    seriesId = vm.currentSeriesId,
                    bookId = vm.currentBookId,
                    retryKey = retryKey
                )
                SubcomposeAsyncImage(
                    model = pageRequestState.request,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (val state = painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            CachedPageLoadingContent(
                                state = state,
                                progressState = pageRequestState.progressState,
                                isLocalCacheHit = pageRequestState.isLocalCacheHit,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                        AsyncImagePainter.State.Empty -> {
                            if (!pageRequestState.isLocalCacheHit) {
                                PageLoadingPlaceholder(
                                    progressState = pageRequestState.progressState,
                                    modifier = Modifier.fillMaxWidth().height(400.dp)
                                )
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            CachedPageErrorContent(
                                state = state,
                                modifier = Modifier.fillMaxWidth().height(400.dp),
                                onRetry = { retryKey += 1 }
                            )
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }
}
