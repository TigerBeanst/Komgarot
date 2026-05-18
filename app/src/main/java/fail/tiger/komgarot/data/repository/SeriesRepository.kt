package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.SearchCondition
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.remote.dto.SeriesSearchDto
import fail.tiger.komgarot.data.remote.dto.isCondition

class SeriesRepository(private val api: KomgaApi) {
    suspend fun getSeries(
        libraryId: String?,
        page: Int,
        search: String? = null,
        sort: String = "metadata.titleSort,asc",
        filters: SeriesFilters = SeriesFilters()
    ): PagedDto<SeriesDto> {
        return api.getSeries(
            search = buildSeriesSearch(libraryId = libraryId, search = search, filters = filters),
            page = page,
            sort = if (sort.isEmpty()) emptyList() else listOf(sort)
        )
    }

    suspend fun getSeriesById(id: String): Result<SeriesDto> = runCatching { api.getSeriesById(id) }

    suspend fun getLatestSeries(size: Int = 12): List<SeriesDto> =
        api.getLatestSeries(size = size).content

    suspend fun getNewSeries(size: Int = 12): List<SeriesDto> =
        api.getNewSeries(size = size).content

    suspend fun getUpdatedSeries(size: Int = 12): List<SeriesDto> =
        api.getUpdatedSeries(size = size).content
}

internal fun buildSeriesSearch(
    libraryId: String?,
    search: String? = null,
    filters: SeriesFilters = SeriesFilters()
): SeriesSearchDto {
    val trimmedSearch = search?.trim()?.takeIf { it.isNotEmpty() }
    val hasAuthorPrefix = trimmedSearch?.startsWith("author:", ignoreCase = true) == true
    val authorSearch = trimmedSearch?.parseAuthorSearch()
    val libraryCondition = libraryId
        ?.takeIf { it != "all" }
        ?.let {
            mapOf(
                "operator" to "SERIES",
                "libraryId" to isCondition(it)
            )
        }
    val authorCondition = authorSearch
        ?.takeIf { it.role != null }
        ?.let {
            mapOf(
                "operator" to "SERIES",
                "author" to isCondition("${it.name},${it.role}")
            )
        }
    val condition = combineSeriesConditions(
        libraryCondition,
        authorCondition,
        filters.readStatus?.let { SearchCondition.series("readStatus", isCondition(it)) },
        filters.status?.let { SearchCondition.series("seriesStatus", isCondition(it)) },
        filters.publisher?.let { SearchCondition.series("publisher", isCondition(it)) },
        filters.language?.let { SearchCondition.series("language", isCondition(it)) },
        filters.genre?.let { SearchCondition.series("genre", isCondition(it)) },
        filters.tag?.let { SearchCondition.series("tag", isCondition(it)) },
        filters.collectionId?.let { SearchCondition.series("collectionId", isCondition(it)) },
        filters.sharingLabel?.let { SearchCondition.series("sharingLabel", isCondition(it)) },
        filters.complete?.let {
            SearchCondition.series("complete", if (it) SearchCondition.isTrue() else SearchCondition.isFalse())
        },
        filters.oneshot?.let {
            SearchCondition.series("oneshot", if (it) SearchCondition.isTrue() else SearchCondition.isFalse())
        },
        filters.minAgeRating?.let {
            SearchCondition.series("ageRating", mapOf("operator" to "GREATER_THAN", "value" to it))
        },
        filters.releaseDateInLast?.let { SearchCondition.series("releaseDate", SearchCondition.inTheLast(it)) }
    )

    return SeriesSearchDto(
        condition = condition,
        fullTextSearch = when {
            authorSearch?.role == null && authorSearch != null -> "author:(${authorSearch.name})"
            authorSearch == null && !hasAuthorPrefix -> trimmedSearch
            else -> null
        }
    )
}

data class SeriesFilters(
    val readStatus: String? = null,
    val status: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val genre: String? = null,
    val tag: String? = null,
    val collectionId: String? = null,
    val sharingLabel: String? = null,
    val complete: Boolean? = null,
    val oneshot: Boolean? = null,
    val minAgeRating: Int? = null,
    val releaseDateInLast: String? = null
) {
    val activeCount: Int
        get() = listOf(
            readStatus, status, publisher, language, genre, tag, collectionId,
            sharingLabel, complete, oneshot, minAgeRating, releaseDateInLast
        ).count { it != null }
}

private data class AuthorSearch(val name: String, val role: String?)

private fun String.parseAuthorSearch(): AuthorSearch? {
    if (!startsWith("author:", ignoreCase = true)) return null
    val value = substringAfter(':').trim()
    if (value.isEmpty()) return null
    val parts = value.split(',', limit = 2).map { it.trim() }
    val name = parts.first().takeIf { it.isNotEmpty() } ?: return null
    return AuthorSearch(
        name = name,
        role = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
    )
}

private fun combineSeriesConditions(vararg conditions: Map<String, Any>?): Map<String, Any>? {
    val present = conditions.filterNotNull()
    return when (present.size) {
        0 -> null
        1 -> present.first()
        else -> mapOf("operator" to "SERIES", "allOf" to present)
    }
}
