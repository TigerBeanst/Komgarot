package fail.tiger.komgarot.data.local

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.memory.MemoryCache
import fail.tiger.komgarot.ThumbnailVersion

class ImageCacheInvalidator(private val context: Context) {
    fun invalidateBook(bookId: String, seriesId: String? = null) {
        if (bookId.isBlank()) return
        invalidateBookThumbnail(bookId)
        ReaderPageCache.clearBook(context, bookId)
        seriesId?.takeIf { it.isNotBlank() }?.let {
            invalidateSeriesThumbnail(it)
        }
    }

    fun invalidateSeries(seriesId: String, bookIds: Iterable<String>) {
        if (seriesId.isBlank()) return
        invalidateSeriesThumbnail(seriesId)
        ReaderPageCache.clearSeries(context, seriesId)
        bookIds.filter { it.isNotBlank() }.distinct().forEach { bookId ->
            invalidateBook(bookId)
        }
    }

    fun invalidateBookCaches(bookIds: Iterable<String>) {
        bookIds.filter { it.isNotBlank() }.distinct().forEach { bookId ->
            invalidateBook(bookId)
        }
    }

    fun invalidateBookThumbnail(bookId: String) {
        if (bookId.isBlank()) return
        invalidateThumbnail(ThumbnailCacheTarget.Book(bookId))
    }

    fun invalidateSeriesThumbnail(seriesId: String) {
        if (seriesId.isBlank()) return
        invalidateThumbnail(ThumbnailCacheTarget.Series(seriesId))
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun invalidateThumbnail(target: ThumbnailCacheTarget) {
        val key = thumbnailCacheKey(target)
        Coil.imageLoader(context).memoryCache?.remove(MemoryCache.Key(key))
        Coil.imageLoader(context).diskCache?.remove(key)
        ThumbnailVersion.bump(target.id)
    }
}
