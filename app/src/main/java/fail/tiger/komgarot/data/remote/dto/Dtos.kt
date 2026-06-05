package fail.tiger.komgarot.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LibraryDto(
    val id: String = "",
    val name: String = "",
    val root: String = "",
    val unavailable: Boolean = false,
    val oneshotsDirectory: String? = null,
    val scanInterval: String? = null,
    val scanOnStartup: Boolean = false,
    val scanCbx: Boolean = true,
    val scanEpub: Boolean = true,
    val scanPdf: Boolean = true,
    val scanDirectoryExclusions: List<String> = emptyList(),
    val scanForceModifiedTime: Boolean = false,
    val emptyTrashAfterScan: Boolean = false,
    val seriesCover: String = "FIRST",
    val hashFiles: Boolean = false,
    val hashPages: Boolean = false,
    val hashKoreader: Boolean = false,
    val analyzeDimensions: Boolean = false,
    val importComicInfoBook: Boolean = true,
    val importComicInfoSeries: Boolean = true,
    val importComicInfoCollection: Boolean = true,
    val importComicInfoReadList: Boolean = true,
    val importComicInfoSeriesAppendVolume: Boolean = false,
    val importEpubBook: Boolean = true,
    val importEpubSeries: Boolean = true,
    val importLocalArtwork: Boolean = true,
    val importBarcodeIsbn: Boolean = false,
    val importMylarSeries: Boolean = false,
    val convertToCbz: Boolean = false,
    val repairExtensions: Boolean = false
)

data class SeriesDto(
    val id: String = "",
    val libraryId: String = "",
    val name: String = "",
    val booksCount: Int = 0,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val booksInProgressCount: Int = 0,
    val oneshot: Boolean = false,
    val created: String? = null,
    val lastModified: String? = null,
    val metadata: SeriesMetadataDto = SeriesMetadataDto()
)

data class SeriesMetadataDto(
    val title: String = "",
    val titleSort: String = "",
    val status: String = "",
    val summary: String = "",
    val publisher: String = "",
    val ageRating: Int? = null,
    val language: String = "",
    val readingDirection: String? = null,
    val alternateTitles: List<AlternateTitleDto> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sharingLabels: List<String> = emptyList(),
    val links: List<WebLinkDto> = emptyList(),
    val titleLock: Boolean = false,
    val titleSortLock: Boolean = false,
    val statusLock: Boolean = false,
    val summaryLock: Boolean = false,
    val publisherLock: Boolean = false,
    val ageRatingLock: Boolean = false,
    val languageLock: Boolean = false,
    val readingDirectionLock: Boolean = false,
    val alternateTitlesLock: Boolean = false,
    val genresLock: Boolean = false,
    val tagsLock: Boolean = false,
    val sharingLabelsLock: Boolean = false,
    val linksLock: Boolean = false,
    val totalBookCount: Int? = null,
    val totalBookCountLock: Boolean = false
)

data class BookDto(
    val id: String = "",
    val libraryId: String? = null,
    val seriesId: String = "",
    val seriesTitle: String? = null,
    val name: String = "",
    val number: Float = 0f,
    val oneshot: Boolean = false,
    val created: String? = null,
    val lastModified: String? = null,
    val fileLastModified: String? = null,
    val url: String? = null,
    val sizeBytes: Long? = null,
    val readProgress: ReadProgressDto? = null,
    val media: BookMediaDto = BookMediaDto(),
    val metadata: BookMetadataDto = BookMetadataDto()
)

data class BookMediaDto(
    val pagesCount: Int = 0,
    val mediaType: String? = null,
    val size: Long? = null
)

data class BookMetadataDto(
    val title: String = "",
    val summary: String = "",
    val number: String = "",
    val numberSort: Float? = null,
    val releaseDate: String? = null,
    val isbn: String = "",
    val authors: List<AuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val links: List<WebLinkDto> = emptyList(),
    val titleLock: Boolean = false,
    val summaryLock: Boolean = false,
    val numberLock: Boolean = false,
    val numberSortLock: Boolean = false,
    val releaseDateLock: Boolean = false,
    val isbnLock: Boolean = false,
    val authorsLock: Boolean = false,
    val tagsLock: Boolean = false,
    val linksLock: Boolean = false
)

data class AuthorDto(val name: String = "", val role: String = "")
data class AlternateTitleDto(val label: String = "", val title: String = "")
data class WebLinkDto(val label: String = "", val url: String = "")

data class PageDto(val number: Int, val mediaType: String, val width: Int, val height: Int)

data class PagedDto<T>(
    val content: List<T>,
    val totalPages: Int,
    val totalElements: Long,
    val number: Int,
    val size: Int
)

data class ReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String? = null,
    val lastModified: String? = null
)

data class ReadProgressUpdateDto(val page: Int, val completed: Boolean = false)
data class GroupCountDto(val name: String = "", val count: Long = 0)

