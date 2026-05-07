package fail.tiger.komgarot.data.remote.dto

data class LibraryDto(val id: String, val name: String)

data class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int,
    val booksReadCount: Int,
    val booksUnreadCount: Int,
    val metadata: SeriesMetadataDto
)

data class SeriesMetadataDto(
    val title: String,
    val titleSort: String,
    val status: String,
    val summary: String,
    val publisher: String,
    val ageRating: Int?,
    val language: String,
    val genres: List<String>,
    val tags: List<String>
)

data class BookDto(
    val id: String,
    val seriesId: String,
    val name: String,
    val number: Float,
    val created: String? = null,
    val fileLastModified: String? = null,
    val url: String? = null,
    val sizeBytes: Long? = null,
    val readProgress: ReadProgressDto? = null,
    val media: BookMediaDto,
    val metadata: BookMetadataDto
)

data class BookMediaDto(
    val pagesCount: Int,
    val mediaType: String? = null,
    val size: Long? = null
)

data class BookMetadataDto(
    val title: String,
    val summary: String,
    val number: String,
    val releaseDate: String?,
    val authors: List<AuthorDto>,
    val tags: List<String>
)

data class AuthorDto(val name: String, val role: String)

data class PageDto(val number: Int, val mediaType: String, val width: Int, val height: Int)

data class PagedDto<T>(
    val content: List<T>,
    val totalPages: Int,
    val totalElements: Long,
    val number: Int,
    val size: Int
)

data class ReadProgressDto(
    val page: Int,
    val completed: Boolean
)
