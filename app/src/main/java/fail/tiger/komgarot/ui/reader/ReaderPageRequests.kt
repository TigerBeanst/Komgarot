package fail.tiger.komgarot.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.remote.ImageDownloadProgressListener
import fail.tiger.komgarot.data.remote.dto.PageDto
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val LOW_MEMORY_PRELOAD_LIMIT = 2
private const val MID_MEMORY_PRELOAD_LIMIT = 3
private const val HIGH_MEMORY_PRELOAD_LIMIT = 5
private const val CANVAS_SAFE_BITMAP_BYTES = 96L * 1024L * 1024L

internal const val READER_CURRENT_PREVIEW_QUALITY_SCALE = 1.25f
internal const val READER_ADJACENT_PREVIEW_QUALITY_SCALE = 1f
internal const val READER_PREVIEW_MAX_UPSCALE_FRACTION = 0.03f

enum class ReaderPageRenderMode { COIL, TILED }

fun readerPageMemoryCacheKey(
    url: String,
    allowHardware: Boolean,
    originalSize: Boolean,
    cacheVersion: Int = 0,
    pageSegment: ReaderPageSegment = ReaderPageSegment.FULL
): String {
    val versionSegment = if (cacheVersion > 0) ":v$cacheVersion" else ""
    val splitSegment = if (pageSegment == ReaderPageSegment.FULL) "" else ":${pageSegment.name.lowercase()}"
    return "reader-page:${if (originalSize) "original" else "display"}:${if (allowHardware) "hardware" else "software"}$splitSegment$versionSegment:$url"
}

fun readerPageDiskCacheKey(url: String): String = "reader-page:$url"

fun shouldShowReaderPageLoadingPlaceholder(isLocalCacheHit: Boolean, hasPreviousPainter: Boolean): Boolean =
    !isLocalCacheHit && !hasPreviousPainter

