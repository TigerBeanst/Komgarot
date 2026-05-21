package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.ReadListCreationDto
import fail.tiger.komgarot.data.remote.dto.ReadListDto
import fail.tiger.komgarot.data.remote.dto.ReadListUpdateDto

class ReadListRepository(private val api: KomgaApi) {
    suspend fun getReadLists(page: Int, search: String? = null): PagedDto<ReadListDto> =
        api.getReadLists(page = page, search = search?.takeIf { it.isNotBlank() })

    suspend fun getReadList(id: String): Result<ReadListDto> = runCatching { api.getReadList(id) }

    suspend fun getBooks(id: String, page: Int): PagedDto<BookDto> =
        api.getReadListBooks(id = id, page = page)

    suspend fun create(name: String, summary: String, ordered: Boolean, bookIds: List<String>): Result<ReadListDto> =
        runCatching { api.createReadList(ReadListCreationDto(name, summary, ordered, bookIds)) }

    suspend fun update(id: String, name: String? = null, summary: String? = null, ordered: Boolean? = null, bookIds: List<String>? = null): Result<ReadListDto> =
        runCatching { api.updateReadList(id, ReadListUpdateDto(name, summary, ordered, bookIds)) }

    suspend fun delete(id: String): Result<Unit> = runCatching { api.deleteReadList(id) }
}
