package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.remote.dto.SeriesSearchDto
import fail.tiger.komgarot.data.remote.dto.isCondition

class SeriesRepository(private val api: KomgaApi) {
    suspend fun getSeries(libraryId: String?, page: Int, search: String? = null, sort: String = "metadata.titleSort,asc"): PagedDto<SeriesDto> {
        val authorSearch = search?.parseAuthorSearch()
        val libraryCondition = libraryId
            ?.takeIf { it != "all" }
            ?.let {
                mapOf(
                    "operator" to "SERIES",
                    "libraryId" to isCondition(it)
                )
            }
        val authorCondition = authorSearch?.let {
            mapOf(
                "operator" to "SERIES",
                "author" to isCondition(
                    mapOf(
                        "name" to it.name,
                        "role" to it.role.orEmpty()
                    )
                )
            )
        }
        val condition = combineSeriesConditions(libraryCondition, authorCondition)

        return api.getSeries(
            search = SeriesSearchDto(
                condition = condition,
                fullTextSearch = search
                    ?.takeIf { authorSearch == null }
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            ),
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

private data class AuthorSearch(val name: String, val role: String?)

private fun String.parseAuthorSearch(): AuthorSearch? {
    if (!startsWith("author:", ignoreCase = true)) return null
    val value = substringAfter(':').trim()
    if (value.isEmpty()) return null
    val parts = value.split(',', limit = 2).map { it.trim() }
    return AuthorSearch(
        name = parts.first(),
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
