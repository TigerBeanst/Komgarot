package fail.tiger.komgarot.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    retryKey: Int = 0,
    fillWidth: Boolean = false,
    zoomScale: Float = 1f,
    progressListener: ImageDownloadProgressListener? = null,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
    onLoadStart: () -> Unit = {},
    onLoadComplete: () -> Unit = {},
    onImageReady: () -> Unit = {}
) {
    val context = LocalContext.current
    var cachedFile by remember(url, seriesId, bookId, retryKey) {
        mutableStateOf(ReaderPageCache.cachedFile(context, seriesId, bookId, url))
    }
    var failed by remember(url, seriesId, bookId, retryKey) { mutableStateOf(false) }

    LaunchedEffect(context, url, seriesId, bookId, retryKey) {
        failed = false
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
            file != null -> AndroidView(
                factory = { viewContext -> ReaderTiledImageView(viewContext) },
                update = { view ->
                    view.onPreviewReady = {
                        if (!previewReady) {
                            previewReady = true
                            onImageReady()
                        }
                    }
                    view.setImageFile(file, fillWidth, zoomScale)
                },
                modifier = Modifier.fillMaxSize()
            )
            failed -> errorContent()
            else -> loadingContent()
        }
        if (file != null && !previewReady) loadingContent()
    }
}

