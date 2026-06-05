package fail.tiger.komgarot.data.local

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import fail.tiger.komgarot.ThumbnailVersion
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.ui.reader.readerPageRequest
import fail.tiger.komgarot.ui.reader.readerPageUrl

data class BookDownloadProgress(val completedPages: Int, val totalPages: Int)

class BookDownloadCache(
    private val context: Context,
    private val repo: BookRepository
) {
    suspend fun cacheBook(
        serverUrl: String,
        bookId: String,
        knownBook: BookDto?,
        onProgress: (BookDownloadProgress) -> Unit
    ): Int {
        val book = knownBook ?: repo.getBookById(bookId).getOrThrow()
        val pages = repo.getPages(bookId)
        val imageLoader = context.imageLoader
        cacheThumbnail(serverUrl, book.id)
        var completed = 0
        onProgress(BookDownloadProgress(completed, pages.size))
        pages.forEach { page ->
            val url = readerPageUrl(serverUrl, book.id, page)
            if (!ReaderPageCache.hasCachedFile(context, book.seriesId, book.id, url)) {
                val request = readerPageRequest(
                    context = context,
                    url = url,
                    seriesId = book.seriesId,
                    bookId = book.id,
                    cacheVersion = ThumbnailVersion.get(book.id),
                    allowHardware = false,
                    originalSize = true
                )
                val result = imageLoader.execute(request)
                if (result is ErrorResult) throw result.throwable
            }
            completed++
            onProgress(BookDownloadProgress(completed, pages.size))
        }
        return pages.size
    }

    suspend fun getProgress(serverUrl: String, book: BookDto): BookDownloadProgress {
        val pages = repo.getPages(book.id)
        val completed = pages.count { page ->
            val url = readerPageUrl(serverUrl, book.id, page)
            ReaderPageCache.hasCachedFile(context, book.seriesId, book.id, url)
        }
        return BookDownloadProgress(completed, pages.size)
    }

    private suspend fun cacheThumbnail(serverUrl: String, bookId: String) {
        val thumbnailVersion = ThumbnailVersion.get(bookId)
        val url = KomgaUrls.bookThumbnail(serverUrl, bookId, thumbnailVersion)
        val key = thumbnailCacheKey(ThumbnailCacheTarget.Book(bookId))
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is ErrorResult) throw result.throwable
    }
}
