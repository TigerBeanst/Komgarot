package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface KomgaApi :
    BrowseApi,
    CollectionApi,
    ReadListApi,
    MetadataApi,
    AdminApi,
    UserApi

interface BrowseApi {
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

    @GET("api/v1/series/alphabetical-groups")
    suspend fun getSeriesAlphabeticalGroups(
        @Query("library_id") libraryIds: List<String>? = null
    ): List<GroupCountDto>

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

    @GET("api/v1/authors/names")
    suspend fun getAuthorNames(): List<String>

    @GET("api/v1/authors/roles")
    suspend fun getAuthorRoles(): List<String>

    @GET("api/v1/tags/series")
    suspend fun getSeriesTags(): List<String>

    @GET("api/v1/tags/book")
    suspend fun getBookTags(): List<String>

    @GET("api/v1/genres")
    suspend fun getGenres(): List<String>

    @GET("api/v1/languages")
    suspend fun getLanguages(): List<String>

    @GET("api/v1/publishers")
    suspend fun getPublishers(): List<String>

    @GET("api/v1/age-ratings")
    suspend fun getAgeRatings(): List<Int>

    @GET("api/v1/sharing-labels")
    suspend fun getSharingLabels(): List<String>
}

interface MetadataApi {
    @PATCH("api/v1/series/{id}/metadata")
    suspend fun updateSeriesMetadata(@Path("id") id: String, @Body metadata: SeriesMetadataUpdateDto)

    @PATCH("api/v1/books/{id}/metadata")
    suspend fun updateBookMetadata(@Path("id") id: String, @Body metadata: BookMetadataUpdateDto)

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(@Path("bookId") bookId: String, @Body progress: ReadProgressUpdateDto)

    @DELETE("api/v1/books/{bookId}/read-progress")
    suspend fun deleteBookReadProgress(@Path("bookId") bookId: String)

    @POST("api/v1/series/{seriesId}/read-progress")
    suspend fun markSeriesRead(@Path("seriesId") seriesId: String)

    @DELETE("api/v1/series/{seriesId}/read-progress")
    suspend fun markSeriesUnread(@Path("seriesId") seriesId: String)

    @POST("api/v1/books/{bookId}/metadata/refresh")
    suspend fun refreshBookMetadata(@Path("bookId") bookId: String)