data class SeriesMetadataUpdateDto(
    val title: String,
    val titleSort: String,
    val status: String,
    val summary: String,
    val publisher: String,
    val ageRating: Int?,
    val language: String,
    val readingDirection: String?,
    val alternateTitles: List<AlternateTitleDto>,
    val genres: List<String>,
    val tags: List<String>,
    val sharingLabels: List<String>,
    val links: List<WebLinkDto>,
    val totalBookCount: Int?,
    val titleLock: Boolean,
    val titleSortLock: Boolean,
    val statusLock: Boolean,
    val summaryLock: Boolean,
    val publisherLock: Boolean,
    val ageRatingLock: Boolean,
    val languageLock: Boolean,
    val readingDirectionLock: Boolean,
    val alternateTitlesLock: Boolean,
    val genresLock: Boolean,
    val tagsLock: Boolean,
    val sharingLabelsLock: Boolean,
    val linksLock: Boolean,
    val totalBookCountLock: Boolean
)

data class BookMetadataUpdateDto(
    val title: String,
    val summary: String,
    val number: String,
    val numberSort: Float?,
    val releaseDate: String?,
    val isbn: String,
    val authors: List<AuthorDto>,
    val tags: List<String>,
    val links: List<WebLinkDto>,
    val titleLock: Boolean,
    val summaryLock: Boolean,
    val numberLock: Boolean,
    val numberSortLock: Boolean,
    val releaseDateLock: Boolean,
    val isbnLock: Boolean,
    val authorsLock: Boolean,
    val tagsLock: Boolean,
    val linksLock: Boolean
)

data class UserDto(
    val id: String = "",
    val email: String = "",
    val roles: List<String> = emptyList(),
    val sharedAllLibraries: Boolean = false,
    val sharedLibrariesIds: List<String> = emptyList(),
    val labelsAllow: List<String> = emptyList(),
    val labelsExclude: List<String> = emptyList(),
    val ageRestriction: AgeRestrictionDto? = null
) {
    val isAdmin: Boolean get() = roles.any { it.equals("ADMIN", ignoreCase = true) }
}

data class AgeRestrictionDto(val age: Int = 0, val restriction: String = "ALLOW_ONLY")
data class AgeRestrictionUpdateDto(val age: Int?, val restriction: String?)
data class SharedLibrariesUpdateDto(val all: Boolean, val libraryIds: List<String>)

data class UserCreationDto(
    val email: String,
    val password: String,
    val roles: List<String>,
    val sharedLibraries: SharedLibrariesUpdateDto,
    val labelsAllow: List<String> = emptyList(),
    val labelsExclude: List<String> = emptyList(),
    val ageRestriction: AgeRestrictionUpdateDto? = null
)

data class UserUpdateDto(
    val roles: List<String>,
    val sharedLibraries: SharedLibrariesUpdateDto,
    val labelsAllow: List<String> = emptyList(),
    val labelsExclude: List<String> = emptyList(),
    val ageRestriction: AgeRestrictionUpdateDto? = null
)

data class PasswordUpdateDto(val password: String)

data class CollectionDto(
    val id: String = "",
    val name: String = "",
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null
)

data class CollectionCreationDto(
    val name: String,
    val ordered: Boolean,
    val seriesIds: List<String>
)

data class CollectionUpdateDto(
    val name: String? = null,
    val ordered: Boolean? = null,
    val seriesIds: List<String>? = null
)

data class ReadListDto(
    val id: String = "",
    val name: String = "",
    val summary: String = "",
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val bookIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null
)

data class ReadListCreationDto(
    val name: String,
    val summary: String,
    val ordered: Boolean,
    val bookIds: List<String>
)

data class ReadListUpdateDto(
    val name: String? = null,
    val summary: String? = null,
    val ordered: Boolean? = null,
    val bookIds: List<String>? = null
)

data class LibraryCreationDto(
    val name: String,
    val root: String,
    val importComicInfoBook: Boolean = true,
    val importComicInfoSeries: Boolean = true,
    val importComicInfoCollection: Boolean = true,
    val importComicInfoReadList: Boolean = true,
    val importComicInfoSeriesAppendVolume: Boolean = false,
    val importEpubBook: Boolean = true,
    val importEpubSeries: Boolean = true,
    val importLocalArtwork: Boolean = true,
    val importBarcodeIsbn: Boolean = false,
    val importMylarSeries: Boolean = false,
    val scanCbx: Boolean = true,
    val scanEpub: Boolean = true,
    val scanPdf: Boolean = true,
    val scanInterval: String = "DISABLED",
    val scanOnStartup: Boolean = false,
    val scanDirectoryExclusions: List<String> = emptyList(),
    val scanForceModifiedTime: Boolean = false,
    val emptyTrashAfterScan: Boolean = false,
    val seriesCover: String = "FIRST",
    val hashFiles: Boolean = false,
    val hashPages: Boolean = false,
    val hashKoreader: Boolean = false,
    val analyzeDimensions: Boolean = false,
    val convertToCbz: Boolean = false,
    val repairExtensions: Boolean = false,
    val oneshotsDirectory: String? = null
)

