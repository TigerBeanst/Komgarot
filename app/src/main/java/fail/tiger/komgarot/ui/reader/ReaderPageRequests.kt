package fail.tiger.komgarot.ui.reader

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.remote.ImageDownloadProgressListener
import fail.tiger.komgarot.data.remote.dto.PageDto

private const val LOW_MEMORY_PRELOAD_LIMIT = 2
private const val MID_MEMORY_PRELOAD_LIMIT = 3
private const val HIGH_MEMORY_PRELOAD_LIMIT = 5
private const val CANVAS_SAFE_BITMAP_BYTES = 96L * 1024L * 1024L

enum class ReaderPageRenderMode { COIL, TILED }

fun readerPageMemoryCacheKey(
    url: String,
    allowHardware: Boolean,
    originalSize: Boolean,
    cacheVersion: Int = 0
): String {
    val versionSegment = if (cacheVersion > 0) ":v$cacheVersion" else ""
    return "reader-page:${if (originalSize) "original" else "display"}:${if (allowHardware) "hardware" else "software"}$versionSegment:$url"
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
    retainInMemory: Boolean = false,
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
    val memoryKey = readerPageMemoryCacheKey(url, allowHardware, originalSize, cacheVersion)
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
            size(Size.ORIGINAL)
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
            page != null && pageInfo(page.pageIndex)?.let(::readerPageRenderMode) == ReaderPageRenderMode.TILED
        }
    ) {
        1
    } else {
        0
    }
}

fun readerShouldRetainPageInMemory(einkMode: Boolean, renderMode: ReaderPageRenderMode): Boolean =
    renderMode == ReaderPageRenderMode.COIL

fun readerPageRenderMode(page: PageDto): ReaderPageRenderMode {
    val width = page.width.coerceAtLeast(0)
    val height = page.height.coerceAtLeast(0)
    if (width == 0 || height == 0) return ReaderPageRenderMode.COIL
    return if (readerBitmapExceedsCanvasSafeSize(width, height)) {
        ReaderPageRenderMode.TILED
    } else {
        ReaderPageRenderMode.COIL
    }
}

fun readerBitmapExceedsCanvasSafeSize(width: Int, height: Int, bytesPerPixel: Int = 4): Boolean {
    if (width <= 0 || height <= 0 || bytesPerPixel <= 0) return true
    return width.toLong() * height.toLong() * bytesPerPixel.toLong() >= CANVAS_SAFE_BITMAP_BYTES
}

fun readerPagerActualPreloadRange(
    pagerPages: List<ReaderPagerPage>,
    currentPagerIndex: Int,
    preloadPages: Int
): List<Int> {
    val from = (currentPagerIndex - 1).coerceAtLeast(0)
    val to = (currentPagerIndex + preloadPages).coerceAtMost(pagerPages.lastIndex)
    if (from > to) return emptyList()

    return (from..to)
        .filter { it != currentPagerIndex }
        .mapNotNull { index -> (pagerPages.getOrNull(index) as? ReaderPagerPage.Actual)?.pageIndex }
}