internal suspend fun ensureReaderPageFileCached(
    context: Context,
    url: String,
    seriesId: String,
    bookId: String,
    progressListener: ImageDownloadProgressListener? = null
): File? = withContext(Dispatchers.IO) {
    ReaderPageCache.cachedFile(context, seriesId, bookId, url)?.let { return@withContext it }

    val app = context.applicationContext as? KomgarotApp ?: return@withContext null
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
                return@withContext null
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

class ReaderTiledImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val tileDecodeExecutor = Executors.newSingleThreadExecutor()
    private val pendingTileKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val decoderLock = Any()
    private var imageFile: File? = null
    private var decoder: BitmapRegionDecoder? = null
    private var previewBitmap: Bitmap? = null
    private var previewKey: String = ""
    private var pendingPreviewKey: String = ""
    private var decoderGeneration = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var fillWidth = false
    private var zoomScale = 1f
    var onPreviewReady: (() -> Unit)? = null
    private val tileCache = object : LruCache<String, Bitmap>(readerTileCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (newValue == null) recycleBitmap(oldValue)
        }
    }

    fun setImageFile(file: File, fillWidth: Boolean, zoomScale: Float) {
        val sameFile = imageFile?.absolutePath == file.absolutePath
        val normalizedZoomScale = zoomScale.coerceAtLeast(1f)
        if (sameFile && this.fillWidth == fillWidth && this.zoomScale == normalizedZoomScale) return
        this.fillWidth = fillWidth
        this.zoomScale = normalizedZoomScale
        if (!sameFile) {
            releaseDecoder()
            imageFile = file
            openDecoderAsync(file, decoderGeneration)
        }
        invalidate()
    }

    private fun openDecoderAsync(file: File, generation: Int) {
        val viewportWidth = width
        val viewportHeight = height
        val previewFillWidth = fillWidth
        tileDecodeExecutor.execute {
            val openedDecoder = newReaderBitmapRegionDecoder(file)
            val openedWidth = openedDecoder?.width ?: 0
            val openedHeight = openedDecoder?.height ?: 0
            val openedPreviewKey = readerPreviewKey(openedWidth, openedHeight, viewportWidth, viewportHeight, previewFillWidth)
            val openedPreview = decodePreviewBitmap(
                decoder = openedDecoder,
                imageWidth = openedWidth,
                imageHeight = openedHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                fillWidth = previewFillWidth
            )
            post {
                if (generation != decoderGeneration || imageFile?.absolutePath != file.absolutePath) {
                    openedDecoder?.recycle()
                    openedPreview?.let(::recycleBitmap)
                    return@post
                }
                synchronized(decoderLock) {
                    decoder?.recycle()
                    decoder = openedDecoder
                    imageWidth = openedWidth
                    imageHeight = openedHeight
                }
                if (openedPreview != null) {
                    val oldPreview = previewBitmap
                    previewBitmap = openedPreview
                    previewKey = openedPreviewKey
                    if (oldPreview != null && oldPreview !== openedPreview) recycleBitmap(oldPreview)
                    onPreviewReady?.invoke()
                }
                invalidate()
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) return
        val key = readerPreviewKey(imageWidth, imageHeight, width, height, fillWidth)
        if (previewBitmap == null || previewKey != key) {
            requestPreviewDecode(readerPreviewKey(imageWidth, imageHeight, width, height, fillWidth))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val regionDecoder = decoder ?: return
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
        val fullDestinationRect = Rect(
            offsetX.toInt(),
            offsetY.toInt(),
            (offsetX + renderedWidth).toInt(),
            (offsetY + renderedHeight).toInt()
        )
        val desiredPreviewKey = readerPreviewKey(imageWidth, imageHeight, width, height, fillWidth)
        if (previewBitmap == null || previewKey != desiredPreviewKey) {
            requestPreviewDecode(desiredPreviewKey)
        }
        drawPreviewBitmap(canvas, fullDestinationRect)
        if (!shouldDrawReaderTiles(zoomScale)) return

        val sampleSize = readerTileSampleSize(imageWidth, imageHeight, width, height, zoomScale)
        val tileSize = READER_TILE_SOURCE_SIZE * sampleSize
        val clipBounds = canvas.clipBounds
        val visibleLeft = floor(((clipBounds.left - offsetX) / fitScale).coerceIn(0f, imageWidth.toFloat())).toInt()
        val visibleTop = floor(((clipBounds.top - offsetY) / fitScale).coerceIn(0f, imageHeight.toFloat())).toInt()
        val visibleRight = ceil(((clipBounds.right - offsetX) / fitScale).coerceIn(0f, imageWidth.toFloat())).toInt()
        val visibleBottom = ceil(((clipBounds.bottom - offsetY) / fitScale).coerceIn(0f, imageHeight.toFloat())).toInt()
        if (visibleLeft >= visibleRight || visibleTop >= visibleBottom) return

        val firstColumn = (visibleLeft / tileSize).coerceAtLeast(0)
        val lastColumn = ((visibleRight - 1) / tileSize).coerceAtLeast(firstColumn)
        val firstRow = (visibleTop / tileSize).coerceAtLeast(0)
        val lastRow = ((visibleBottom - 1) / tileSize).coerceAtLeast(firstRow)

        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val left = column * tileSize
                val top = row * tileSize
                val sourceRect = Rect(
                    left,
                    top,
                    minOf(left + tileSize, imageWidth),
                    minOf(top + tileSize, imageHeight)
                )
                val key = readerTileKey(sourceRect, sampleSize)
                val bitmap = tileCache.get(key)
                if (bitmap == null) {
                    requestTileDecode(key, sourceRect, sampleSize)
                    continue
                }
                val destinationRect = Rect(
                    (offsetX + sourceRect.left * fitScale).toInt(),
                    (offsetY + sourceRect.top * fitScale).toInt(),
                    (offsetX + sourceRect.right * fitScale).toInt(),
                    (offsetY + sourceRect.bottom * fitScale).toInt()
                )
                canvas.drawBitmap(bitmap, null, destinationRect, paint)
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
        tileDecodeExecutor.execute {
            val bitmap = synchronized(decoderLock) {
                val activeDecoder = decoder
                if (activeDecoder == null || activeDecoder.isRecycled || generation != decoderGeneration) {
                    null
                } else {
                    decodePreviewBitmap(
                        decoder = activeDecoder,
                        imageWidth = previewImageWidth,
                        imageHeight = previewImageHeight,
                        viewportWidth = previewViewportWidth,
                        viewportHeight = previewViewportHeight,
                        fillWidth = previewFillWidth
                    )
                }
            }
            post {
                if (pendingPreviewKey == key) pendingPreviewKey = ""
                if (bitmap == null) return@post
                if (generation != decoderGeneration) {
                    recycleBitmap(bitmap)
                    return@post
                }
                val oldPreview = previewBitmap
                previewBitmap = bitmap
                previewKey = key
                if (oldPreview != null && oldPreview !== bitmap) recycleBitmap(oldPreview)
                onPreviewReady?.invoke()
                invalidate()
            }
        }
    }

    private fun requestTileDecode(key: String, rect: Rect, sampleSize: Int) {
        if (tileCache.get(key) != null) return
        if (!pendingTileKeys.add(key)) return
        val generation = decoderGeneration
        val decodeRect = Rect(rect)
        tileDecodeExecutor.execute {
            val bitmap = synchronized(decoderLock) {
                val activeDecoder = decoder
                if (activeDecoder == null || activeDecoder.isRecycled || generation != decoderGeneration) {
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
            post {
                pendingTileKeys.remove(key)
                if (bitmap == null) {
                    return@post
                }
                if (generation != decoderGeneration) {
                    recycleBitmap(bitmap)
                } else {
                    tileCache.put(key, bitmap)
                    invalidate()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseDecoder()
        tileDecodeExecutor.shutdownNow()
    }

    private fun releaseDecoder() {
        decoderGeneration += 1
        pendingTileKeys.clear()
        pendingPreviewKey = ""
        previewKey = ""
        previewBitmap?.let(::recycleBitmap)
        previewBitmap = null
        tileCache.evictAll()
        synchronized(decoderLock) {
            decoder?.recycle()
            decoder = null
        }
        imageWidth = 0
        imageHeight = 0
    }
}

private fun readerTileKey(rect: Rect, sampleSize: Int): String =
    "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}:$sampleSize"

private fun readerPreviewKey(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean
): String =
    "$imageWidth:$imageHeight:$viewportWidth:$viewportHeight:$fillWidth"

private fun decodePreviewBitmap(
    decoder: BitmapRegionDecoder?,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean
): Bitmap? {
    if (decoder == null || decoder.isRecycled || imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return null
    }
    return runCatching {
        decoder.decodeRegion(
            Rect(0, 0, imageWidth, imageHeight),
            BitmapFactory.Options().apply {
                inSampleSize = readerPreviewSampleSize(imageWidth, imageHeight, viewportWidth, viewportHeight, fillWidth)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()
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
    zoomScale: Float = 1f
): Int {
    val maxImageEdge = max(imageWidth, imageHeight).coerceAtLeast(1)
    val maxViewportEdge = (max(viewportWidth, viewportHeight).coerceAtLeast(1) * zoomScale.coerceAtLeast(1f)).toInt()
    var sampleSize = 1
    while (maxImageEdge / sampleSize > maxViewportEdge * 2 && sampleSize < 16) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun shouldDrawReaderTiles(zoomScale: Float): Boolean =
    zoomScale >= READER_TILE_ZOOM_THRESHOLD

internal fun readerPreviewSampleSize(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillWidth: Boolean
): Int {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1
    val fitScale = if (fillWidth) {
        viewportWidth / imageWidth.toFloat()
    } else {
        minOf(viewportWidth / imageWidth.toFloat(), viewportHeight / imageHeight.toFloat())
    }.coerceAtLeast(0.0001f)
    val targetWidth = (imageWidth * fitScale).toInt().coerceAtLeast(1)
    val targetHeight = (imageHeight * fitScale).toInt().coerceAtLeast(1)
    var sampleSize = 1
    while (
        imageWidth / (sampleSize * 2) >= targetWidth &&
        imageHeight / (sampleSize * 2) >= targetHeight &&
        sampleSize < 32
    ) {
        sampleSize *= 2
    }
    while (
        imageWidth.toLong() / sampleSize * (imageHeight.toLong() / sampleSize) * 4L > READER_PREVIEW_MAX_BYTES &&
        sampleSize < 32
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun readerTileCacheBytes(): Int {
    val maxMemory = Runtime.getRuntime().maxMemory()
    val megabyte = 1024 * 1024
    return when {
        maxMemory < 256L * megabyte -> 12 * megabyte
        maxMemory < 512L * megabyte -> 20 * megabyte
        else -> 32 * megabyte
    }
}

private const val READER_TILE_SOURCE_SIZE = 1024
private const val READER_TILE_ZOOM_THRESHOLD = 1.4f
private const val READER_PREVIEW_MAX_BYTES = 48L * 1024L * 1024L
