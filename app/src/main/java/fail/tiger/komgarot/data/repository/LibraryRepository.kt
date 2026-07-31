package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.BookSearchDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SearchCondition
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.remote.dto.isCondition

interface LibraryHomeSource {
    suspend fun getLibraries(): List<LibraryDto>
    suspend fun getContinueReadingBooks(size: Int = 12): List<BookDto>
    suspend fun getLatestBooks(size: Int = 12): List<BookDto>
    suspend fun getUpdatedSeries(size: Int = 12): List<SeriesDto>
    suspend fun getNewSeries(size: Int = 12): List<SeriesDto>
}

class LibraryRepository(private val api: KomgaApi) : LibraryHomeSource {
    override suspend fun getLibraries(): List<LibraryDto> =
        api.getLibrariesRaw().toFlexibleList("libraries")

    override suspend fun getContinueReadingBooks(size: Int): List<BookDto> =
        api.getBooks(
            search = BookSearchDto(
                condition = SearchCondition.book("readStatus", isCondition("IN_PROGRESS"))
            ),
            page = 0,
            size = size,
            sort = listOf("readProgress.readDate,desc")
        ).content

    override suspend fun getLatestBooks(size: Int): List<BookDto> =
        api.getBooks(
            search = BookSearchDto(),
            page = 0,
            size = size,
            sort = listOf("created,desc")
        ).content

    override suspend fun getUpdatedSeries(size: Int): List<SeriesDto> =
        api.getUpdatedSeries(size = size).content

    override suspend fun getNewSeries(size: Int): List<SeriesDto> =
        api.getNewSeries(size = size).content
}
