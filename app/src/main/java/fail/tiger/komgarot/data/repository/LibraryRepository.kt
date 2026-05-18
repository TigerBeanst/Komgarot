package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto

class LibraryRepository(private val api: KomgaApi) {
    suspend fun getLibraries(): List<LibraryDto> = api.getLibraries()

    suspend fun getBooksOnDeck(size: Int = 12): List<BookDto> =
        api.getBooksOnDeck(size = size).content

    suspend fun getLatestBooks(size: Int = 12): List<BookDto> =
        api.getLatestBooks(size = size).content

    suspend fun getUpdatedSeries(size: Int = 12): List<SeriesDto> =
        api.getUpdatedSeries(size = size).content

    suspend fun getNewSeries(size: Int = 12): List<SeriesDto> =
        api.getNewSeries(size = size).content
}
