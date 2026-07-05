package fail.tiger.komgarot.ui.library

import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.ReadProgressDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.LibraryHomeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHomeLoaderTest {
    @Test
    fun loadLibraryHomeKeepsSuccessfulSectionsWhenOneSectionFails() = runBlocking {
        val result = loadLibraryHome(FakeLibraryHomeSource(failLatestBooks = true))

        assertEquals(listOf(LibraryDto(id = "library-1", name = "Library")), result.libraries)
        assertEquals(listOf("continue-1"), result.continueReadingBooks.map { it.id })
        assertTrue(result.latestBooks.isEmpty())
        assertEquals(listOf(SeriesDto(id = "updated-1")), result.updatedSeries)
        assertEquals(listOf(SeriesDto(id = "new-1")), result.newSeries)
        assertEquals(listOf("latest failed"), result.failures)
    }

    @Test
    fun loadLibraryHomeUsesContinueReadingSource() = runBlocking {
        val result = loadLibraryHome(FakeLibraryHomeSource())

        assertEquals(listOf("continue-1"), result.continueReadingBooks.map { it.id })
    }

    private class FakeLibraryHomeSource(
        private val failLatestBooks: Boolean = false
    ) : LibraryHomeSource {
        override suspend fun getLibraries(): List<LibraryDto> =
            listOf(LibraryDto(id = "library-1", name = "Library"))

        override suspend fun getContinueReadingBooks(size: Int): List<BookDto> =
            listOf(BookDto(id = "continue-1", readProgress = ReadProgressDto(page = 4, completed = false)))

        override suspend fun getLatestBooks(size: Int): List<BookDto> {
            if (failLatestBooks) error("latest failed")
            return listOf(BookDto(id = "latest-1"))
        }

        override suspend fun getUpdatedSeries(size: Int): List<SeriesDto> =
            listOf(SeriesDto(id = "updated-1"))

        override suspend fun getNewSeries(size: Int): List<SeriesDto> =
            listOf(SeriesDto(id = "new-1"))
    }
}