data class LibraryUpdateDto(
    val name: String? = null,
    val root: String? = null,
    val scanInterval: String? = null,
    val scanOnStartup: Boolean? = null,
    val scanCbx: Boolean? = null,
    val scanEpub: Boolean? = null,
    val scanPdf: Boolean? = null,
    val scanDirectoryExclusions: List<String>? = null,
    val scanForceModifiedTime: Boolean? = null,
    val emptyTrashAfterScan: Boolean? = null,
    val seriesCover: String? = null,
    val hashFiles: Boolean? = null,
    val hashPages: Boolean? = null,
    val hashKoreader: Boolean? = null,
    val analyzeDimensions: Boolean? = null,
    val importComicInfoBook: Boolean? = null,
    val importComicInfoSeries: Boolean? = null,
    val importComicInfoCollection: Boolean? = null,
    val importComicInfoReadList: Boolean? = null,
    val importComicInfoSeriesAppendVolume: Boolean? = null,
    val importEpubBook: Boolean? = null,
    val importEpubSeries: Boolean? = null,
    val importLocalArtwork: Boolean? = null,
    val importBarcodeIsbn: Boolean? = null,
    val importMylarSeries: Boolean? = null,
    val convertToCbz: Boolean? = null,
    val repairExtensions: Boolean? = null,
    val oneshotsDirectory: String? = null
)

data class ScanRequestDto(val path: String)

data class SettingsDto(
    val deleteEmptyCollections: Boolean = false,
    val deleteEmptyReadLists: Boolean = false,
    val koboProxy: Boolean = false,
    val koboPort: Int? = null,
    val rememberMeDurationDays: Long = 14,
    val taskPoolSize: Int = 1,
    val thumbnailSize: String = "DEFAULT",
    val serverContextPath: SettingMultiSourceString? = null,
    val serverPort: SettingMultiSourceInteger? = null,
    val kepubifyPath: SettingMultiSourceString? = null
)

data class SettingsUpdateDto(
    val deleteEmptyCollections: Boolean? = null,
    val deleteEmptyReadLists: Boolean? = null,
    val koboProxy: Boolean? = null,
    val koboPort: Int? = null,
    val rememberMeDurationDays: Long? = null,
    val taskPoolSize: Int? = null,
    val thumbnailSize: String? = null,
    val serverContextPath: String? = null,
    val serverPort: Int? = null,
    val renewRememberMeKey: Boolean? = null
)

data class SettingMultiSourceString(val value: String? = null, val source: String? = null)
data class SettingMultiSourceInteger(val value: Int? = null, val source: String? = null)

data class ApiKeyDto(
    val id: String = "",
    val userId: String = "",
    val comment: String = "",
    val key: String = "",
    val createdDate: String? = null,
    val lastModifiedDate: String? = null
)

data class ApiKeyRequestDto(val comment: String)

data class AuthenticationActivityDto(
    val dateTime: String = "",
    val success: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val ip: String? = null,
    val source: String? = null,
    val userAgent: String? = null,
    val error: String? = null,
    val apiKeyId: String? = null,
    val apiKeyComment: String? = null
)

data class HistoricalEventDto(
    val id: String = "",
    val type: String = "",
    val timestamp: String = "",
    val bookId: String? = null,
    val seriesId: String? = null,
    val properties: Map<String, String> = emptyMap()
)

data class PageHashKnownDto(
    val hash: String = "",
    val size: Int = 0,
    val action: String? = null,
    val matchCount: Int = 0
)

data class PageHashUnknownDto(
    val hash: String = "",
    val size: Int = 0,
    val matchCount: Int = 0
)

data class PageHashMatchDto(
    val bookId: String = "",
    val pageNumber: Int = 0,
    val url: String? = null,
    val fileName: String? = null,
    val seriesId: String? = null
)

data class PageHashCreationDto(
    val hash: String,
    val size: Int? = null,
    val action: String
)

data class AnnouncementDto(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val date: String? = null,
    val read: Boolean = false
)

data class JsonFeedDto(
    val items: List<JsonFeedItemDto> = emptyList()
)

data class JsonFeedItemDto(
    val id: String = "",
    val title: String = "",
    val summary: String? = null,
    @SerializedName("content_html") val contentHtml: String? = null,
    @SerializedName("date_modified") val dateModified: String? = null,
    @SerializedName("_komga") val komga: KomgaExtensionDto? = null
)

data class KomgaExtensionDto(
    val read: Boolean = false
)

data class ClaimStatusDto(val claimed: Boolean = true)
data class OAuth2ClientDto(val name: String = "", val label: String = "")
data class ReleaseDto(val version: String = "", val releaseDate: String? = null, val url: String? = null)
