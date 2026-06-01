package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto

interface LibraryHomeSource {
    suspend fun getLibraries(): List<LibraryDto>
    suspend fun getBooksOnDeck(size: Int = 12): List<BookDto>
    suspend fun getLatestBooks(size: Int = 12): List<BookDto>
    suspend fun getUpdatedSeries(size: Int = 12): List<SeriesDto>
    suspend fun getNewSeries(size: Int = 12): List<SeriesDto>
}

class LibraryRepository(private val api: KomgaApi) : LibraryHomeSource {
    override suspend fun getLibraries(): List<LibraryDto> = api.getLibraries()

    override suspend fun getBooksOnDeck(size: Int): List<BookDto> =
        api.getBooksOnDeck(size = size).content

    override suspend fun getLatestBooks(size: Int): List<BookDto> =
        api.getLatestBooks(size = size).content

    override suspend fun getUpdatedSeries(size: Int): List<SeriesDto> =
        api.getUpdatedSeries(size = size).content

    override suspend fun getNewSeries(size: Int): List<SeriesDto> =
        api.getNewSeries(size = size).content
}
