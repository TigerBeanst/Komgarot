package fail.tiger.komgarot.data.remote.dto

data class LibraryDto(val id: String, val name: String)

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
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList()
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
    val releaseDate: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val tags: List<String> = emptyList()
)

data class AuthorDto(val name: String = "", val role: String = "")

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
