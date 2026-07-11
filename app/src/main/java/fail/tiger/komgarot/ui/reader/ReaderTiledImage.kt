package fail.tiger.komgarot.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.remote.ImageDownloadProgressListener
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import okhttp3.Request
import android.graphics.Color as AndroidColor

@Composable
fun ReaderTiledImage(
    url: String,
    seriesId: String,
    bookId: String,
    modifier: Modifier = Modifier,
    retryKey: Int = 0,
    fillWidth: Boolean = false,
    zoomScale: Float = 1f,
    zoomOffsetX: Float = 0f,
    zoomOffsetY: Float = 0f,
    isActive: Boolean = true,
    retainPreview: Boolean = true,
    previewQualityScale: Float = READER_ADJACENT_PREVIEW_QUALITY_SCALE,
    progressListener: ImageDownloadProgressListener? = null,
    loadingContent: @Composable () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
    tileErrorContent: @Composable (onRetry: () -> Unit) -> Unit = {},
    onLoadStart: () -> Unit = {},
    onLoadComplete: () -> Unit = {},
    onImageReady: () -> Unit = {}
) {
    val context = LocalContext.current
    var cachedFile by remember(url, seriesId, bookId, retryKey) {
        mutableStateOf(ReaderPageCache.cachedFile(context, seriesId, bookId, url))
    }
    var failed by remember(url, seriesId, bookId, retryKey) { mutableStateOf(false) }
    var decodeFailed by remember(url, seriesId, bookId, retryKey) { mutableStateOf(false) }
    var tileDecodeFailed by remember(url, seriesId, bookId, retryKey) { mutableStateOf(false) }
    var tiledImageView by remember(url, seriesId, bookId, retryKey) { mutableStateOf<ReaderTiledImageView?>(null) }

    LaunchedEffect(context, url, seriesId, bookId, retryKey) {
        failed = false
        decodeFailed = false
        tileDecodeFailed = false
        onLoadStart()
        cachedFile = ensureReaderPageFileCached(
            context = context,
            url = url,
            seriesId = seriesId,
            bookId = bookId,
            progressListener = progressListener
        )
        if (cachedFile != null) onLoadComplete()
        failed = cachedFile == null
    }

    val file = cachedFile
    var previewReady by remember(file) { mutableStateOf(false) }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            file != null -> key(retryKey) {
                AndroidView(
                    factory = { viewContext -> ReaderTiledImageView(viewContext) },
                    update = { view ->
                        tiledImageView = view
                        view.onPreviewReady = {
                            if (!previewReady) {
                                previewReady = true
                                onImageReady()
                            }
                        }
                        view.onPreviewReleased = { previewReady = false }
                        view.onDecodeError = {
                            if (!previewReady && !decodeFailed) {
                                ReaderPageCache.removeCachedFile(context, seriesId, bookId, url)
                                invalidateReaderPreviewCache(file)
                                decodeFailed = true
                            }
                        }
                        view.onTileDecodeError = { tileDecodeFailed = true }
                        view.onTileDecodeRecovered = { tileDecodeFailed = false }
                        view.setImageFile(
                            file = file,
                            fillWidth = fillWidth,
                            zoomScale = zoomScale,
                            zoomOffsetX = zoomOffsetX,
                            zoomOffsetY = zoomOffsetY,
                            previewQualityScale = previewQualityScale,
                            isActive = isActive,
                            retainPreview = retainPreview,
                            contentVersion = retryKey
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            failed -> errorContent()
            else -> loadingContent()
        }
        if (file != null && !previewReady) {
            if (decodeFailed) errorContent() else loadingContent()
        }
        if (file != null && previewReady && tileDecodeFailed) {
            tileErrorContent {
                tileDecodeFailed = false
                tiledImageView?.retryFailedTiles()
            }
        }
    }
}

internal suspend fun ensureReaderPageFileCached(
    context: Context,
    url: String,
    seriesId: String,
    bookId: String,
    progressListener: ImageDownloadProgressListener? = null,
    priority: ReaderPageLoadPriority = ReaderPageLoadPriority.DISPLAY
): File? = withContext(Dispatchers.IO) {
    ReaderPageCache.cachedFile(context, seriesId, bookId, url)?.let { return@withContext it }

    ReaderPageLoadCoordinator.loadFile("$seriesId:$bookId:$url", priority) load@{
        ReaderPageCache.cachedFile(context, seriesId, bookId, url)?.let { return@load it }
        val app = context.applicationContext as? KomgarotApp ?: return@load null
        val entry = ReaderPageCache.entry(context, seriesId, bookId, url)
        val maxSizeBytes = app.authPreferences.readerCacheSizeBytesBlocking
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "image/*,*/*;q=0.8")
            .get()

        if (progressListener != null) {
            requestBuilder.tag(ImageDownloadProgressListener::class.java, progressListener)
        }

        try {
            app.okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    ReaderPageCache.discard(entry)
                    return@load null
                }

                entry.tempFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    entry.tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (ReaderPageCache.commit(context, entry, maxSizeBytes)) {
                ReaderPageCache.cachedFile(context, seriesId, bookId, url)
            } else {
                null
            }
        } catch (error: CancellationException) {
            ReaderPageCache.discard(entry)
            throw error
        } catch (error: IOException) {
            ReaderPageCache.discard(entry)
            null
        } catch (error: IllegalArgumentException) {
            ReaderPageCache.discard(entry)
            null
        }
    }
}

class ReaderTiledImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val previewDestinationRect = Rect()
    private val tileSourceRect = Rect()
    private val tileDestinationRect = Rect()
    private val zoomVisibleBounds = FloatArray(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingTileKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val tileRetryCounts = Collections.synchronizedMap(mutableMapOf<String, Int>())
    private val decoderLock = Any()
    private var imageFile: File? = null
    private var decoder: BitmapRegionDecoder? = null
    private var previewBitmap: Bitmap? = null
    private var previewKey: String = ""
    private var previewCacheKey: String = ""
    private var pendingPreviewKey: String = ""
    private var decoderOpenPending = false
    private var decoderReleasePending = false
    @Volatile private var decoderGeneration = 0
    @Volatile private var tileDecodeGeneration = 0
    private var tileSessionSampleSize = 0
    private var tileSessionFirstColumn = -1
    private var tileSessionLastColumn = -1
    private var tileSessionFirstRow = -1
    private var tileSessionLastRow = -1
    private var imageWidth = 0
    private var imageHeight = 0
    private var fillWidth = false
    private var zoomScale = 1f
    private var zoomOffsetX = 0f
    private var zoomOffsetY = 0f
    @Volatile private var isActive = true
    private var retainPreview = true
    private var previewQualityScale = READER_ADJACENT_PREVIEW_QUALITY_SCALE
    private var contentVersion = 0
    private var previewNeedsDetailTiles = false
    var onPreviewReady: (() -> Unit)? = null
    var onPreviewReleased: (() -> Unit)? = null
    var onDecodeError: (() -> Unit)? = null
    var onTileDecodeError: (() -> Unit)? = null
    var onTileDecodeRecovered: (() -> Unit)? = null
    private val tileCache = object : LruCache<String, Bitmap>(readerRenderMemoryBudget().activeTileBytes.toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (newValue == null) recycleBitmap(oldValue)
        }
    }

    fun setImageFile(
        file: File,
        fillWidth: Boolean,
        zoomScale: Float,
        zoomOffsetX: Float,
        zoomOffsetY: Float,
        previewQualityScale: Float,
        isActive: Boolean,
        retainPreview: Boolean,
        contentVersion: Int
    ) {
        val sameFile = imageFile?.absolutePath == file.absolutePath
        val normalizedZoomScale = zoomScale.coerceAtLeast(1f)
        val normalizedPreviewQualityScale = previewQualityScale.coerceAtLeast(READER_ADJACENT_PREVIEW_QUALITY_SCALE)
        val previewQualityChanged = normalizedPreviewQualityScale != this.previewQualityScale
        val retainPreviewChanged = retainPreview != this.retainPreview
        val becameActive = isActive && !this.isActive
        val becameInactive = !isActive && this.isActive
        if (sameFile && !retainPreview && !retainPreviewChanged && previewBitmap == null) return
        if (
            sameFile &&
            this.fillWidth == fillWidth &&
            this.zoomScale == normalizedZoomScale &&
            this.zoomOffsetX == zoomOffsetX &&
            this.zoomOffsetY == zoomOffsetY &&
            this.isActive == isActive &&
            this.retainPreview == retainPreview &&
            this.contentVersion == contentVersion &&
            !previewQualityChanged &&
            (!isActive || decoder != null) &&
            !decoderReleasePending
        ) return
        this.fillWidth = fillWidth
        this.zoomScale = normalizedZoomScale
        this.zoomOffsetX = zoomOffsetX
        this.zoomOffsetY = zoomOffsetY
        this.isActive = isActive
        this.retainPreview = retainPreview
        this.contentVersion = contentVersion
        if (becameInactive) releaseTileSessionAndDecoder()
        if (!retainPreview) {
            releaseDecoder(clearPreview = true)
            imageFile = file
            invalidate()
            return
        }
        if (!sameFile) {
            releaseDecoder(clearPreview = true)
            imageFile = file
            this.previewQualityScale = normalizedPreviewQualityScale
            val cachedPreviewKey = readerPreviewCacheKey(
                file,
                width,
                height,
                fillWidth,
                this.previewQualityScale,
                contentVersion
            )
            readerPreviewBitmapCache.get(cachedPreviewKey)?.let { cachedPreview ->
                previewBitmap = cachedPreview
                previewCacheKey = cachedPreviewKey
            }
            openImageAsync(file, decoderGeneration)
        } else {
            this.previewQualityScale = normalizedPreviewQualityScale
            if ((isActive && (decoder == null || decoderReleasePending)) || previewBitmap == null || becameActive) {
                openImageAsync(file, decoderGeneration)
            } else if (previewQualityChanged && width > 0 && height > 0) {
                requestPreviewDecode(
                    readerPreviewKey(imageWidth, imageHeight, width, height, fillWidth, this.previewQualityScale)
                )
            }
        }
        invalidate()
    }

    private fun openImageAsync(file: File, generation: Int) {
        if (decoderOpenPending) return
        decoderOpenPending = true
        val viewportWidth = width
        val viewportHeight = height
        val previewFillWidth = fillWidth
        val openedPreviewQualityScale = previewQualityScale
        val shouldOpenRegionDecoder = isActive
        val openedContentVersion = contentVersion
        val openedPreviewCacheKey = readerPreviewCacheKey(
            file,
            viewportWidth,
            viewportHeight,
            previewFillWidth,
            openedPreviewQualityScale,
            openedContentVersion
        )
        val previewPriority = if (shouldOpenRegionDecoder) {
            ReaderPageLoadPriority.DISPLAY
        } else {
            ReaderPageLoadPriority.PREFETCH
        }
        ReaderPageLoadCoordinator.executePreview(previewPriority) {
            if (generation != decoderGeneration) return@executePreview
            val imageSize = readReaderImageSize(file)
            val openedWidth = imageSize.first
            val openedHeight = imageSize.second
            val openedPreviewKey = readerPreviewKey(
                openedWidth,
                openedHeight,
                viewportWidth,
                viewportHeight,
                previewFillWidth,
                openedPreviewQualityScale
            )
            val previewPlan = readerPreviewDecodePlan(
                imageWidth = openedWidth,
                imageHeight = openedHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                fillWidth = previewFillWidth,
                qualityScale = openedPreviewQualityScale,
                maxDecodedBytes = readerPreviewMaxDecodedBytes(openedPreviewQualityScale)
            )
            val cachedPreview = readerPreviewBitmapCache.get(openedPreviewCacheKey)
            val openedPreviewResult = if (cachedPreview != null) {
                ReaderPreviewDecodeResult(
                    cachedPreview,
                    previewPlan.meetsQualityTarget,
                    readerPreviewBitmapMeetsDisplayTarget(
                        bitmapWidth = cachedPreview.width,
                        bitmapHeight = cachedPreview.height,
                        imageWidth = openedWidth,
                        imageHeight = openedHeight,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        fillWidth = previewFillWidth
                    )
                )
            } else {
                decodeReaderPreviewFile(
                    file = file,
                    imageWidth = openedWidth,
                    imageHeight = openedHeight,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    fillWidth = previewFillWidth,
                    qualityScale = openedPreviewQualityScale
                )?.also { result -> readerPreviewBitmapCache.put(openedPreviewCacheKey, result.bitmap) }
            }
            if (generation != decoderGeneration) return@executePreview
            val openedDecoder = if (shouldOpenRegionDecoder) {
                runCatching { newReaderBitmapRegionDecoder(file) }.getOrNull()
            } else {
                null
            }
            if (generation != decoderGeneration) {
                openedDecoder?.recycle()
                return@executePreview
            }
            mainHandler.post {
                if (
                    generation != decoderGeneration ||
                    imageFile?.absolutePath != file.absolutePath
                ) {
                    openedDecoder?.recycle()
                    return@post
                }
                decoderOpenPending = false
                if (openedContentVersion != contentVersion) {
                    openedDecoder?.recycle()
                    imageFile?.let { activeFile -> openImageAsync(activeFile, decoderGeneration) }
                    return@post
                }
                if (openedPreviewResult != null) {
                    publishOpenedPreview(openedPreviewResult, openedPreviewKey, openedPreviewCacheKey)
                }
                if (
                    openedPreviewQualityScale != previewQualityScale ||
                    shouldOpenRegionDecoder != isActive
                ) {
                    openedDecoder?.recycle()
                    imageWidth = openedWidth
                    imageHeight = openedHeight
                    imageFile?.let { activeFile -> openImageAsync(activeFile, decoderGeneration) }
                    invalidate()
                    return@post
                }
                synchronized(decoderLock) {
                    decoder?.recycle()
                    decoder = openedDecoder
                    imageWidth = openedWidth
                    imageHeight = openedHeight
                }
                decoderReleasePending = false
                if (shouldOpenRegionDecoder && openedDecoder == null && openedPreviewResult != null) {
                    onTileDecodeError?.invoke()
                } else if (openedDecoder != null) {
                    onTileDecodeRecovered?.invoke()
                }
                if (openedPreviewResult == null && viewportWidth > 0 && viewportHeight > 0) {
                    onDecodeError?.invoke()
                }
                invalidate()
            }
        }
    }

    private fun publishOpenedPreview(
        result: ReaderPreviewDecodeResult,
        key: String,
        cacheKey: String
    ) {
        previewBitmap = result.bitmap
        previewKey = key
        previewCacheKey = cacheKey
        previewNeedsDetailTiles = !result.meetsDisplayTarget
        onPreviewReady?.invoke()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) return
        val key = readerPreviewKey(imageWidth, imageHeight, width, height, fillWidth, previewQualityScale)
        val cacheKey = imageFile?.let {
            readerPreviewCacheKey(it, width, height, fillWidth, previewQualityScale, contentVersion)
        }.orEmpty()
        if (cacheKey.isNotBlank() && previewCacheKey != cacheKey) {
            readerPreviewBitmapCache.get(cacheKey)?.let { cachedPreview ->
                previewBitmap = cachedPreview
                previewKey = key
                previewCacheKey = cacheKey
                previewNeedsDetailTiles = !readerPreviewBitmapMeetsDisplayTarget(
                    bitmapWidth = cachedPreview.width,
                    bitmapHeight = cachedPreview.height,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    viewportWidth = width,
                    viewportHeight = height,
                    fillWidth = fillWidth
                )
                onPreviewReady?.invoke()
                invalidate()
                return
            }
        }
        if (previewBitmap == null || previewKey != key) {
            requestPreviewDecode(key)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        canvas.drawColor(AndroidColor.BLACK)
        val fitScale = if (fillWidth) {
            width / imageWidth.toFloat()
        } else {
            minOf(width / imageWidth.toFloat(), height / imageHeight.toFloat())
        }.coerceAtLeast(0.0001f)
        val renderedWidth = imageWidth * fitScale
        val renderedHeight = imageHeight * fitScale
        val offsetX = (width - renderedWidth) / 2f
        val offsetY = if (fillWidth && renderedHeight > height) 0f else (height - renderedHeight) / 2f
        previewDestinationRect.set(
            offsetX.toInt(),
            offsetY.toInt(),
            (offsetX + renderedWidth).toInt(),
            (offsetY + renderedHeight).toInt()
        )
        drawPreviewBitmap(canvas, previewDestinationRect)
        if (!isActive || decoder == null || decoderReleasePending) return
        if (!shouldDrawReaderTiles(zoomScale, previewNeedsDetailTiles)) return

        fillReaderZoomVisibleBounds(width, height, zoomScale, zoomOffsetX, zoomOffsetY, zoomVisibleBounds)
        val visibleLeft = floor(((zoomVisibleBounds[0] - offsetX) / fitScale).coerceIn(0f, imageWidth.toFloat())).toInt()
        val visibleTop = floor(((zoomVisibleBounds[1] - offsetY) / fitScale).coerceIn(0f, imageHeight.toFloat())).toInt()
        val visibleRight = ceil(((zoomVisibleBounds[2] - offsetX) / fitScale).coerceIn(0f, imageWidth.toFloat())).toInt()
        val visibleBottom = ceil(((zoomVisibleBounds[3] - offsetY) / fitScale).coerceIn(0f, imageHeight.toFloat())).toInt()
        if (visibleLeft >= visibleRight || visibleTop >= visibleBottom) return

        val sampleSize = readerTileSampleSizeForCache(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            visibleLeft = visibleLeft,
            visibleTop = visibleTop,
            visibleRight = visibleRight,
            visibleBottom = visibleBottom,
            initialSampleSize = readerTileSampleSize(imageWidth, imageHeight, width, height, zoomScale, fillWidth),
            maxCacheBytes = readerActiveTileCacheBytes(Long.MAX_VALUE)
        )
        val tileSize = READER_TILE_SOURCE_SIZE * sampleSize
        val firstColumn = (visibleLeft / tileSize).coerceAtLeast(0)
        val lastColumn = ((visibleRight - 1) / tileSize).coerceAtLeast(firstColumn)
        val firstRow = (visibleTop / tileSize).coerceAtLeast(0)
        val lastRow = ((visibleBottom - 1) / tileSize).coerceAtLeast(firstRow)

        if (
            sampleSize != tileSessionSampleSize ||
            firstColumn != tileSessionFirstColumn ||
            lastColumn != tileSessionLastColumn ||
            firstRow != tileSessionFirstRow ||
            lastRow != tileSessionLastRow
        ) {
            tileSessionSampleSize = sampleSize
            tileSessionFirstColumn = firstColumn
            tileSessionLastColumn = lastColumn
            tileSessionFirstRow = firstRow
            tileSessionLastRow = lastRow
            val visibleTileBytes = readerVisibleTileWorkingSetBytes(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                firstColumn = firstColumn,
                lastColumn = lastColumn,
                firstRow = firstRow,
                lastRow = lastRow,
                sampleSize = sampleSize
            )
            tileCache.resize(readerActiveTileCacheBytes(visibleTileBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            tileDecodeGeneration += 1
            pendingTileKeys.clear()
            tileRetryCounts.clear()
            mainHandler.post { onTileDecodeRecovered?.invoke() }
        }
        val sessionGeneration = tileDecodeGeneration
        val centerColumn = (firstColumn + lastColumn) / 2
        val centerRow = (firstRow + lastRow) / 2
        val maxRadius = max(
            max(abs(firstColumn - centerColumn), abs(lastColumn - centerColumn)),
            max(abs(firstRow - centerRow), abs(lastRow - centerRow))
        )
        for (radius in 0..maxRadius) {
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    if (max(abs(column - centerColumn), abs(row - centerRow)) != radius) continue
                    val left = column * tileSize
                    val top = row * tileSize
                    tileSourceRect.set(
                        left,
                        top,
                        minOf(left + tileSize, imageWidth),
                        minOf(top + tileSize, imageHeight)
                    )
                    val key = readerTileKey(tileSourceRect, sampleSize)
                    val bitmap = tileCache.get(key)
                    if (bitmap == null) {
                        requestTileDecode(key, tileSourceRect, sampleSize, sessionGeneration)
                        continue
                    }
                    tileDestinationRect.set(
                        (offsetX + tileSourceRect.left * fitScale).toInt(),
                        (offsetY + tileSourceRect.top * fitScale).toInt(),
                        (offsetX + tileSourceRect.right * fitScale).toInt(),
                        (offsetY + tileSourceRect.bottom * fitScale).toInt()
                    )
                    canvas.drawBitmap(bitmap, null, tileDestinationRect, paint)
                }
            }
        }
    }

    private fun drawPreviewBitmap(canvas: Canvas, destinationRect: Rect) {
        val bitmap = previewBitmap ?: return
        canvas.drawBitmap(bitmap, null, destinationRect, paint)
    }

    private fun requestPreviewDecode(key: String) {
        if (key == pendingPreviewKey) return
        pendingPreviewKey = key
        val generation = decoderGeneration
        val previewImageWidth = imageWidth
        val previewImageHeight = imageHeight
        val previewViewportWidth = width
        val previewViewportHeight = height
        val previewFillWidth = fillWidth
        val requestedPreviewQualityScale = previewQualityScale
        val previewFile = imageFile ?: return
        val requestedContentVersion = contentVersion
        val cacheKey = previewFile.let {
            readerPreviewCacheKey(
                it,
                previewViewportWidth,
                previewViewportHeight,
                previewFillWidth,
                requestedPreviewQualityScale,
                requestedContentVersion
            )
        }
        readerPreviewBitmapCache.get(cacheKey)?.let { cachedPreview ->
            pendingPreviewKey = ""
            previewBitmap = cachedPreview
            previewKey = key
            previewCacheKey = cacheKey
            previewNeedsDetailTiles = !readerPreviewBitmapMeetsDisplayTarget(
                bitmapWidth = cachedPreview.width,
                bitmapHeight = cachedPreview.height,
                imageWidth = previewImageWidth,
                imageHeight = previewImageHeight,
                viewportWidth = previewViewportWidth,
                viewportHeight = previewViewportHeight,
                fillWidth = previewFillWidth
            )
            onPreviewReady?.invoke()
            invalidate()
            return
        }
        val previewPriority = if (isActive) ReaderPageLoadPriority.DISPLAY else ReaderPageLoadPriority.PREFETCH
        ReaderPageLoadCoordinator.executePreview(previewPriority) {
            if (generation != decoderGeneration) return@executePreview
            val queuedCachedPreview = readerPreviewBitmapCache.get(cacheKey)
            val decodeResult = if (queuedCachedPreview != null) {
                val plan = readerPreviewDecodePlan(
                    imageWidth = previewImageWidth,
                    imageHeight = previewImageHeight,
                    viewportWidth = previewViewportWidth,
                    viewportHeight = previewViewportHeight,
                    fillWidth = previewFillWidth,
                    qualityScale = requestedPreviewQualityScale,
                    maxDecodedBytes = readerPreviewMaxDecodedBytes(requestedPreviewQualityScale)
                )
                ReaderPreviewDecodeResult(
                    queuedCachedPreview,
                    plan.meetsQualityTarget,
                    readerPreviewBitmapMeetsDisplayTarget(
                        bitmapWidth = queuedCachedPreview.width,
                        bitmapHeight = queuedCachedPreview.height,
                        imageWidth = previewImageWidth,
                        imageHeight = previewImageHeight,
                        viewportWidth = previewViewportWidth,
                        viewportHeight = previewViewportHeight,
                        fillWidth = previewFillWidth
                    )
                )
            } else {
                decodeReaderPreviewFile(
                    file = previewFile,
                    imageWidth = previewImageWidth,
                    imageHeight = previewImageHeight,
                    viewportWidth = previewViewportWidth,
                    viewportHeight = previewViewportHeight,
                    fillWidth = previewFillWidth,
                    qualityScale = requestedPreviewQualityScale
                )
            }
            if (generation != decoderGeneration) {
                if (queuedCachedPreview == null) decodeResult?.bitmap?.let(::recycleBitmap)
                return@executePreview
            }
            if (decodeResult != null && queuedCachedPreview == null) {
                readerPreviewBitmapCache.put(cacheKey, decodeResult.bitmap)
            }
            mainHandler.post {
                if (pendingPreviewKey == key) pendingPreviewKey = ""
                if (
                    requestedPreviewQualityScale != previewQualityScale ||
                    requestedContentVersion != contentVersion
                ) {
                    return@post
                }
                if (decodeResult == null) {
                    if (generation == decoderGeneration) onDecodeError?.invoke()
                    return@post
                }
                if (generation != decoderGeneration) {
                    return@post
                }
                val cachedBitmap = readerPreviewBitmapCache.get(cacheKey) ?: decodeResult.bitmap
                previewBitmap = cachedBitmap
                previewKey = key
                previewCacheKey = cacheKey
                previewNeedsDetailTiles = !decodeResult.meetsDisplayTarget
                onPreviewReady?.invoke()
                invalidate()
            }
        }
    }

    private fun requestTileDecode(key: String, rect: Rect, sampleSize: Int, sessionGeneration: Int) {
        if (tileCache.get(key) != null) return
        if ((tileRetryCounts[key] ?: 0) >= READER_TILE_DECODE_ATTEMPTS) return
        val pendingToken = "$sessionGeneration:$key"
        if (!pendingTileKeys.add(pendingToken)) return
        val generation = decoderGeneration
        val decodeRect = Rect(rect)
        ReaderPageLoadCoordinator.executeTile {
            if (generation != decoderGeneration || sessionGeneration != tileDecodeGeneration || !isActive) {
                pendingTileKeys.remove(pendingToken)
                return@executeTile
            }
            if (tileCache.get(key) != null) {
                pendingTileKeys.remove(pendingToken)
                return@executeTile
            }
            val bitmap = synchronized(decoderLock) {
                val activeDecoder = decoder
                if (
                    activeDecoder == null ||
                    activeDecoder.isRecycled ||
                    generation != decoderGeneration ||
                    sessionGeneration != tileDecodeGeneration ||
                    !isActive
                ) {
                    null
                } else {
                    runCatching {
                        activeDecoder.decodeRegion(
                            decodeRect,
                            BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                        )
                    }.getOrNull()
                }
            }
            mainHandler.post {
                pendingTileKeys.remove(pendingToken)
                if (bitmap == null) {
                    if (generation == decoderGeneration && sessionGeneration == tileDecodeGeneration && isActive) {
                        val retryCount = (tileRetryCounts[key] ?: 0) + 1
                        tileRetryCounts[key] = retryCount
                        if (retryCount >= READER_TILE_DECODE_ATTEMPTS) onTileDecodeError?.invoke()
                        invalidate()
                    }
                    return@post
                }
                if (
                    generation != decoderGeneration ||
                    sessionGeneration != tileDecodeGeneration ||
                    !isActive
                ) {
                    recycleBitmap(bitmap)
                } else {
                    tileRetryCounts.remove(key)
                    tileCache.put(key, bitmap)
                    val hasFailedTiles = synchronized(tileRetryCounts) {
                        tileRetryCounts.values.any { retryCount ->
                            retryCount >= READER_TILE_DECODE_ATTEMPTS
                        }
                    }
                    if (!hasFailedTiles) onTileDecodeRecovered?.invoke()
                    invalidate()
                }
            }
        }
    }

    fun retryFailedTiles() {
        tileDecodeGeneration += 1
        pendingTileKeys.clear()
        tileRetryCounts.clear()
        onTileDecodeRecovered?.invoke()
        if (decoder == null && isActive) {
            imageFile?.let { file -> openImageAsync(file, decoderGeneration) }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseDecoder(clearPreview = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val file = imageFile
        if (file != null && (previewBitmap == null || isActive)) openImageAsync(file, decoderGeneration)
    }

    private fun releaseTileSessionAndDecoder() {
        tileDecodeGeneration += 1
        tileSessionSampleSize = 0
        tileSessionFirstColumn = -1
        tileSessionLastColumn = -1
        tileSessionFirstRow = -1
        tileSessionLastRow = -1
        pendingTileKeys.clear()
        tileRetryCounts.clear()
        tileCache.evictAll()
        releaseDecoder(clearPreview = false)
    }

    private fun releaseDecoder(clearPreview: Boolean) {
        decoderGeneration += 1
        tileDecodeGeneration += 1
        val releaseGeneration = decoderGeneration
        decoderOpenPending = false
        decoderReleasePending = true
        pendingTileKeys.clear()
        tileRetryCounts.clear()
        pendingPreviewKey = ""
        tileCache.evictAll()
        ReaderPageLoadCoordinator.executePreview(ReaderPageLoadPriority.DISPLAY) {
            synchronized(decoderLock) {
                if (releaseGeneration == decoderGeneration) {
                    decoder?.recycle()
                    decoder = null
                }
            }
            mainHandler.post {
                if (releaseGeneration == decoderGeneration) decoderReleasePending = false
            }
        }
        if (clearPreview) {
            val hadPreview = previewBitmap != null
            previewKey = ""
            previewCacheKey = ""
            previewBitmap = null
            previewNeedsDetailTiles = false
            imageWidth = 0
            imageHeight = 0
            if (hadPreview) onPreviewReleased?.invoke()
        }
    }
}

private fun readerTileKey(rect: Rect, sampleSize: Int): String =
    "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}:$sampleSize"

private fun readerPreviewKey(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    qualityScale: Float
): String =
    "$imageWidth:$imageHeight:$viewportWidth:$viewportHeight:$fillWidth:$qualityScale"

private fun readerPreviewCacheKey(
    file: File,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    qualityScale: Float,
    contentVersion: Int
): String =
    "${file.absolutePath}:${readerFileIdentity(file)}:$viewportWidth:$viewportHeight:$fillWidth:$qualityScale:$contentVersion"

private fun readerFileIdentity(file: File): String = runCatching {
    val stat = Os.stat(file.absolutePath)
    "${stat.st_dev}:${stat.st_ino}:${file.length()}"
}.getOrElse {
    "${file.length()}:${file.lastModified()}"
}

private data class ReaderPreviewDecodeResult(
    val bitmap: Bitmap,
    val meetsQualityTarget: Boolean,
    val meetsDisplayTarget: Boolean
)

private fun decodeReaderPreviewFile(
    file: File,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    qualityScale: Float
): ReaderPreviewDecodeResult? {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return null
    }
    val plan = readerPreviewDecodePlan(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        fillWidth = fillWidth,
        qualityScale = qualityScale,
        maxDecodedBytes = readerPreviewMaxDecodedBytes(qualityScale)
    )
    val attemptedSamples = listOf(plan.sampleSize, (plan.sampleSize * 2).coerceAtMost(32)).distinct()
    for (sampleSize in attemptedSamples) {
        val bitmap = runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }.getOrNull() ?: continue
        return ReaderPreviewDecodeResult(
            bitmap = bitmap,
            meetsQualityTarget = sampleSize <= plan.qualitySampleSize,
            meetsDisplayTarget = sampleSize <= plan.displaySampleSize
        )
    }
    return null
}

