package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.CollectionCreationDto
import fail.tiger.komgarot.data.remote.dto.CollectionDto
import fail.tiger.komgarot.data.remote.dto.CollectionUpdateDto
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto

class CollectionRepository(private val api: KomgaApi) {
    suspend fun getCollections(page: Int, search: String? = null): PagedDto<CollectionDto> =
        api.getCollections(page = page, search = search?.takeIf { it.isNotBlank() })

    suspend fun getCollection(id: String): Result<CollectionDto> = runCatching { api.getCollection(id) }

    suspend fun getSeries(id: String, page: Int): PagedDto<SeriesDto> =
        api.getCollectionSeries(id = id, page = page)

    suspend fun create(name: String, ordered: Boolean, seriesIds: List<String>): Result<CollectionDto> =
        runCatching { api.createCollection(CollectionCreationDto(name, ordered, seriesIds)) }

    suspend fun update(id: String, name: String? = null, ordered: Boolean? = null, seriesIds: List<String>? = null): Result<CollectionDto> =
        runCatching { api.updateCollection(id, CollectionUpdateDto(name, ordered, seriesIds)) }

    suspend fun delete(id: String): Result<Unit> = runCatching { api.deleteCollection(id) }
}
