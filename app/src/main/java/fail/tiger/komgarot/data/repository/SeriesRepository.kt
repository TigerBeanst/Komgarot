package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto

class SeriesRepository(private val api: KomgaApi) {
    suspend fun getSeries(libraryId: String?, page: Int, search: String? = null, sort: String = "metadata.titleSort,asc"): PagedDto<SeriesDto> =
        api.getSeries(
            libraryId = libraryId?.takeIf { it != "all" },
            search = search,
            page = page,
            sort = if (sort.isEmpty()) emptyList() else listOf(sort)
        )

    suspend fun getSeriesById(id: String): Result<SeriesDto> = runCatching { api.getSeriesById(id) }
}
