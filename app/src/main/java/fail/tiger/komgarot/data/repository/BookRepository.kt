package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class BookRepository(private val api: KomgaApi) {
    suspend fun getBooks(seriesId: String, page: Int): PagedDto<BookDto> =
        api.getBooks(
            search = BookSearchDto(
                condition = BookCondition(
                    seriesId = SeriesIdCondition(value = seriesId)
                )
            ),
            page = page
        )

    suspend fun getBookById(id: String): Result<BookDto> = runCatching { api.getBookById(id) }

    suspend fun getPages(bookId: String): List<PageDto> = api.getBookPages(bookId)

    suspend fun getSeriesMetadata(id: String): SeriesMetadataDto = api.getSeriesMetadata(id)

    suspend fun getBookMetadata(id: String): BookMetadataDto = api.getBookById(id).metadata

    suspend fun updateSeriesMetadata(id: String, metadata: Map<String, Any?>) =
        api.updateSeriesMetadata(id, metadata)

    suspend fun updateBookMetadata(id: String, metadata: Map<String, Any?>) =
        api.updateBookMetadata(id, metadata)

    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean = false) =
        api.updateReadProgress(bookId, mapOf("page" to page, "completed" to completed))

    suspend fun uploadBookThumbnail(bookId: String, imageBytes: ByteArray, mimeType: String) {
        val body = imageBytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", "thumbnail.jpg", body)
        api.uploadBookThumbnail(bookId, part)
    }

    suspend fun uploadSeriesThumbnail(seriesId: String, imageBytes: ByteArray, mimeType: String) {
        val body = imageBytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", "thumbnail.jpg", body)
        api.uploadSeriesThumbnail(seriesId, part)
    }
}