private fun readReaderImageSize(file: File): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
}

@Suppress("DEPRECATION")
private fun newReaderBitmapRegionDecoder(file: File): BitmapRegionDecoder? =
    BitmapRegionDecoder.newInstance(file.absolutePath, false)

private fun recycleBitmap(bitmap: Bitmap) {
    if (!bitmap.isRecycled) bitmap.recycle()
}

internal fun readerTileSampleSize(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    zoomScale: Float = 1f,
    fillWidth: Boolean = false,
    maxUpscaleFraction: Float = READER_PREVIEW_MAX_UPSCALE_FRACTION
): Int {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1
    val fitScale = if (fillWidth) {
        viewportWidth / imageWidth.toFloat()
    } else {
        minOf(viewportWidth / imageWidth.toFloat(), viewportHeight / imageHeight.toFloat())
    }.coerceAtLeast(0.0001f)
    val renderedPixelScale = fitScale * zoomScale.coerceAtLeast(1f)
    val allowedDecodedPixelScale = 1f + maxUpscaleFraction.coerceIn(0f, 1f)
    var sampleSize = 1
    while (sampleSize * 2f * renderedPixelScale <= allowedDecodedPixelScale && sampleSize < 16) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun readerTileSampleSizeForCache(
    imageWidth: Int,
    imageHeight: Int,
    visibleLeft: Int,
    visibleTop: Int,
    visibleRight: Int,
    visibleBottom: Int,
    initialSampleSize: Int,
    maxCacheBytes: Long,
    tileSourceSize: Int = READER_TILE_SOURCE_SIZE
): Int {
    if (
        imageWidth <= 0 || imageHeight <= 0 ||
        visibleLeft >= visibleRight || visibleTop >= visibleBottom
    ) return initialSampleSize.coerceAtLeast(1)
    var sampleSize = initialSampleSize.coerceAtLeast(1)
    while (true) {
        val tileSize = tileSourceSize.coerceAtLeast(1) * sampleSize
        val firstColumn = (visibleLeft.coerceAtLeast(0) / tileSize).coerceAtLeast(0)
        val lastColumn = ((visibleRight.coerceAtMost(imageWidth) - 1) / tileSize).coerceAtLeast(firstColumn)
        val firstRow = (visibleTop.coerceAtLeast(0) / tileSize).coerceAtLeast(0)
        val lastRow = ((visibleBottom.coerceAtMost(imageHeight) - 1) / tileSize).coerceAtLeast(firstRow)
        val workingSetBytes = readerVisibleTileWorkingSetBytes(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            firstColumn = firstColumn,
            lastColumn = lastColumn,
            firstRow = firstRow,
            lastRow = lastRow,
            sampleSize = sampleSize,
            tileSourceSize = tileSourceSize
        )
        if (workingSetBytes <= maxCacheBytes || sampleSize >= 64) return sampleSize
        sampleSize *= 2
    }
}

internal fun shouldDrawReaderTiles(
    zoomScale: Float,
    previewNeedsDetailTiles: Boolean = false
): Boolean =
    previewNeedsDetailTiles || zoomScale >= READER_TILE_ZOOM_THRESHOLD

internal fun readerPreviewSampleSize(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    qualityScale: Float = READER_ADJACENT_PREVIEW_QUALITY_SCALE,
    maxUpscaleFraction: Float = READER_PREVIEW_MAX_UPSCALE_FRACTION
): Int {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1
    val fitScale = if (fillWidth) {
        viewportWidth / imageWidth.toFloat()
    } else {
        minOf(viewportWidth / imageWidth.toFloat(), viewportHeight / imageHeight.toFloat())
    }.coerceAtLeast(0.0001f)
    val normalizedQualityScale = qualityScale.coerceAtLeast(1f)
    val normalizedUpscaleFraction = maxUpscaleFraction.coerceIn(0f, 1f)
    val targetWidth = (imageWidth * fitScale * normalizedQualityScale).toInt().coerceAtLeast(1)
    val targetHeight = (imageHeight * fitScale * normalizedQualityScale).toInt().coerceAtLeast(1)
    val minimumDecodedWidth = (targetWidth / (1f + normalizedUpscaleFraction)).toInt().coerceAtLeast(1)
    val minimumDecodedHeight = (targetHeight / (1f + normalizedUpscaleFraction)).toInt().coerceAtLeast(1)
    var sampleSize = 1
    while (
        imageWidth / (sampleSize * 2) >= minimumDecodedWidth &&
        imageHeight / (sampleSize * 2) >= minimumDecodedHeight &&
        sampleSize < 32
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun readerPreviewBitmapMeetsDisplayTarget(
    bitmapWidth: Int,
    bitmapHeight: Int,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    maxUpscaleFraction: Float = READER_PREVIEW_MAX_UPSCALE_FRACTION
): Boolean {
    if (
        bitmapWidth <= 0 || bitmapHeight <= 0 ||
        imageWidth <= 0 || imageHeight <= 0 ||
        viewportWidth <= 0 || viewportHeight <= 0
    ) return false
    val fitScale = if (fillWidth) {
        viewportWidth / imageWidth.toFloat()
    } else {
        minOf(viewportWidth / imageWidth.toFloat(), viewportHeight / imageHeight.toFloat())
    }.coerceAtLeast(0.0001f)
    val allowedUpscale = 1f + maxUpscaleFraction.coerceIn(0f, 1f)
    val minimumWidth = imageWidth * fitScale / allowedUpscale
    val minimumHeight = imageHeight * fitScale / allowedUpscale
    return bitmapWidth >= minimumWidth && bitmapHeight >= minimumHeight
}

internal data class ReaderPreviewDecodePlan(
    val sampleSize: Int,
    val qualitySampleSize: Int,
    val displaySampleSize: Int,
    val meetsQualityTarget: Boolean,
    val meetsDisplayTarget: Boolean
)

internal fun readerPreviewDecodePlan(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean,
    qualityScale: Float,
    maxDecodedBytes: Long
): ReaderPreviewDecodePlan {
    val qualitySampleSize = readerPreviewSampleSize(
        imageWidth,
        imageHeight,
        viewportWidth,
        viewportHeight,
        fillWidth,
        qualityScale
    )
    val displaySampleSize = readerPreviewSampleSize(
        imageWidth,
        imageHeight,
        viewportWidth,
        viewportHeight,
        fillWidth,
        READER_ADJACENT_PREVIEW_QUALITY_SCALE
    )
    var sampleSize = qualitySampleSize
    while (readerSampledBitmapBytes(imageWidth, imageHeight, sampleSize) > maxDecodedBytes && sampleSize < 32) {
        sampleSize *= 2
    }
    return ReaderPreviewDecodePlan(
        sampleSize = sampleSize,
        qualitySampleSize = qualitySampleSize,
        displaySampleSize = displaySampleSize,
        meetsQualityTarget = sampleSize <= qualitySampleSize,
        meetsDisplayTarget = sampleSize <= displaySampleSize
    )
}

private fun readerSampledBitmapBytes(imageWidth: Int, imageHeight: Int, sampleSize: Int): Long {
    val normalizedSample = sampleSize.coerceAtLeast(1)
    val sampledWidth = (imageWidth + normalizedSample - 1L) / normalizedSample
    val sampledHeight = (imageHeight + normalizedSample - 1L) / normalizedSample
    return sampledWidth * sampledHeight * 4L
}

internal data class ReaderRenderMemoryBudget(
    val hardLimitBytes: Long,
    val currentPreviewBytes: Long,
    val adjacentPreviewBytes: Long,
    val activeTileBytes: Long
)

internal fun readerVisibleTileWorkingSetBytes(
    imageWidth: Int,
    imageHeight: Int,
    firstColumn: Int,
    lastColumn: Int,
    firstRow: Int,
    lastRow: Int,
    sampleSize: Int,
    tileSourceSize: Int = READER_TILE_SOURCE_SIZE
): Long {
    if (
        imageWidth <= 0 || imageHeight <= 0 ||
        firstColumn < 0 || lastColumn < firstColumn ||
        firstRow < 0 || lastRow < firstRow
    ) return 0L
    val normalizedSample = sampleSize.coerceAtLeast(1)
    val sourceTileSize = tileSourceSize.coerceAtLeast(1) * normalizedSample
    var bytes = 0L
    for (row in firstRow..lastRow) {
        val top = row * sourceTileSize
        val bottom = minOf(top + sourceTileSize, imageHeight)
        if (top >= bottom) continue
        val decodedHeight = (bottom - top + normalizedSample - 1) / normalizedSample
        for (column in firstColumn..lastColumn) {
            val left = column * sourceTileSize
            val right = minOf(left + sourceTileSize, imageWidth)
            if (left >= right) continue
            val decodedWidth = (right - left + normalizedSample - 1) / normalizedSample
            bytes += decodedWidth.toLong() * decodedHeight.toLong() * 4L
        }
    }
    return bytes
}

internal fun readerActiveTileCacheBytes(
    visibleTileBytes: Long,
    budget: ReaderRenderMemoryBudget = readerRenderMemoryBudget()
): Long {
    val maximum = (budget.hardLimitBytes - budget.currentPreviewBytes - budget.adjacentPreviewBytes)
        .minus(budget.adjacentPreviewBytes)
        .coerceAtLeast(budget.activeTileBytes)
    return visibleTileBytes.coerceIn(budget.activeTileBytes, maximum)
}

internal fun readerRenderMemoryBudget(
    maxMemory: Long = Runtime.getRuntime().maxMemory()
): ReaderRenderMemoryBudget {
    val megabyte = 1024L * 1024L
    val hardLimit = ((maxMemory * 28L) / 100L).coerceIn(24L * megabyte, 96L * megabyte)
    val activeTiles = ((hardLimit * 22L) / 100L).coerceIn(6L * megabyte, 20L * megabyte)
    val currentPreview = ((hardLimit * 40L) / 100L).coerceIn(8L * megabyte, 32L * megabyte)
    val adjacentPreview = ((hardLimit - activeTiles - currentPreview) / 2L)
        .coerceIn(4L * megabyte, 16L * megabyte)
    return ReaderRenderMemoryBudget(
        hardLimitBytes = hardLimit,
        currentPreviewBytes = currentPreview,
        adjacentPreviewBytes = adjacentPreview,
        activeTileBytes = activeTiles
    )
}

private fun readerPreviewMaxDecodedBytes(qualityScale: Float): Long {
    val budget = readerRenderMemoryBudget()
    return if (qualityScale > READER_ADJACENT_PREVIEW_QUALITY_SCALE) {
        budget.currentPreviewBytes
    } else {
        budget.adjacentPreviewBytes
    }
}

internal data class ReaderVisibleBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal fun readerZoomVisibleBounds(
    viewportWidth: Int,
    viewportHeight: Int,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float
): ReaderVisibleBounds {
    val bounds = FloatArray(4)
    fillReaderZoomVisibleBounds(
        viewportWidth,
        viewportHeight,
        zoomScale,
        zoomOffsetX,
        zoomOffsetY,
        bounds
    )
    return ReaderVisibleBounds(bounds[0], bounds[1], bounds[2], bounds[3])
}

private fun fillReaderZoomVisibleBounds(
    viewportWidth: Int,
    viewportHeight: Int,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float,
    output: FloatArray
) {
    val scale = zoomScale.coerceAtLeast(1f)
    val centerX = viewportWidth / 2f
    val centerY = viewportHeight / 2f
    output[0] = ((-centerX - zoomOffsetX) / scale + centerX).coerceIn(0f, viewportWidth.toFloat())
    output[1] = ((-centerY - zoomOffsetY) / scale + centerY).coerceIn(0f, viewportHeight.toFloat())
    output[2] = ((centerX - zoomOffsetX) / scale + centerX).coerceIn(0f, viewportWidth.toFloat())
    output[3] = ((centerY - zoomOffsetY) / scale + centerY).coerceIn(0f, viewportHeight.toFloat())
}

private const val READER_TILE_SOURCE_SIZE = 512
private const val READER_TILE_ZOOM_THRESHOLD = 1.05f
private const val READER_TILE_DECODE_ATTEMPTS = 2

private val readerPreviewBitmapCache = object : LruCache<String, Bitmap>(readerPreviewCacheBytes()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun invalidateReaderPreviewCache(file: File) {
    val prefix = "${file.absolutePath}:"
    readerPreviewBitmapCache.snapshot().keys
        .filter { key -> key.startsWith(prefix) }
        .forEach(readerPreviewBitmapCache::remove)
}

private fun readerPreviewCacheBytes(): Int {
    return readerPreviewCacheBytes(Runtime.getRuntime().maxMemory()).toInt()
}

internal fun readerPreviewCacheBytes(maxMemory: Long): Long {
    val budget = readerRenderMemoryBudget(maxMemory)
    return budget.currentPreviewBytes + budget.adjacentPreviewBytes * 2L
}
