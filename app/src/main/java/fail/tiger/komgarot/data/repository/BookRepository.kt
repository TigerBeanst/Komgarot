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
                condition = mapOf(
                    "operator" to "BOOK",
                    "seriesId" to isCondition(seriesId)
                )
            ),
            page = page
        )

    suspend fun getLatestBooks(size: Int = 12): List<BookDto> =
        api.getLatestBooks(size = size).content

    suspend fun getBooksOnDeck(size: Int = 12): List<BookDto> =
        api.getBooksOnDeck(size = size).content

    suspend fun getBookById(id: String): Result<BookDto> = runCatching { api.getBookById(id) }

    suspend fun getNextBook(id: String): Result<BookDto> = runCatching { api.getNextBook(id) }

    suspend fun getPreviousBook(id: String): Result<BookDto> = runCatching { api.getPreviousBook(id) }

    suspend fun getPages(bookId: String): List<PageDto> = api.getBookPages(bookId)

    suspend fun getSeriesMetadata(id: String): SeriesMetadataDto = api.getSeriesById(id).metadata

    suspend fun getBookMetadata(id: String): BookMetadataDto = api.getBookById(id).metadata

    suspend fun getLanguages(): List<String> = api.getLanguages()

    suspend fun updateSeriesMetadata(id: String, metadata: SeriesMetadataUpdateDto) =
        api.updateSeriesMetadata(id, metadata)

    suspend fun updateSeriesLanguage(id: String, metadata: SeriesLanguageUpdateDto) =
        api.updateSeriesLanguage(id, metadata)

    suspend fun updateBookMetadata(id: String, metadata: BookMetadataUpdateDto) =
        api.updateBookMetadata(id, metadata)

    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean = false) =
        api.updateReadProgress(bookId, ReadProgressUpdateDto(page, completed))

    suspend fun deleteBookReadProgress(bookId: String) = api.deleteBookReadProgress(bookId)

    suspend fun markSeriesRead(seriesId: String) = api.markSeriesRead(seriesId)

    suspend fun markSeriesUnread(seriesId: String) = api.markSeriesUnread(seriesId)

    suspend fun refreshBookMetadata(bookId: String) = api.refreshBookMetadata(bookId)

    suspend fun refreshSeriesMetadata(seriesId: String) = api.refreshSeriesMetadata(seriesId)

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
