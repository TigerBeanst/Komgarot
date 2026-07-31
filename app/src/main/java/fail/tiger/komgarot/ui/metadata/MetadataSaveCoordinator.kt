package fail.tiger.komgarot.ui.metadata

import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataUpdateDto
import fail.tiger.komgarot.data.remote.dto.SeriesLanguageUpdateDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataUpdateDto
import retrofit2.HttpException

sealed interface MetadataSaveResult<out T> {
    data class Success<T>(val metadata: T) : MetadataSaveResult<T>
    data class Failure(val message: String) : MetadataSaveResult<Nothing>
}

suspend fun saveBookMetadata(
    meta: BookMetadataDto,
    fallbackErrorMessage: String,
    forbiddenErrorMessage: String,
    update: suspend (BookMetadataUpdateDto) -> Unit
): MetadataSaveResult<BookMetadataDto> {
    val body = BookMetadataUpdateDto(
        title = meta.title,
        summary = meta.summary,
        number = meta.number,
        numberSort = meta.numberSort,
        releaseDate = meta.releaseDate,
        isbn = meta.isbn,
        authors = meta.authors,
        tags = meta.tags,
        links = meta.links,
        titleLock = meta.titleLock,
        summaryLock = meta.summaryLock,
        numberLock = meta.numberLock,
        numberSortLock = meta.numberSortLock,
        releaseDateLock = meta.releaseDateLock,
        isbnLock = meta.isbnLock,
        authorsLock = meta.authorsLock,
        tagsLock = meta.tagsLock,
        linksLock = meta.linksLock
    )
    return runCatching { update(body) }
        .fold(
            onSuccess = { MetadataSaveResult.Success(meta) },
            onFailure = { MetadataSaveResult.Failure(it.metadataSaveFailureMessage(fallbackErrorMessage, forbiddenErrorMessage)) }
        )
}

suspend fun saveSeriesMetadata(
    meta: SeriesMetadataDto,
    fallbackErrorMessage: String,
    forbiddenErrorMessage: String,
    update: suspend (SeriesMetadataUpdateDto) -> Unit
): MetadataSaveResult<SeriesMetadataDto> {
    val body = SeriesMetadataUpdateDto(
        title = meta.title,
        titleSort = meta.titleSort,
        status = meta.status,
        summary = meta.summary,
        publisher = meta.publisher,
        ageRating = meta.ageRating,
        language = meta.language,
        readingDirection = meta.readingDirection,
        alternateTitles = meta.alternateTitles,
        genres = meta.genres,
        tags = meta.tags,
        sharingLabels = meta.sharingLabels,
        links = meta.links,
        totalBookCount = meta.totalBookCount,
        titleLock = meta.titleLock,
        titleSortLock = meta.titleSortLock,
        statusLock = meta.statusLock,
        summaryLock = meta.summaryLock,
        publisherLock = meta.publisherLock,
        ageRatingLock = meta.ageRatingLock,
        languageLock = meta.languageLock,
        readingDirectionLock = meta.readingDirectionLock,
        alternateTitlesLock = meta.alternateTitlesLock,
        genresLock = meta.genresLock,
        tagsLock = meta.tagsLock,
        sharingLabelsLock = meta.sharingLabelsLock,
        linksLock = meta.linksLock,
        totalBookCountLock = meta.totalBookCountLock
    )
    return runCatching { update(body) }
        .fold(
            onSuccess = { MetadataSaveResult.Success(meta) },
            onFailure = { MetadataSaveResult.Failure(it.metadataSaveFailureMessage(fallbackErrorMessage, forbiddenErrorMessage)) }
        )
}

suspend fun saveSeriesLanguageMetadata(
    current: SeriesMetadataDto,
    language: String,
    languageLock: Boolean,
    fallbackErrorMessage: String,
    forbiddenErrorMessage: String,
    update: suspend (SeriesLanguageUpdateDto) -> Unit
): MetadataSaveResult<SeriesMetadataDto> {
    val updated = current.copy(language = language, languageLock = languageLock)
    val body = SeriesLanguageUpdateDto(
        language = language,
        languageLock = languageLock
    )
    return runCatching { update(body) }
        .fold(
            onSuccess = { MetadataSaveResult.Success(updated) },
            onFailure = { MetadataSaveResult.Failure(it.metadataSaveFailureMessage(fallbackErrorMessage, forbiddenErrorMessage)) }
        )
}

private fun Throwable.metadataSaveFailureMessage(
    fallbackErrorMessage: String,
    forbiddenErrorMessage: String
): String =
    if (this is HttpException && code() == 403) {
        forbiddenErrorMessage
    } else {
        message?.takeIf(String::isNotBlank) ?: fallbackErrorMessage
    }
