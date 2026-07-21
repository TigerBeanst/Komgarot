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
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    originalSize: Boolean,
    displayWidthPx: Int = 0,
    displayHeightPx: Int = 0,
    displayQualityScale: Float = 1f,
    displayMaxDecodedBytes: Long = readerRenderMemoryBudget().currentPreviewBytes,
    allowHardware: Boolean = false,
    retainInMemory: Boolean = false,
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

    val request = remember(
        context,
        url,
        originalSize,
        displayWidthPx,
        displayHeightPx,
        displayQualityScale,
        displayMaxDecodedBytes,
        allowHardware,
        retainInMemory,
        retryKey,
        progressState,
        cacheVersion
    ) {
        readerPageRequest(
            context = context,
            url = url,
            seriesId = seriesId,
            bookId = bookId,
            cacheVersion = cacheVersion,
            allowHardware = allowHardware,
            originalSize = originalSize,
            displayWidthPx = displayWidthPx,
            displayHeightPx = displayHeightPx,
            displayQualityScale = displayQualityScale,
            displayMaxDecodedBytes = displayMaxDecodedBytes,
            retainInMemory = retainInMemory,
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

internal fun readerRetainPainterForTransientState(
    state: AsyncImagePainter.State,
    retainedPainter: Painter?
): AsyncImagePainter.State = when (state) {
    is AsyncImagePainter.State.Loading -> {
        if (state.painter == null && retainedPainter != null) state.copy(painter = retainedPainter) else state
    }
    is AsyncImagePainter.State.Error -> {
        if (state.painter == null && retainedPainter != null) state.copy(painter = retainedPainter) else state
    }
    else -> state
}

internal fun readerFallbackPainterForTransientState(
    state: AsyncImagePainter.State,
    retainedPainter: Painter?
): Painter? = when (state) {
    AsyncImagePainter.State.Empty -> retainedPainter
    is AsyncImagePainter.State.Loading -> retainedPainter.takeIf { state.painter == null }
    is AsyncImagePainter.State.Error -> retainedPainter.takeIf { state.painter == null }
    else -> null
}

private fun MutableMap<String, Painter>.trimReaderPagePainters(
    pageUrls: List<String>,
    currentPage: Int,
    preloadPages: Int
) {
    if (pageUrls.isEmpty()) {
        clear()
        return
    }
    val from = (currentPage - 1).coerceAtLeast(0)
    val to = (currentPage + preloadPages.coerceAtLeast(1)).coerceAtMost(pageUrls.lastIndex)
    val retainedUrls = pageUrls.subList(from, to + 1).toSet()
    keys.toList().forEach { url ->
        if (url !in retainedUrls) remove(url)
    }
}

@Composable
private fun rememberReaderPagePainterTransform(
    request: ImageRequest
): (AsyncImagePainter.State) -> AsyncImagePainter.State {
    var retainedPainter by remember(request) { mutableStateOf<Painter?>(null) }
    return remember(request) {
        { state ->
            if (state is AsyncImagePainter.State.Success) {
                retainedPainter = state.painter
            }
            readerRetainPainterForTransientState(state, retainedPainter)
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
    aiTranslationAvailable: Boolean,
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
    val aiTranslationEnabled by vm.prefs.aiTranslationEnabled.collectAsStateWithLifecycle(initialValue = false)
    val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
    val memoryAwarePreloadPages = readerMemoryAwarePreloadPages(preloadPages)
    val aiVerticalGlyphSpacingPercent by vm.prefs.aiVerticalGlyphSpacingPercent.collectAsStateWithLifecycle(initialValue = 86)
    val aiVerticalGlyphSpacingMultiplier = aiVerticalGlyphSpacingMultiplier(aiVerticalGlyphSpacingPercent)
    var aiTranslationErrorDialogMessage by remember { mutableStateOf<String?>(null) }
    var readerAiTimingDialog by remember { mutableStateOf<String?>(null) }
    var readerAiFailureDialog by remember { mutableStateOf<String?>(null) }
    var readerAiDeleteFirstConfirmation by remember { mutableStateOf(false) }
    var readerAiDeleteFinalConfirmation by remember { mutableStateOf(false) }
    val aiTranslationMessageFallback = if (vm.aiTranslationMessageRes != 0) {
        stringResource(vm.aiTranslationMessageRes)
    } else {
        ""
    }

    LaunchedEffect(
        vm.currentBookId,
        vm.currentPage,
        vm.currentAiTranslationDisplayMode,
        vm.currentAiTranslatedPage(vm.currentPage)?.status,
        memoryAwarePreloadPages,
        aiTranslationAvailable,
        aiTranslationEnabled
    ) {
        if (aiTranslationAvailable && aiTranslationEnabled) {
            vm.translateCurrentAiPageIfDisplayEnabled(memoryAwarePreloadPages)
        }
    }

    LaunchedEffect(vm.aiTranslationMessageNonce, aiTranslationMessageFallback) {
        val messageRes = vm.aiTranslationMessageRes
        if (messageRes != 0) {
            val message = vm.aiTranslationMessageText.takeIf { it.isNotBlank() } ?: aiTranslationMessageFallback
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
        val aiTranslationControlsVisible = aiTranslationAvailable && aiTranslationEnabled
        if (einkMode) {
            PagerReader(
                vm,
                onOpenBook,
                onSetBookCover,
                onSetSeriesCover,
                canEditMetadata,
                aiTranslationAvailable = aiTranslationControlsVisible,
                verticalGlyphSpacingMultiplier = aiVerticalGlyphSpacingMultiplier
            )
        } else {
            when (vm.mode) {
                ReadingMode.PAGER -> PagerReader(
                    vm,
                    onOpenBook,
                    onSetBookCover,
                    onSetSeriesCover,
                    canEditMetadata,
                    aiTranslationAvailable = aiTranslationControlsVisible,
                    verticalGlyphSpacingMultiplier = aiVerticalGlyphSpacingMultiplier
                )
                ReadingMode.SCROLL -> ScrollReader(
                    vm,
                    onSetBookCover = onSetBookCover,
                    onSetSeriesCover = onSetSeriesCover,
                    canEditMetadata = canEditMetadata,
                    aiTranslationAvailable = aiTranslationControlsVisible,
                    verticalGlyphSpacingMultiplier = aiVerticalGlyphSpacingMultiplier
                )
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
                    aiTranslationAvailable = aiTranslationControlsVisible,
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
                ReaderBottomControls(vm = vm, aiTranslationAvailable = aiTranslationControlsVisible, containerAlpha = 0.8f)
            }
        }

        if (vm.pageUrls.isNotEmpty() && !vm.showControls) {
            val indicatorAlpha = if (einkMode) 0.55f else 0.45f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = readerPageIndicatorBottomPadding(showControls = false).dp)
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

    if (aiTranslationAvailable && aiTranslationEnabled && vm.showAiTranslationPageActions) {
        val currentAiFailureDetails = readerAiFailureDiagnostic(vm.currentAiTranslatedPage(vm.currentPage))
        val currentAiTimingText = readerAiTimingDiagnostic(vm.currentAiTranslationTiming())
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
                        Text(stringResource(R.string.reader_ai_retry_current_page))
                    }
                    TextButton(
                        onClick = {
                            readerAiTimingDialog = currentAiTimingText
                            vm.showAiTranslationPageActions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reader_ai_page_timing))
                    }
                    TextButton(
                        onClick = {
                            readerAiFailureDialog = currentAiFailureDetails
                            vm.showAiTranslationPageActions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reader_ai_failure_reason))
                    }
                    TextButton(
                        onClick = {
                            vm.showAiTranslationPageActions = false
                            readerAiDeleteFirstConfirmation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ai_translate_delete_book_translation))
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

    if (aiTranslationAvailable && aiTranslationEnabled) readerAiFailureDialog?.let { failureText ->
        AlertDialog(
            onDismissRequest = { readerAiFailureDialog = null },
            title = { Text(stringResource(R.string.reader_ai_failure_reason_title)) },
            text = {
                SelectionContainer {
                    Text(failureText)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { readerAiFailureDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (aiTranslationAvailable && aiTranslationEnabled) readerAiTimingDialog?.let { timingText ->
        AlertDialog(
            onDismissRequest = { readerAiTimingDialog = null },
            title = { Text(stringResource(R.string.reader_ai_page_timing_title)) },
            text = {
                SelectionContainer {
                    Text(timingText)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { readerAiTimingDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (aiTranslationAvailable && aiTranslationEnabled && vm.showAiLocalModelRequiredDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissAiLocalModelRequiredDialog,
            title = { Text(stringResource(R.string.reader_ai_local_model_required_title)) },
            text = { Text(stringResource(R.string.reader_ai_local_model_required_message)) },
            confirmButton = {
                TextButton(
                    enabled = !vm.aiLocalModelDownloading,
                    onClick = { vm.downloadRequiredAiLocalModel() }
                ) {
                    if (vm.aiLocalModelDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.reader_ai_local_model_download))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !vm.aiLocalModelDownloading,
                    onClick = vm::dismissAiLocalModelRequiredDialog
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (readerAiDeleteFirstConfirmation) {
        AlertDialog(
            onDismissRequest = { readerAiDeleteFirstConfirmation = false },
            title = { Text(stringResource(R.string.ai_translate_delete_title)) },
            text = { Text(stringResource(R.string.ai_translate_delete_message_first)) },
            confirmButton = {
                TextButton(onClick = {
                    readerAiDeleteFirstConfirmation = false
                    readerAiDeleteFinalConfirmation = true
                }) { Text(stringResource(R.string.ai_translate_delete_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { readerAiDeleteFirstConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (readerAiDeleteFinalConfirmation) {
        AlertDialog(
            onDismissRequest = { readerAiDeleteFinalConfirmation = false },
            title = { Text(stringResource(R.string.ai_translate_delete_title_final)) },
            text = { Text(stringResource(R.string.ai_translate_delete_message_final)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearCurrentBookAiTranslation()
                    readerAiDeleteFinalConfirmation = false
                }) { Text(stringResource(R.string.ai_translate_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { readerAiDeleteFinalConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (aiTranslationAvailable) aiTranslationErrorDialogMessage?.let { message ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val copiedMessage = stringResource(R.string.copied)
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
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
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
        messageRes == R.string.reader_ai_test_failed

@Composable
private fun readerAiTimingStepLabel(key: String): String = when (key) {
    "page_image_cache" -> stringResource(R.string.reader_ai_page_timing_step_page_image_cache)
    "local_detection_cache" -> stringResource(R.string.reader_ai_page_timing_step_local_detection_cache)
    "paddle_ocr" -> stringResource(R.string.reader_ai_page_timing_step_paddle_ocr)
    "heuristic_fallback" -> stringResource(R.string.reader_ai_page_timing_step_heuristic_fallback)
    "page_image_input" -> stringResource(R.string.reader_ai_page_timing_step_page_image_input)
    "region_crop_images" -> stringResource(R.string.reader_ai_page_timing_step_region_crop_images)
    "ai_request_batch" -> stringResource(R.string.reader_ai_page_timing_step_ai_request_batch)
    "ai_request" -> stringResource(R.string.reader_ai_page_timing_step_ai_request)
    "ai_response_parse" -> stringResource(R.string.reader_ai_page_timing_step_ai_response_parse)
    "save_and_verify" -> stringResource(R.string.reader_ai_page_timing_step_save_and_verify)
    else -> key
}

@Composable
private fun readerAiTimingDiagnostic(timing: fail.tiger.komgarot.data.repository.AiTranslationPageTiming?): String {
    if (timing == null) return stringResource(R.string.reader_ai_page_timing_empty)
    val lines = mutableListOf(stringResource(R.string.reader_ai_page_timing_total, timing.totalMs))
    for (step in timing.steps) {
        lines += stringResource(
            R.string.reader_ai_page_timing_step,
            readerAiTimingStepLabel(step.label),
            step.durationMs
        )
    }
    timing.localDetectionStats?.let { stats ->
        lines += stringResource(
            R.string.reader_ai_page_timing_local_session,
            if (stats.sessionWasReused) {
                stringResource(R.string.reader_ai_page_timing_session_hot)
            } else {
                stringResource(R.string.reader_ai_page_timing_session_cold)
            },
            stats.executionProvider,
            stats.sessionAcquireMs
        )
        lines += stringResource(
            R.string.reader_ai_page_timing_local_inference,
            stats.preprocessMs,
            stats.inferenceMs,
            stats.postProcessMs,
            stats.estimatedPeakWorkingSetBytes / (1024L * 1024L)
        )
    }
    val requestStats = timing.requestStats
    lines += stringResource(
        R.string.reader_ai_page_timing_requests,
        requestStats.regionCount,
        requestStats.requestCount,
        requestStats.retryCount
    )
    lines += stringResource(
        R.string.reader_ai_page_timing_concurrency,
        requestStats.configuredConcurrency,
        requestStats.initialConcurrency,
        requestStats.peakConcurrency,
        requestStats.concurrencyDownshiftCount,
        stringResource(
            if (requestStats.crossPageConcurrencyStarted) {
                R.string.reader_ai_page_timing_cross_page_active
            } else {
                R.string.reader_ai_page_timing_cross_page_waiting
            }
        )
    )
    requestStats.firstRegionVisibleMs?.let { firstRegionVisibleMs ->
        lines += stringResource(
            R.string.reader_ai_page_timing_first_region,
            firstRegionVisibleMs,
            requestStats.pageCompletedMs
        )
    }
    lines += stringResource(
        R.string.reader_ai_page_timing_context,
        requestStats.pageContextStrategy.ifBlank { "-" },
        requestStats.pageContextBytes / 1024L
    )
    val usage = requestStats.usage
    lines += if (usage.totalTokens > 0L) {
        stringResource(
            R.string.reader_ai_page_timing_usage,
            usage.promptTokens,
            usage.completionTokens,
            usage.totalTokens
        )
    } else {
        stringResource(
            R.string.reader_ai_page_timing_usage_fallback,
            1,
            requestStats.regionCount,
            requestStats.requestCount
        )
    }
    return lines.joinToString("\n")
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderBottomControls(
    vm: ReaderViewModel,
    aiTranslationAvailable: Boolean,
    containerAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(color = Color.Black.copy(alpha = containerAlpha), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (vm.pageUrls.isNotEmpty()) {
                val scope = rememberCoroutineScope()
                val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
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
                    Text(
                        text = "${vm.currentPage + 1}/${vm.pageUrls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(
                        onClick = { if (vm.currentPage < vm.pageUrls.size - 1) vm.goToPage(vm.currentPage + 1) },
                        enabled = vm.currentPage < vm.pageUrls.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.reader_next), tint = Color.White)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {
                            scope.launch { vm.prefs.setPreloadPages(readerNextQuickPreloadPages(preloadPages)) }
                        },
                        colors = readerQuickAssistChipColors(),
                        label = {
                            ReaderQuickChipLabel(stringResource(R.string.reader_quick_preload, preloadPages))
                        }
                    )
                    ReaderAiTranslationProgressControl(
                        vm = vm,
                        aiTranslationAvailable = aiTranslationAvailable,
                        preloadPages = readerMemoryAwarePreloadPages(preloadPages),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderAiTranslationProgressControl(
    vm: ReaderViewModel,
    aiTranslationAvailable: Boolean,
    preloadPages: Int,
    modifier: Modifier = Modifier
) {
    val page = vm.currentAiTranslatedPage(vm.currentPage)
    val windowStatus = vm.currentAiTranslationWindowStatus(preloadPages)
    val activePageNumbers = listOfNotNull(vm.aiTranslationPriorityPageIndex.takeIf { it >= 0 }?.plus(1))
    val pageRunning = page?.status == AiTranslationPageStatus.RUNNING
    val running = pageRunning || windowStatus.runningPages > 0
    val failed = page?.status == AiTranslationPageStatus.FAILED
    val active = vm.currentAiTranslationDisplayMode == AiTranslationDisplayMode.ON
    val statusText = readerAiTranslationProgressText(page)?.let { progress ->
        stringResource(R.string.reader_ai_page_region_progress_short, vm.currentPage + 1, progress)
    }
        ?: if (windowStatus.runningPages > 0 && windowStatus.totalPages > 0) {
            if (activePageNumbers.isNotEmpty()) {
                stringResource(
                    R.string.reader_ai_window_active_progress_short,
                    windowStatus.completedPages,
                    windowStatus.totalPages,
                    activePageNumbers.joinToString(", ")
                )
            } else {
                stringResource(
                    R.string.reader_ai_window_progress_short,
                    windowStatus.completedPages,
                    windowStatus.totalPages
                )
            }
        } else null
        ?: stringResource(readerAiStatusStringRes(page?.status))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            failed -> MaterialTheme.colorScheme.errorContainer
            active || running -> MaterialTheme.colorScheme.primaryContainer
            else -> ReaderQuickChipContainerColor
        },
        contentColor = when {
            failed -> MaterialTheme.colorScheme.onErrorContainer
            active || running -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> ReaderQuickChipTextColor
        },
        border = if (active) {
            null
        } else {
            BorderStroke(
                width = 1.dp,
                color = when {
                    failed -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f)
                    running -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    else -> ReaderQuickChipTextColor.copy(alpha = 0.78f)
                }
            )
        },
        modifier = modifier
            .heightIn(min = 40.dp)
            .combinedClickable(
                enabled = aiTranslationAvailable,
                onClick = { vm.handleAiTranslationButtonClick(preloadPages) },
                onLongClick = { vm.showAiTranslationPageActions = true }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = stringResource(R.string.reader_ai_translation),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.reader_ai_translation) + " · " + statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ReaderQuickChipLabel(text: String) {
    Text(text, color = ReaderQuickChipTextColor)
}

@Composable
private fun readerQuickAssistChipColors() = AssistChipDefaults.assistChipColors(
    containerColor = ReaderQuickChipContainerColor,
    labelColor = ReaderQuickChipTextColor
)

private val ReaderQuickChipContainerColor = Color.Black.copy(alpha = 0.58f)
private val ReaderQuickChipTextColor = Color.White

@Composable
private fun readerAiFailureDiagnostic(page: AiTranslatedPage?): String {
    val summary = page?.errorSummary?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.reader_ai_failure_reason_empty)
    val httpStatusLine = page?.errorHttpStatus?.let { status ->
        stringResource(R.string.reader_ai_failure_http_status, status)
    }
    val retryAfterLine = page?.retryAfterMs?.let { retryAfterMs ->
        stringResource(R.string.reader_ai_failure_retry_after, (retryAfterMs + 999L) / 1000L)
    }
    return buildString {
        append(summary)
        httpStatusLine?.let { line ->
            append('\n')
            append(line)
        }
        retryAfterLine?.let { line ->
            append('\n')
            append(line)
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
    aiTranslationAvailable: Boolean,
    verticalGlyphSpacingMultiplier: Float
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
    val pagerScope = rememberCoroutineScope()
    val memoryAwarePreloadPages = readerMemoryAwarePreloadPages(preloadPages)
    val retainedPagePainters = remember(vm.currentBookId) { mutableStateMapOf<String, Painter>() }
    val pagerRenderMemoryBudget = remember { readerRenderMemoryBudget() }
    val hasTiledPages = vm.pageUrls.indices.any { pageIndex ->
        readerPageNeedsStableAdjacentComposition(vm.pageInfo(pageIndex))
    }
    var preloadDirection by remember(vm.currentBookId) { mutableIntStateOf(1) }
    var lastActualPage by remember(vm.currentBookId) { mutableIntStateOf(vm.currentPage) }

    LaunchedEffect(pagerState.currentPage, pagerPages) {
        when (val page = pagerPages.getOrNull(pagerState.currentPage)) {
            is ReaderPagerPage.Actual -> {
                val pageDelta = page.pageIndex - lastActualPage
                if (pageDelta != 0) preloadDirection = if (pageDelta < 0) -1 else 1
                lastActualPage = page.pageIndex
                vm.updatePage(page.pageIndex)
            }
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
        val targetPage = pagerPages.pagerIndexForActualPage(vm.currentPage)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(vm.currentPage, memoryAwarePreloadPages, vm.pageUrls) {
        retainedPagePainters.trimReaderPagePainters(vm.pageUrls, vm.currentPage, memoryAwarePreloadPages)
    }
    val targetPreloadDirection = readerPagerPreloadDirection(
        pagerPages = pagerPages,
        currentPagerIndex = pagerState.currentPage,
        targetPagerIndex = pagerState.targetPage,
        fallbackDirection = preloadDirection
    )
    LaunchedEffect(pagerState.targetPage, pagerPages, memoryAwarePreloadPages, targetPreloadDirection, vm.pageUrls) {
        val preloadIndexes = readerPagerActualPreloadRange(
            pagerPages = pagerPages,
            currentPagerIndex = pagerState.targetPage,
            preloadPages = memoryAwarePreloadPages,
            direction = targetPreloadDirection
        )
        coroutineScope {
            preloadIndexes.mapNotNull { pageIndex ->
                vm.pageUrls.getOrNull(pageIndex)?.let { pageIndex to it }
            }.map { (_, pageUrl) ->
                async {
                    ensureReaderPageFileCached(
                        context = context,
                        url = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        priority = ReaderPageLoadPriority.PREFETCH
                    )
                }
            }.awaitAll()
        }
    }
    DisposableEffect(einkMode, readingDirection, pagerPages, pagerState) {
        if (!einkMode) {
            onDispose {}
        } else {
            ReaderPhysicalKeyDispatcher.setHandler { event ->
                val keyAction = readerPhysicalKeyAction(
                    keyCode = event.keyCode,
                    einkMode = true,
                    readingDirection = readingDirection
                ) ?: return@setHandler false
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        when (keyAction) {
                            ReaderPhysicalKeyAction.PreviousPage -> {
                                pagerScope.launch {
                                    pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                            }
                            ReaderPhysicalKeyAction.NextPage -> {
                                pagerScope.launch {
                                    pagerState.scrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerPages.lastIndex))
                                }
                            }
                            ReaderPhysicalKeyAction.ScrollBackward,
                            ReaderPhysicalKeyAction.ScrollForward -> Unit
                        }
                        true
                    }
                    KeyEvent.ACTION_UP -> true
                    else -> false
                }
            }
            onDispose { ReaderPhysicalKeyDispatcher.setHandler(null) }
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = readerPagerBeyondViewportPageCount(
            einkMode = einkMode,
            hasTiledPages = hasTiledPages
        ),
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
                val pageInfo = vm.pageInfo(actualPageIndex)
                val renderMode = if (pageInfo != null) readerPageRenderMode(pageInfo) else ReaderPageRenderMode.COIL
                val retainInMemory = readerShouldRetainPageInMemory(einkMode, renderMode)
                val isDisplayTarget = page == pagerState.targetPage
                val isSettledPage = page == pagerState.settledPage
                var retryKey by remember(pageUrl) { mutableIntStateOf(0) }

                BoxWithConstraints(
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
                    val pageAspectRatio = pageInfo
                        ?.takeIf { it.width > 0 && it.height > 0 }
                        ?.let { it.width.toFloat() / it.height.toFloat() }
                    val displayWidthPx = constraints.maxWidth.coerceAtLeast(1)
                    val displayHeightPx = if (pageFit == "WIDTH" && pageAspectRatio != null) {
                        (displayWidthPx / pageAspectRatio).roundToInt().coerceAtLeast(1)
                    } else {
                        constraints.maxHeight.coerceAtLeast(1)
                    }
                    val pageRequestState = rememberReaderPageRequest(
                        url = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        originalSize = false,
                        displayWidthPx = displayWidthPx,
                        displayHeightPx = displayHeightPx,
                        displayQualityScale = if (isDisplayTarget) 1.25f else 1f,
                        displayMaxDecodedBytes = if (isDisplayTarget) {
                            pagerRenderMemoryBudget.currentPreviewBytes
                        } else {
                            pagerRenderMemoryBudget.adjacentPreviewBytes
                        },
                        retainInMemory = retainInMemory,
                        retryKey = retryKey
                    )
                    ZoomableReaderPageContent(
                        actualPageIndex = actualPageIndex,
                        pageUrl = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        pageRequestState = pageRequestState,
                        renderMode = renderMode,
                        retainedPainter = retainedPagePainters[pageUrl],
                        retryKey = retryKey,
                        pageFit = pageFit,
                        zoomState = zoomState,
                        isActive = isDisplayTarget,
                        previewQualityScale = if (isSettledPage || (isDisplayTarget && !pagerState.isScrollInProgress)) {
                            READER_CURRENT_PREVIEW_QUALITY_SCALE
                        } else {
                            READER_ADJACENT_PREVIEW_QUALITY_SCALE
                        },
                        einkMode = einkMode,
                        aiTranslatedPage = vm.currentAiTranslatedPage(actualPageIndex).takeIf { aiTranslationAvailable },
                        aiDisplayMode = if (aiTranslationAvailable) vm.aiTranslationDisplayModeForPage(actualPageIndex) else AiTranslationDisplayMode.OFF,
                        verticalGlyphSpacingMultiplier = verticalGlyphSpacingMultiplier,
                        onPainterRetained = { retainedPagePainters[pageUrl] = it },
                        onRetry = { retryKey += 1 },
                        onLongPress = { longPressUrl = pageUrl },
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
            onDismiss = { longPressUrl = null },
        )
    }
}

@Composable
private fun ZoomableReaderPageContent(
    actualPageIndex: Int,
    pageUrl: String,
    seriesId: String,
    bookId: String,
    pageRequestState: ReaderPageImageRequestState,
    renderMode: ReaderPageRenderMode,
    retainedPainter: Painter?,
    retryKey: Int,
    pageFit: String,
    zoomState: ZoomState,
    isActive: Boolean,
    previewQualityScale: Float,
    einkMode: Boolean,
    aiTranslatedPage: AiTranslatedPage?,
    aiDisplayMode: AiTranslationDisplayMode,
    verticalGlyphSpacingMultiplier: Float,
    onPainterRetained: (Painter) -> Unit,
    onRetry: () -> Unit,
    onLongPress: () -> Unit = {},
    onTap: (tapX: Float, width: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageWidthPx by remember(pageRequestState.request) { mutableIntStateOf(0) }
    var pageImageLoaded by remember(pageRequestState.request) { mutableStateOf(false) }
    var forceTiledRender by remember(pageUrl, renderMode) { mutableStateOf(false) }
    val effectiveRenderMode = if (forceTiledRender) ReaderPageRenderMode.TILED else renderMode
    val inputModifier = if (einkMode) {
        modifier
            .onSizeChanged { pageWidthPx = it.width }
            .pointerInput(onTap, onLongPress, pageWidthPx) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { position -> onTap(position.x, pageWidthPx.toFloat()) }
                )
            }
    } else {
        modifier
            .onSizeChanged { pageWidthPx = it.width }
            .zoomable(
                zoomState,
                onTap = { position -> onTap(position.x, pageWidthPx.toFloat()) }
            )
    }
    Box(
        inputModifier
    ) {
        when (effectiveRenderMode) {
            ReaderPageRenderMode.TILED -> ReaderTiledImage(
                url = pageUrl,
                seriesId = seriesId,
                bookId = bookId,
                retryKey = retryKey,
                fillWidth = pageFit == "WIDTH",
                zoomScale = zoomState.scale,
                zoomOffsetX = zoomState.offsetX,
                zoomOffsetY = zoomState.offsetY,
                isActive = isActive,
                previewQualityScale = previewQualityScale,
                progressListener = pageRequestState.progressState.listener,
                modifier = Modifier.matchParentSize(),
                loadingContent = {
                    PageLoadingPlaceholder(
                        progressState = pageRequestState.progressState,
                        einkMode = einkMode,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                errorContent = { ReaderPageError(onRetry = onRetry) },
                tileErrorContent = { retryTiles -> ReaderTileDecodeError(onRetry = retryTiles) },
                onLoadStart = pageRequestState.progressState::reset,
                onLoadComplete = pageRequestState.progressState::complete,
                onImageReady = { pageImageLoaded = true }
            )
            ReaderPageRenderMode.COIL -> SubcomposeAsyncImage(
                model = pageRequestState.request,
                contentDescription = stringResource(R.string.reader_page_description, actualPageIndex + 1),
                transform = rememberReaderPagePainterTransform(pageRequestState.request),
                contentScale = if (pageFit == "WIDTH") ContentScale.FillWidth else ContentScale.Fit,
                modifier = Modifier.matchParentSize()
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPainter)
                        if (fallbackPainter != null) {
                            SubcomposeAsyncImageContent(painter = fallbackPainter)
                        } else {
                            CachedPageLoadingContent(
                                state = state,
                                progressState = pageRequestState.progressState,
                                isLocalCacheHit = pageRequestState.isLocalCacheHit,
                                einkMode = einkMode,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    AsyncImagePainter.State.Empty -> {
                        val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPainter)
                        if (fallbackPainter != null) {
                            SubcomposeAsyncImageContent(painter = fallbackPainter)
                        } else if (!pageRequestState.isLocalCacheHit) {
                            PageLoadingPlaceholder(
                                progressState = pageRequestState.progressState,
                                einkMode = einkMode,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPainter)
                        if (fallbackPainter != null) {
                            SubcomposeAsyncImageContent(painter = fallbackPainter)
                        } else {
                            CachedPageErrorContent(
                                state = state,
                                modifier = Modifier.fillMaxSize(),
                                onRetry = onRetry
                            )
                        }
                    }
                    else -> {
                        val success = state as? AsyncImagePainter.State.Success
                        val drawable = success?.result?.drawable
                        if (readerDrawableExceedsCanvasSafeSize(drawable)) {
                            forceTiledRender = true
                            PageLoadingPlaceholder(
                                progressState = pageRequestState.progressState,
                                einkMode = einkMode,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            pageImageLoaded = true
                            if (success != null) onPainterRetained(success.painter)
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
        }
        AiTranslationOverlay(
            page = aiTranslatedPage.takeIf { pageImageLoaded },
            mode = aiDisplayMode,
            modifier = Modifier.matchParentSize(),
            fillWidth = pageFit == "WIDTH",
            verticalGlyphSpacingMultiplier = verticalGlyphSpacingMultiplier
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

@Composable
private fun ReaderTileDecodeError(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.reader_detail_load_failed), color = Color.White)
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.reader_retry), color = Color.White)
                }
            }
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
    onDismiss: () -> Unit,
) {
    val imageLoader = coil.Coil.imageLoader(context)
    val settingsScope = rememberCoroutineScope()
    val pageFit by vm.prefs.pageFit.collectAsStateWithLifecycle(initialValue = "FIT")
    val readingDirection by vm.prefs.readingDirection.collectAsStateWithLifecycle(initialValue = "LTR")
    val tapPageTurn by vm.prefs.tapPageTurn.collectAsStateWithLifecycle(initialValue = false)
    val pageCached = ReaderPageCache.hasCachedFile(context, vm.currentSeriesId, vm.currentBookId, url)
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
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_page_fit)) },
                supportingContent = {
                    Text(stringResource(if (pageFit == "WIDTH") R.string.settings_page_fit_width else R.string.settings_page_fit_fit))
                },
                leadingContent = { Icon(Icons.Default.FitScreen, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    settingsScope.launch { vm.prefs.setPageFit(if (pageFit == "WIDTH") "FIT" else "WIDTH") }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reading_direction)) },
                supportingContent = {
                    Text(stringResource(if (readingDirection == "RTL") R.string.settings_reading_rtl else R.string.settings_reading_ltr))
                },
                leadingContent = { Icon(Icons.Default.SwapHoriz, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    settingsScope.launch {
                        vm.prefs.setReadingDirection(if (readingDirection == "RTL") "LTR" else "RTL")
                    }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tap_page_turn)) },
                leadingContent = { Icon(Icons.Default.TouchApp, null) },
                trailingContent = { Switch(checked = tapPageTurn, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    settingsScope.launch { vm.prefs.setTapPageTurn(!tapPageTurn) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_action_cache_status)) },
                supportingContent = {
                    Text(stringResource(if (pageCached) R.string.reader_cached else R.string.reader_network_loading))
                },
                leadingContent = { Icon(Icons.Default.Cached, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollReader(
    vm: ReaderViewModel,
    onSetBookCover: (String) -> Unit,
    onSetSeriesCover: (String) -> Unit,
    canEditMetadata: Boolean,
    aiTranslationAvailable: Boolean,
    verticalGlyphSpacingMultiplier: Float
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var longPressUrl by remember { mutableStateOf<String?>(null) }
    val scrollScope = rememberCoroutineScope()
    val preloadPages by vm.prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
    val memoryAwarePreloadPages = readerMemoryAwarePreloadPages(preloadPages)
    val einkMode by vm.prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val retainedPagePainters = remember(vm.currentBookId) { mutableStateMapOf<String, Painter>() }
    val scrollRenderMemoryBudget = remember { readerRenderMemoryBudget() }
    var scrollPreloadDirection by remember(vm.currentBookId) { mutableIntStateOf(1) }
    var lastVisiblePage by remember(vm.currentBookId) { mutableIntStateOf(vm.currentPage) }

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
                if (index != null) {
                    val pageDelta = index - lastVisiblePage
                    if (pageDelta != 0) scrollPreloadDirection = if (pageDelta < 0) -1 else 1
                    lastVisiblePage = index
                    vm.updatePage(index)
                }
            }
    }

    LaunchedEffect(vm.currentPage, memoryAwarePreloadPages, scrollPreloadDirection, vm.pageUrls) {
        retainedPagePainters.trimReaderPagePainters(vm.pageUrls, vm.currentPage, memoryAwarePreloadPages)
        val forwardIndexes = (1..memoryAwarePreloadPages.coerceAtLeast(0))
            .map { distance -> vm.currentPage + scrollPreloadDirection * distance }
        val oppositeNeighbor = vm.currentPage - scrollPreloadDirection
        val preloadIndexes = (forwardIndexes + oppositeNeighbor)
            .distinct()
            .filter { it in vm.pageUrls.indices }
        coroutineScope {
            preloadIndexes.map { index ->
                async {
                    val pageUrl = vm.pageUrls[index]
                    ensureReaderPageFileCached(
                        context = context,
                        url = pageUrl,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        priority = ReaderPageLoadPriority.PREFETCH
                    )
                }
            }.awaitAll()
        }
    }
    DisposableEffect(einkMode, listState) {
        if (!einkMode) {
            onDispose {}
        } else {
            ReaderPhysicalKeyDispatcher.setHandler { event ->
                val keyAction = readerScrollPhysicalKeyAction(event.keyCode, einkMode = true)
                    ?: return@setHandler false
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val viewportHeight = (
                            listState.layoutInfo.viewportEndOffset -
                                listState.layoutInfo.viewportStartOffset
                            ).coerceAtLeast(1)
                        val distance = (viewportHeight * 0.88f).coerceAtLeast(240f)
                        val delta = when (keyAction) {
                            ReaderPhysicalKeyAction.ScrollBackward -> -distance
                            ReaderPhysicalKeyAction.ScrollForward -> distance
                            ReaderPhysicalKeyAction.PreviousPage -> -distance
                            ReaderPhysicalKeyAction.NextPage -> distance
                        }
                        scrollScope.launch { listState.scrollBy(delta) }
                        true
                    }
                    KeyEvent.ACTION_UP -> true
                    else -> false
                }
            }
            onDispose { ReaderPhysicalKeyDispatcher.setHandler(null) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(vm.pageUrls, key = { _, url -> url }) { index, url ->
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .combinedClickable(
                        onClick = { vm.toggleControls() },
                        onLongClick = { longPressUrl = url }
                    ),
                contentAlignment = Alignment.Center
            ) {
                var retryKey by remember(url) { mutableIntStateOf(0) }
                val pageInfo = vm.pageInfo(index)
                val renderMode = if (pageInfo != null) readerPageRenderMode(pageInfo) else ReaderPageRenderMode.COIL
                val pageAspectRatio = if (pageInfo != null && pageInfo.width > 0 && pageInfo.height > 0) {
                    pageInfo.width.toFloat() / pageInfo.height.toFloat()
                } else {
                    0.7f
                }
                val displayWidthPx = constraints.maxWidth.coerceAtLeast(1)
                val displayHeightPx = (displayWidthPx / pageAspectRatio).roundToInt().coerceAtLeast(1)
                val pageRequestState = rememberReaderPageRequest(
                    url = url,
                    seriesId = vm.currentSeriesId,
                    bookId = vm.currentBookId,
                    originalSize = false,
                    displayWidthPx = displayWidthPx,
                    displayHeightPx = displayHeightPx,
                    displayQualityScale = if (index == vm.currentPage) 1.25f else 1f,
                    displayMaxDecodedBytes = if (index == vm.currentPage) {
                        scrollRenderMemoryBudget.currentPreviewBytes
                    } else {
                        scrollRenderMemoryBudget.adjacentPreviewBytes
                    },
                    retryKey = retryKey
                )
                var pageImageLoaded by remember(pageRequestState.request) { mutableStateOf(false) }
                var forceTiledRender by remember(url, renderMode) { mutableStateOf(false) }
                val effectiveRenderMode = if (forceTiledRender) ReaderPageRenderMode.TILED else renderMode
                when (effectiveRenderMode) {
                    ReaderPageRenderMode.TILED -> ReaderTiledImage(
                        url = url,
                        seriesId = vm.currentSeriesId,
                        bookId = vm.currentBookId,
                        retryKey = retryKey,
                        fillWidth = true,
                        isActive = index == vm.currentPage,
                        retainPreview = kotlin.math.abs(index - vm.currentPage) <= 1,
                        previewQualityScale = if (index == vm.currentPage) {
                            READER_CURRENT_PREVIEW_QUALITY_SCALE
                        } else {
                            READER_ADJACENT_PREVIEW_QUALITY_SCALE
                        },
                        progressListener = pageRequestState.progressState.listener,
                        modifier = Modifier.fillMaxWidth().aspectRatio(pageAspectRatio),
                        loadingContent = {
                            PageLoadingPlaceholder(
                                progressState = pageRequestState.progressState,
                                einkMode = einkMode,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        },
                        errorContent = { ReaderPageError(onRetry = { retryKey += 1 }) },
                        tileErrorContent = { retryTiles -> ReaderTileDecodeError(onRetry = retryTiles) },
                        onLoadStart = pageRequestState.progressState::reset,
                        onLoadComplete = pageRequestState.progressState::complete,
                        onImageReady = { pageImageLoaded = true }
                    )
                    ReaderPageRenderMode.COIL -> SubcomposeAsyncImage(
                        model = pageRequestState.request,
                        contentDescription = stringResource(R.string.reader_page_description, index + 1),
                        transform = rememberReaderPagePainterTransform(pageRequestState.request),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (val state = painter.state) {
                            is AsyncImagePainter.State.Loading -> {
                                val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPagePainters[url])
                                if (fallbackPainter != null) {
                                    SubcomposeAsyncImageContent(painter = fallbackPainter)
                                } else {
                                    CachedPageLoadingContent(
                                        state = state,
                                        progressState = pageRequestState.progressState,
                                        isLocalCacheHit = pageRequestState.isLocalCacheHit,
                                        einkMode = einkMode,
                                        modifier = Modifier.fillMaxWidth().height(400.dp)
                                    )
                                }
                            }
                            AsyncImagePainter.State.Empty -> {
                                val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPagePainters[url])
                                if (fallbackPainter != null) {
                                    SubcomposeAsyncImageContent(painter = fallbackPainter)
                                } else if (!pageRequestState.isLocalCacheHit) {
                                    PageLoadingPlaceholder(
                                        progressState = pageRequestState.progressState,
                                        einkMode = einkMode,
                                        modifier = Modifier.fillMaxWidth().height(400.dp)
                                    )
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                val fallbackPainter = readerFallbackPainterForTransientState(state, retainedPagePainters[url])
                                if (fallbackPainter != null) {
                                    SubcomposeAsyncImageContent(painter = fallbackPainter)
                                } else {
                                    CachedPageErrorContent(
                                        state = state,
                                        modifier = Modifier.fillMaxWidth().height(400.dp),
                                        onRetry = { retryKey += 1 }
                                    )
                                }
                            }
                            else -> {
                                val success = state as? AsyncImagePainter.State.Success
                                val drawable = success?.result?.drawable
                                if (readerDrawableExceedsCanvasSafeSize(drawable)) {
                                    forceTiledRender = true
                                    PageLoadingPlaceholder(
                                        progressState = pageRequestState.progressState,
                                        einkMode = einkMode,
                                        modifier = Modifier.fillMaxWidth().height(400.dp)
                                    )
                                } else {
                                    pageImageLoaded = true
                                    if (success != null) retainedPagePainters[url] = success.painter
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }
                    }
                }
                if (aiTranslationAvailable) {
                    AiTranslationOverlay(
                        page = vm.currentAiTranslatedPage(index).takeIf { pageImageLoaded },
                        mode = vm.aiTranslationDisplayModeForPage(index),
                        modifier = Modifier.matchParentSize(),
                        fillWidth = true,
                        verticalGlyphSpacingMultiplier = verticalGlyphSpacingMultiplier
                    )
                }
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
            onDismiss = { longPressUrl = null }
        )
    }
}
