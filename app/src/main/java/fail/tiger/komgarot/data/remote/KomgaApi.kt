package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.remote.dto.*
import retrofit2.http.*

interface KomgaApi {
    @GET("api/v1/libraries")
    suspend fun getLibraries(): List<LibraryDto>

    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("library_id") libraryId: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("metadata.titleSort,asc")
    ): PagedDto<SeriesDto>

    @GET("api/v1/series/{id}")
    suspend fun getSeriesById(@Path("id") id: String): SeriesDto

    @GET("api/v1/series/{id}/metadata")
    suspend fun getSeriesMetadata(@Path("id") id: String): SeriesMetadataDto

    @POST("api/v1/books/list")
    suspend fun getBooks(
        @Body search: BookSearchDto,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("metadata.numberSort,asc")
    ): PagedDto<BookDto>

    @GET("api/v1/books/{id}")
    suspend fun getBookById(@Path("id") id: String): BookDto

    @GET("api/v1/books/{id}/pages")
    suspend fun getBookPages(@Path("id") bookId: String): List<PageDto>

    @PATCH("api/v1/series/{id}/metadata")
    suspend fun updateSeriesMetadata(@Path("id") id: String, @Body metadata: Map<String, @JvmSuppressWildcards Any?>)

    @PATCH("api/v1/books/{id}/metadata")
    suspend fun updateBookMetadata(@Path("id") id: String, @Body metadata: Map<String, @JvmSuppressWildcards Any?>)

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(@Path("bookId") bookId: String, @Body progress: Map<String, @JvmSuppressWildcards Any?>)
}