fun readerPageRequest(
    context: Context,
    url: String,
    seriesId: String? = null,
    bookId: String? = null,
    cacheVersion: Int = 0,
    allowHardware: Boolean = false,
    originalSize: Boolean = false,
    displayWidthPx: Int = 0,
    displayHeightPx: Int = 0,
    displayQualityScale: Float = 1f,
    displayMaxDecodedBytes: Long = Long.MAX_VALUE,
    retainInMemory: Boolean = false,
    pageSegment: ReaderPageSegment = ReaderPageSegment.FULL,
    retryKey: Int = 0,
    progressListener: ImageDownloadProgressListener? = null,
    listener: ImageRequest.Listener? = null
): ImageRequest {
    val cachedFile = when {
        !seriesId.isNullOrBlank() && !bookId.isNullOrBlank() -> ReaderPageCache.cachedFile(context, seriesId, bookId, url)
        !bookId.isNullOrBlank() -> ReaderPageCache.cachedFile(context, bookId, url)
        else -> ReaderPageCache.cachedFile(context, url)
    }
    val data = cachedFile ?: url
    val memoryKey = readerPageMemoryCacheKey(url, allowHardware, originalSize, cacheVersion, pageSegment)
    val builder = ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey(memoryKey)
        .placeholderMemoryCacheKey(memoryKey)
        .diskCacheKey(readerPageDiskCacheKey(url))
        .setHeader("Accept", "image/*,*/*;q=0.8")
        .setParameter("reader_retry_key", retryKey, memoryCacheKey = null)
        .memoryCachePolicy(if (retainInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(if (cachedFile == null) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .allowHardware(allowHardware)
        .allowRgb565(false)
        .apply {
            if (pageSegment != ReaderPageSegment.FULL) {
                transformations(ReaderPageHalfTransformation(pageSegment))
            }
            if (originalSize) {
                size(Size.ORIGINAL)
            } else if (displayWidthPx > 0 && displayHeightPx > 0) {
                val displayDecodeSize = readerDisplayDecodeSize(
                    layoutWidth = displayWidthPx,
                    layoutHeight = displayHeightPx,
                    qualityScale = displayQualityScale,
                    maxDecodedBytes = displayMaxDecodedBytes
                )
                size(displayDecodeSize.width, displayDecodeSize.height)
            }
            if (listener != null) {
                this.listener(listener)
            }
        }
    if (cachedFile == null) {
        builder.tag(
            ReaderPageCache.Entry::class.java,
            when {
                !seriesId.isNullOrBlank() && !bookId.isNullOrBlank() -> ReaderPageCache.entry(context, seriesId, bookId, url)
                !bookId.isNullOrBlank() -> ReaderPageCache.entry(context, bookId, url)
                else -> ReaderPageCache.entry(context, url)
            }
        )
        if (progressListener != null) {
            builder.tag(ImageDownloadProgressListener::class.java, progressListener)
        }
    }
    return builder.build()
}

private class ReaderPageHalfTransformation(
    private val segment: ReaderPageSegment
) : Transformation {
    override val cacheKey: String = "${javaClass.name}:${segment.name}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (segment == ReaderPageSegment.FULL || input.width < 2 || input.height < 1) return input
        val splitX = input.width / 2
        val source = when (segment) {
            ReaderPageSegment.LEFT_HALF -> Rect(0, 0, splitX, input.height)
            ReaderPageSegment.RIGHT_HALF -> Rect(splitX, 0, input.width, input.height)
            ReaderPageSegment.FULL -> return input
        }
        val output = Bitmap.createBitmap(
            source.width().coerceAtLeast(1),
            source.height().coerceAtLeast(1),
            input.config ?: Bitmap.Config.ARGB_8888
        )
        Canvas(output).drawBitmap(
            input,
            source,
            Rect(0, 0, output.width, output.height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    override fun equals(other: Any?): Boolean =
        other is ReaderPageHalfTransformation && other.segment == segment

    override fun hashCode(): Int = 31 * javaClass.hashCode() + segment.hashCode()
}

fun readerMemoryAwarePreloadPages(
    requestedPreloadPages: Int,
    maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()
): Int {
    if (requestedPreloadPages <= 0) return 0
    val memoryLimit = when {
        maxMemoryBytes < 256L * 1024L * 1024L -> LOW_MEMORY_PRELOAD_LIMIT
        maxMemoryBytes < 512L * 1024L * 1024L -> MID_MEMORY_PRELOAD_LIMIT
        else -> HIGH_MEMORY_PRELOAD_LIMIT
    }
    return requestedPreloadPages.coerceAtMost(memoryLimit)
}

fun readerNextQuickPreloadPages(current: Int): Int = when {
    current < 2 -> 2
    current < 5 -> 5
    current < 8 -> 8
    else -> 0
}

fun readerPagerBeyondViewportPageCount(einkMode: Boolean): Int = if (einkMode) 1 else 0

fun readerPagerBeyondViewportPageCount(einkMode: Boolean, hasTiledPages: Boolean): Int =
    if (einkMode || hasTiledPages) 1 else 0

fun readerPagerBeyondViewportPageCount(
    einkMode: Boolean,
    pagerPages: List<ReaderPagerPage>,
    currentPagerIndex: Int,
    pageInfo: (Int) -> PageDto?
): Int {
    if (einkMode) return 1
    val from = (currentPagerIndex - 1).coerceAtLeast(0)
    val to = (currentPagerIndex + 1).coerceAtMost(pagerPages.lastIndex)
    return if ((from..to).any { index ->
            val page = pagerPages.getOrNull(index) as? ReaderPagerPage.Actual
            page != null && readerPageNeedsStableAdjacentComposition(pageInfo(page.pageIndex))
        }
    ) {
        1
    } else {
        0
    }
}

fun readerShouldRetainPageInMemory(einkMode: Boolean, renderMode: ReaderPageRenderMode): Boolean =
    renderMode == ReaderPageRenderMode.COIL

@Suppress("UNUSED_PARAMETER")
fun readerPageRenderMode(
    page: PageDto,
    maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()
): ReaderPageRenderMode = ReaderPageRenderMode.COIL

@Suppress("UNUSED_PARAMETER")
fun readerPageNeedsStableAdjacentComposition(page: PageDto?): Boolean = false

fun readerBitmapExceedsCanvasSafeSize(
    width: Int,
    height: Int,
    bytesPerPixel: Int = 4,
    bitmapByteCount: Long? = null,
    maxSafeBytes: Long = CANVAS_SAFE_BITMAP_BYTES
): Boolean {
    bitmapByteCount?.takeIf { it > 0L }?.let { return it >= maxSafeBytes }
    if (width <= 0 || height <= 0 || bytesPerPixel <= 0) return true
    return width.toLong() * height.toLong() * bytesPerPixel.toLong() >= maxSafeBytes
}

data class ReaderDisplayDecodeSize(val width: Int, val height: Int)

fun readerImageAspectRatio(width: Int, height: Int): Float? =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null

fun readerDisplayDecodeSize(
    layoutWidth: Int,
    layoutHeight: Int,
    qualityScale: Float,
    maxDecodedBytes: Long
): ReaderDisplayDecodeSize {
    val scale = qualityScale.coerceAtLeast(1f)
    val targetWidth = (layoutWidth.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (layoutHeight.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
    val targetBytes = targetWidth.toLong() * targetHeight.toLong() * 4L
    if (targetBytes <= maxDecodedBytes.coerceAtLeast(4L)) {
        return ReaderDisplayDecodeSize(targetWidth, targetHeight)
    }
    val reduction = sqrt(maxDecodedBytes.coerceAtLeast(4L).toDouble() / targetBytes.toDouble())
    return ReaderDisplayDecodeSize(
        width = floor(targetWidth * reduction).toInt().coerceAtLeast(1),
        height = floor(targetHeight * reduction).toInt().coerceAtLeast(1)
    )
}

fun readerDrawableExceedsCanvasSafeSize(
    drawable: Drawable?,
    maxSafeBytes: Long = readerCanvasSafeBitmapBytes(Runtime.getRuntime().maxMemory())
): Boolean {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap
    if (bitmap?.isRecycled == true) return true
    return readerBitmapExceedsCanvasSafeSize(
        width = drawable?.intrinsicWidth ?: 0,
        height = drawable?.intrinsicHeight ?: 0,
        bitmapByteCount = bitmap?.byteCount?.toLong(),
        maxSafeBytes = maxSafeBytes
    )
}

internal fun readerCanvasSafeBitmapBytes(maxMemoryBytes: Long): Long =
    (maxMemoryBytes / 4L).coerceAtMost(CANVAS_SAFE_BITMAP_BYTES)

fun readerPagerActualPreloadRange(
    pagerPages: List<ReaderPagerPage>,
    currentPagerIndex: Int,
    preloadPages: Int,
    direction: Int = 1
): List<Int> {
    val normalizedDirection = if (direction < 0) -1 else 1
    val forward = (1..preloadPages.coerceAtLeast(0))
        .map { distance -> currentPagerIndex + normalizedDirection * distance }
    val oppositeNeighbor = currentPagerIndex - normalizedDirection

    return (forward + oppositeNeighbor)
        .distinct()
        .mapNotNull { index -> (pagerPages.getOrNull(index) as? ReaderPagerPage.Actual)?.pageIndex }
        .distinct()
}

fun readerPagerPreloadDirection(
    pagerPages: List<ReaderPagerPage>,
    currentPagerIndex: Int,
    targetPagerIndex: Int,
    fallbackDirection: Int
): Int {
    val currentPage = (pagerPages.getOrNull(currentPagerIndex) as? ReaderPagerPage.Actual)?.pageIndex
    val targetPage = (pagerPages.getOrNull(targetPagerIndex) as? ReaderPagerPage.Actual)?.pageIndex
    val delta = if (currentPage != null && targetPage != null) targetPage - currentPage else 0
    return when {
        delta < 0 -> -1
        delta > 0 -> 1
        fallbackDirection < 0 -> -1
        else -> 1
    }
}
