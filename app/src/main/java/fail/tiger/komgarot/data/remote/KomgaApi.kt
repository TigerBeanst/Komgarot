package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface KomgaApi {
    @GET("api/v1/libraries")
    suspend fun getLibraries(): List<LibraryDto>

    @POST("api/v1/series/list")
    suspend fun getSeries(
        @Body search: SeriesSearchDto = SeriesSearchDto(),
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("metadata.titleSort,asc")
    ): PagedDto<SeriesDto>

    @GET("api/v1/series/{id}")
    suspend fun getSeriesById(@Path("id") id: String): SeriesDto

    @GET("api/v1/series/latest")
    suspend fun getLatestSeries(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<SeriesDto>

    @GET("api/v1/series/new")
    suspend fun getNewSeries(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<SeriesDto>

    @GET("api/v1/series/updated")
    suspend fun getUpdatedSeries(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<SeriesDto>

    @POST("api/v1/books/list")
    suspend fun getBooks(
        @Body search: BookSearchDto,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("metadata.numberSort,asc")
    ): PagedDto<BookDto>

    @GET("api/v1/books/{id}")
    suspend fun getBookById(@Path("id") id: String): BookDto

    @GET("api/v1/books/latest")
    suspend fun getLatestBooks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<BookDto>

    @GET("api/v1/books/ondeck")
    suspend fun getBooksOnDeck(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<BookDto>

    @GET("api/v1/books/{id}/next")
    suspend fun getNextBook(@Path("id") id: String): BookDto

    @GET("api/v1/books/{id}/previous")
    suspend fun getPreviousBook(@Path("id") id: String): BookDto

    @GET("api/v1/books/{id}/pages")
    suspend fun getBookPages(@Path("id") bookId: String): List<PageDto>

    @PATCH("api/v1/series/{id}/metadata")
    suspend fun updateSeriesMetadata(@Path("id") id: String, @Body metadata: Map<String, @JvmSuppressWildcards Any?>)

    @PATCH("api/v1/books/{id}/metadata")
    suspend fun updateBookMetadata(@Path("id") id: String, @Body metadata: Map<String, @JvmSuppressWildcards Any?>)

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(@Path("bookId") bookId: String, @Body progress: Map<String, @JvmSuppressWildcards Any?>)

    @Multipart
    @POST("api/v1/books/{bookId}/thumbnails")
    suspend fun uploadBookThumbnail(
        @Path("bookId") bookId: String,
        @Part file: MultipartBody.Part,
        @Query("selected") selected: Boolean = true
    )

    @Multipart
    @POST("api/v1/series/{seriesId}/thumbnails")
    suspend fun uploadSeriesThumbnail(
        @Path("seriesId") seriesId: String,
        @Part file: MultipartBody.Part,
        @Query("selected") selected: Boolean = true
    )
}