    @POST("api/v1/series/{seriesId}/metadata/refresh")
    suspend fun refreshSeriesMetadata(@Path("seriesId") seriesId: String)

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

interface CollectionApi {
    @GET("api/v1/collections")
    suspend fun getCollections(
        @Query("search") search: String? = null,
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<CollectionDto>

    @POST("api/v1/collections")
    suspend fun createCollection(@Body body: CollectionCreationDto): CollectionDto

    @GET("api/v1/collections/{id}")
    suspend fun getCollection(@Path("id") id: String): CollectionDto

    @PATCH("api/v1/collections/{id}")
    suspend fun updateCollection(@Path("id") id: String, @Body body: CollectionUpdateDto): CollectionDto

    @DELETE("api/v1/collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String)

    @GET("api/v1/collections/{id}/series")
    suspend fun getCollectionSeries(
        @Path("id") id: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("read_status") readStatus: List<String>? = null
    ): PagedDto<SeriesDto>

    @Multipart
    @POST("api/v1/collections/{id}/thumbnails")
    suspend fun uploadCollectionThumbnail(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Query("selected") selected: Boolean = true
    )
}

interface ReadListApi {
    @GET("api/v1/readlists")
    suspend fun getReadLists(
        @Query("search") search: String? = null,
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<ReadListDto>

    @POST("api/v1/readlists")
    suspend fun createReadList(@Body body: ReadListCreationDto): ReadListDto

    @GET("api/v1/readlists/{id}")
    suspend fun getReadList(@Path("id") id: String): ReadListDto

    @PATCH("api/v1/readlists/{id}")
    suspend fun updateReadList(@Path("id") id: String, @Body body: ReadListUpdateDto): ReadListDto

    @DELETE("api/v1/readlists/{id}")
    suspend fun deleteReadList(@Path("id") id: String)

    @GET("api/v1/readlists/{id}/books")
    suspend fun getReadListBooks(
        @Path("id") id: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("read_status") readStatus: List<String>? = null
    ): PagedDto<BookDto>

    @GET("api/v1/readlists/{id}/books/{bookId}/next")
    suspend fun getNextBookInReadList(@Path("id") id: String, @Path("bookId") bookId: String): BookDto

    @GET("api/v1/readlists/{id}/books/{bookId}/previous")
    suspend fun getPreviousBookInReadList(@Path("id") id: String, @Path("bookId") bookId: String): BookDto

    @Multipart
    @POST("api/v1/readlists/{id}/thumbnails")
    suspend fun uploadReadListThumbnail(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Query("selected") selected: Boolean = true
    )
}

interface UserApi {
    @GET("api/v2/users/me")
    suspend fun getCurrentUser(): UserDto

    @GET("api/v2/users/me/api-keys")
    suspend fun getApiKeysForCurrentUser(): List<ApiKeyDto>

    @POST("api/v2/users/me/api-keys")
    suspend fun createApiKeyForCurrentUser(@Body body: ApiKeyRequestDto): ApiKeyDto

    @DELETE("api/v2/users/me/api-keys/{keyId}")
    suspend fun deleteApiKey(@Path("keyId") keyId: String)

    @GET("api/v2/users/me/authentication-activity")
    suspend fun getMyAuthenticationActivity(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("dateTime,desc")
    ): PagedDto<AuthenticationActivityDto>

    @PATCH("api/v2/users/me/password")
    suspend fun updateCurrentUserPassword(@Body body: PasswordUpdateDto)
}

interface AdminApi {
    @GET("api/v2/users")
    suspend fun getUsers(): List<UserDto>

    @POST("api/v2/users")
    suspend fun createUser(@Body body: UserCreationDto): UserDto

    @PATCH("api/v2/users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: UserUpdateDto): UserDto

    @DELETE("api/v2/users/{id}")
    suspend fun deleteUser(@Path("id") id: String)

    @PATCH("api/v2/users/{id}/password")
    suspend fun updateUserPassword(@Path("id") id: String, @Body body: PasswordUpdateDto)

    @GET("api/v2/users/authentication-activity")
    suspend fun getAuthenticationActivity(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: List<String> = listOf("dateTime,desc")
    ): PagedDto<AuthenticationActivityDto>

    @GET("api/v1/libraries/{libraryId}")
    suspend fun getLibrary(@Path("libraryId") libraryId: String): LibraryDto

    @POST("api/v1/libraries")
    suspend fun createLibrary(@Body body: LibraryCreationDto): LibraryDto

    @PATCH("api/v1/libraries/{libraryId}")
    suspend fun updateLibrary(@Path("libraryId") libraryId: String, @Body body: LibraryUpdateDto): LibraryDto

    @DELETE("api/v1/libraries/{libraryId}")
    suspend fun deleteLibrary(@Path("libraryId") libraryId: String)

    @POST("api/v1/libraries/{libraryId}/scan")
    suspend fun scanLibrary(@Path("libraryId") libraryId: String, @Body body: ScanRequestDto = ScanRequestDto(""))

    @POST("api/v1/libraries/{libraryId}/analyze")
    suspend fun analyzeLibrary(@Path("libraryId") libraryId: String)

    @POST("api/v1/libraries/{libraryId}/metadata/refresh")
    suspend fun refreshLibraryMetadata(@Path("libraryId") libraryId: String)

    @POST("api/v1/libraries/{libraryId}/empty-trash")
    suspend fun emptyLibraryTrash(@Path("libraryId") libraryId: String)

    @GET("api/v1/settings")
    suspend fun getServerSettings(): SettingsDto

    @PATCH("api/v1/settings")
    suspend fun updateServerSettings(@Body body: SettingsUpdateDto): SettingsDto

    @GET("api/v1/history")
    suspend fun getHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
        @Query("sort") sort: List<String> = listOf("timestamp,desc")
    ): PagedDto<HistoricalEventDto>

    @GET("api/v1/books/duplicates")
    suspend fun getDuplicateBooks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<BookDto>

    @GET("api/v1/page-hashes")
    suspend fun getKnownPageHashes(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<PageHashKnownDto>

    @GET("api/v1/page-hashes/unknown")
    suspend fun getUnknownPageHashes(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedDto<PageHashUnknownDto>

    @PUT("api/v1/page-hashes")
    suspend fun markPageHashKnown(@Body body: PageHashCreationDto)

    @POST("api/v1/page-hashes/{pageHash}/delete-all")
    suspend fun deleteAllDuplicatePages(@Path("pageHash") pageHash: String)

    @POST("api/v1/page-hashes/{pageHash}/delete-match")
    suspend fun deleteDuplicatePageMatch(
        @Path("pageHash") pageHash: String,
        @Body body: PageHashMatchDto
    )

    @DELETE("api/v1/tasks")
    suspend fun clearTaskQueue(): Int

    @GET("api/v1/announcements")
    suspend fun getAnnouncements(): List<AnnouncementDto>

    @PUT("api/v1/announcements")
    suspend fun markAnnouncementsRead()

    @GET("api/v1/claim")
    suspend fun getClaimStatus(): ClaimStatusDto

    @GET("api/v1/oauth2/providers")
    suspend fun getOAuthProviders(): List<OAuth2ClientDto>

    @GET("api/v1/releases")
    suspend fun getReleases(): List<ReleaseDto>
}
