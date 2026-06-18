package fail.tiger.komgarot.ui.reader

import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.remote.ImageDownloadProgressListener
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.SubcomposeAsyncImageScope
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.SuccessResult
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.ui.cover.writeTemporaryCoverImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
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
            originalSize = false,
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
    einkMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (state.painter != null) {
        SubcomposeAsyncImageContent()
    } else if (shouldShowReaderPageLoadingPlaceholder(isLocalCacheHit, hasPreviousPainter = false)) {
        PageLoadingPlaceholder(progressState = progressState, einkMode = einkMode, modifier = modifier)
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
    onSetBookCover: (String) -> Unit,
    onSetSeriesCover: (String) -> Unit,
    canEditMetadata: Boolean,
    vm: ReaderViewModel
) {
    LaunchedEffect(bookId) { vm.load(bookId, startPage, trackProgress) }
    LaunchedEffect(vm.currentBookId, vm.currentPage, vm.currentAiTranslatedPage(vm.currentPage)?.status) {
        while (vm.currentAiTranslatedPage(vm.currentPage)?.status == AiTranslationPageStatus.RUNNING) {
            vm.refreshAiTranslationState()
            delay(700)
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    val keepScreenOn by vm.prefs.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    val einkMode by vm.prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val aiTestModeEnabled by vm.prefs.aiTestModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    var aiTranslationErrorDialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.aiTranslationMessageNonce) {
        val messageRes = vm.aiTranslationMessageRes
        if (messageRes != 0) {
            val message = vm.aiTranslationMessageText.takeIf { it.isNotBlank() } ?: context.getString(messageRes)
            if (isAiTranslationErrorMessage(messageRes)) {
                aiTranslationErrorDialogMessage = message
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        if (einkMode) {
            PagerReader(vm, onOpenBook, onSetBookCover, onSetSeriesCover, canEditMetadata, aiTestModeEnabled)
        } else {
            when (vm.mode) {
                ReadingMode.PAGER -> PagerReader(vm, onOpenBook, onSetBookCover, onSetSeriesCover, canEditMetadata, aiTestModeEnabled)
                ReadingMode.SCROLL -> ScrollReader(vm)
            }
        }

        if (vm.pageUrls.isEmpty()) {
            ReaderStatusOverlay(
                loading = vm.loading,
                error = vm.error,
                einkMode = einkMode,
                onRetry = { vm.load(bookId, startPage, trackProgress) }
            )
        }

        if (einkMode) {
            if (vm.showControls) {
                ReaderTopControls(
                    title = vm.book?.displayTitle().orEmpty(),
                    onBack = onBack,
                    onToggleMode = { vm.toggleMode() },
                    showModeToggle = false,
                    containerAlpha = 0.88f,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars)
                )
            }
        } else {
            AnimatedVisibility(
                visible = vm.showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars)
            ) {
                ReaderTopControls(
                    title = vm.book?.displayTitle().orEmpty(),
                    onBack = onBack,
                    onToggleMode = { vm.toggleMode() },
                    showModeToggle = true,
                    containerAlpha = 0.6f
                )
            }
        }

        if (einkMode) {
            if (vm.showControls) {
                ReaderBottomControls(
                    vm = vm,
                    containerAlpha = 0.9f,
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)
                )
            }
        } else {
            AnimatedVisibility(
                visible = vm.showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)
            ) {
                ReaderBottomControls(vm = vm, containerAlpha = 0.8f)
            }
        }

        // 页面指示器独立于工具栏显示，并在工具栏显示时抬高。
        if (vm.pageUrls.isNotEmpty()) {
            val animatedIndicatorAlpha by animateFloatAsState(
                targetValue = if (vm.showControls) 0.85f else 0.45f,
                label = "page_indicator_alpha"
            )
            val indicatorAlpha = if (einkMode) {
                if (vm.showControls) 0.9f else 0.55f
            } else {
                animatedIndicatorAlpha
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = readerPageIndicatorBottomPadding(vm.showControls).dp)
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

        AiTranslationFloatingButton(
            mode = vm.currentAiTranslationDisplayMode,
            pageStatus = vm.currentAiTranslatedPage(vm.currentPage)?.status,
            onClick = { vm.cycleAiTranslationDisplayMode() },
            onLongClick = { vm.showAiTranslationPageActions = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(end = 16.dp, bottom = 72.dp)
        )
    }

    if (vm.showAiTranslationPageActions) {
        AlertDialog(
            onDismissRequest = { vm.showAiTranslationPageActions = false },
            title = { Text(stringResource(R.string.reader_ai_translation)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.retryCurrentAiTranslationPage()
                            vm.showAiTranslationPageActions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reader_ai_translate_current_page))
                    }
                    TextButton(
                        onClick = {
                            vm.retryCurrentAiTranslationPage()
                            vm.showAiTranslationPageActions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reader_ai_retry_current_page))
                    }
                    TextButton(
                        onClick = {
                            vm.deleteCurrentAiTranslationPage()
                            vm.showAiTranslationPageActions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reader_ai_delete_current_page))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.showAiTranslationPageActions = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    aiTranslationErrorDialogMessage?.let { message ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        AlertDialog(
            onDismissRequest = { aiTranslationErrorDialogMessage = null },
            title = { Text(stringResource(R.string.reader_ai_error_title)) },
            text = {
                SelectionContainer {
                    Text(message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("ai_translation_error", message))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { aiTranslationErrorDialogMessage = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

private fun isAiTranslationErrorMessage(messageRes: Int): Boolean =
    messageRes == R.string.ai_translate_config_required ||
        messageRes == R.string.reader_ai_test_failed ||
        messageRes == R.string.reader_ai_retry_failed

@Composable
private fun ReaderTopControls(
    title: String,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    showModeToggle: Boolean,
    containerAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(color = Color.Black.copy(alpha = containerAlpha), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showModeToggle) {
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.reader_switch_mode), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    vm: ReaderViewModel,
    containerAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(color = Color.Black.copy(alpha = containerAlpha), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (vm.pageUrls.isNotEmpty()) {
                val context = LocalContext.current
                val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
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
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.reader_previous), tint = Color.White)
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
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.reader_next), tint = Color.White)
                    }
                }
                Text(
                    text = stringResource(
                        R.string.reader_status_format,
                        vm.currentPage + 1,
                        vm.pageUrls.size,
                        preloadPages,
                        stringResource(if (currentPageCached) R.string.reader_cached else R.string.reader_network_loading) +
                            " · " +
                            stringResource(readerAiStatusStringRes(vm.currentAiTranslatedPage(vm.currentPage)?.status)) +
                            " · " +
                            stringResource(readerAiModeShortStringRes(vm.currentAiTranslationModeForPage(vm.currentPage)))
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ReaderStatusOverlay(
    loading: Boolean,
    error: String?,
    einkMode: Boolean,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading -> {
                if (einkMode) {
                    Text(stringResource(R.string.loading), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
                }
            }
            error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(error, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.reader_retry), color = Color.White)
                }
            }
            else -> Text(stringResource(R.string.reader_no_pages), color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerReader(
    vm: ReaderViewModel,
    onOpenBook: (BookDto, Boolean) -> Unit,
    onSetBookCover: (String) -> Unit,
    onSetSeriesCover: (String) -> Unit,
    canEditMetadata: Boolean,
    aiTestModeEnabled: Boolean
) {
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
    val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
    val readingDirection by vm.prefs.readingDirection.collectAsStateWithLifecycle(initialValue = "LTR")
    val pageFit by vm.prefs.pageFit.collectAsStateWithLifecycle(initialValue = "FIT")
    val einkMode by vm.prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val tapPageTurn by vm.prefs.tapPageTurn.collectAsStateWithLifecycle(initialValue = false)
    val imageLoader = coil.Coil.imageLoader(context)
    val pagerScope = rememberCoroutineScope()

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
                        cacheVersion = ThumbnailVersion.get(vm.currentBookId),
                        originalSize = false
                    )
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
        reverseLayout = readingDirection == "RTL",
        userScrollEnabled = !einkMode,
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
                    ZoomableReaderPageContent(
                        actualPageIndex = actualPageIndex,
                        pageRequestState = pageRequestState,
                        pageFit = pageFit,
                        zoomState = zoomState,
                        einkMode = einkMode,
                        aiTranslatedPage = vm.currentAiTranslatedPage(actualPageIndex),
                        aiDisplayMode = vm.aiTranslationDisplayModeForPage(actualPageIndex),
                        onRetry = { retryKey += 1 },
                        onTap = { tapX, width ->
                            when (
                                readerTapPageAction(
                                    tapX = tapX,
                                    width = width,
                                    tapPageTurnEnabled = tapPageTurn,
                                    einkMode = einkMode,
                                    readingDirection = readingDirection
                                )
                            ) {
                                ReaderTapPageAction.PreviousPage -> {
                                    pagerScope.launch {
                                        pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                    }
                                }
                                ReaderTapPageAction.NextPage -> {
                                    pagerScope.launch {
                                        pagerState.scrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerPages.lastIndex))
                                    }
                                }
                                ReaderTapPageAction.ToggleControls -> vm.toggleControls()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
            onSetBookCover = onSetBookCover,
            onSetSeriesCover = onSetSeriesCover,
            canEditMetadata = canEditMetadata,
            aiTestModeEnabled = aiTestModeEnabled,
            onDismiss = { longPressUrl = null },
        )
    }
}

@Composable
private fun ZoomableReaderPageContent(
    actualPageIndex: Int,
    pageRequestState: ReaderPageImageRequestState,
    pageFit: String,
    zoomState: ZoomState,
    einkMode: Boolean,
    aiTranslatedPage: AiTranslatedPage?,
    aiDisplayMode: AiTranslationDisplayMode,
    onRetry: () -> Unit,
    onTap: (tapX: Float, width: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageWidthPx by remember(pageRequestState.request) { mutableIntStateOf(0) }
    Box(
        modifier
            .onSizeChanged { pageWidthPx = it.width }
            .zoomable(
                zoomState,
                onTap = { position -> onTap(position.x, pageWidthPx.toFloat()) }
            )
    ) {
        SubcomposeAsyncImage(
            model = pageRequestState.request,
            contentDescription = stringResource(R.string.reader_page_description, actualPageIndex + 1),
            contentScale = if (pageFit == "WIDTH") ContentScale.FillWidth else ContentScale.Fit,
            modifier = Modifier.matchParentSize()
        ) {
            when (val state = painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    CachedPageLoadingContent(
                        state = state,
                        progressState = pageRequestState.progressState,
                        isLocalCacheHit = pageRequestState.isLocalCacheHit,
                        einkMode = einkMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AsyncImagePainter.State.Empty -> {
                    if (!pageRequestState.isLocalCacheHit) {
                        PageLoadingPlaceholder(
                            progressState = pageRequestState.progressState,
                            einkMode = einkMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    CachedPageErrorContent(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = onRetry
                    )
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
        AiTranslationOverlay(
            page = aiTranslatedPage,
            mode = aiDisplayMode,
            modifier = Modifier.matchParentSize(),
            fillWidth = pageFit == "WIDTH"
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
        opening && isNext -> stringResource(R.string.reader_boundary_opening_next)
        opening -> stringResource(R.string.reader_boundary_opening_previous)
        isNext -> stringResource(R.string.reader_boundary_finished)
        else -> stringResource(R.string.reader_boundary_first_page)
    }
    val message = when {
        target != null && isNext -> stringResource(R.string.reader_boundary_continue_next)
        target != null -> stringResource(R.string.reader_boundary_continue_previous)
        isNext -> stringResource(R.string.reader_boundary_last_book)
        else -> stringResource(R.string.reader_boundary_first_book)
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
    einkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (einkMode) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                if (progressState.hasPercent) {
                    LinearProgressIndicator(
                        progress = { progressState.progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.width(160.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.reader_loading_percent,
                            (progressState.progress * 100).roundToInt().coerceIn(0, 100)
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(stringResource(R.string.loading), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (progressState.hasPercent) {
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
        Text(stringResource(R.string.reader_image_load_failed), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.reader_retry), color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContextMenu(
    url: String,
    context: Context,
    vm: ReaderViewModel,
    onSetBookCover: (String) -> Unit,
    onSetSeriesCover: (String) -> Unit,
    canEditMetadata: Boolean,
    aiTestModeEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val imageLoader = coil.Coil.imageLoader(context)
    val operationFailed = stringResource(R.string.operation_failed_format)
    val savedToGallery = stringResource(R.string.reader_action_saved_to_gallery)
    val shareTitle = stringResource(R.string.reader_share_title)

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

    fun doAction(pageUrl: String, action: suspend (Bitmap) -> Unit) {
        onDismiss()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val bitmap = loadBitmap(pageUrl) ?: return@launch
                action(bitmap)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, operationFailed.format(e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            if (aiTestModeEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_ai_test_current_page)) },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onDismiss()
                        vm.testCurrentAiTranslationPage()
                    }
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_action_save)) },
                leadingContent = { Icon(Icons.Default.Download, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val pageUrl = url
                    doAction(pageUrl) { bitmap ->
                        saveBitmapToGallery(context, bitmap)
                        withContext(Dispatchers.Main) { Toast.makeText(context, savedToGallery, Toast.LENGTH_SHORT).show() }
                    }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_action_share)) },
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
                            context.startActivity(Intent.createChooser(intent, shareTitle))
                        }
                    }
                }
            )
            if (canEditMetadata) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_action_set_book_cover)) },
                    leadingContent = { Icon(Icons.Default.Book, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        val pageUrl = url
                        doAction(pageUrl) { bitmap ->
                            val uri = writeTemporaryCoverImage(context, bitmap)
                            withContext(Dispatchers.Main) { onSetBookCover(uri.toString()) }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_action_set_series_cover)) },
                    leadingContent = { Icon(Icons.Default.Collections, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        val pageUrl = url
                        doAction(pageUrl) { bitmap ->
                            val uri = writeTemporaryCoverImage(context, bitmap)
                            withContext(Dispatchers.Main) { onSetSeriesCover(uri.toString()) }
                        }
                    }
                )
            }
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
    val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
    val einkMode by vm.prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)

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
                            cacheVersion = ThumbnailVersion.get(vm.currentBookId),
                            originalSize = false
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
                    contentDescription = stringResource(R.string.reader_page_description, index + 1),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (val state = painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            CachedPageLoadingContent(
                                state = state,
                                progressState = pageRequestState.progressState,
                                isLocalCacheHit = pageRequestState.isLocalCacheHit,
                                einkMode = einkMode,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                        AsyncImagePainter.State.Empty -> {
                            if (!pageRequestState.isLocalCacheHit) {
                                PageLoadingPlaceholder(
                                    progressState = pageRequestState.progressState,
                                    einkMode = einkMode,
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
                AiTranslationOverlay(
                    page = vm.currentAiTranslatedPage(index),
                    mode = vm.aiTranslationDisplayModeForPage(index),
                    modifier = Modifier.matchParentSize(),
                    fillWidth = true
                )
            }
        }
    }
}
