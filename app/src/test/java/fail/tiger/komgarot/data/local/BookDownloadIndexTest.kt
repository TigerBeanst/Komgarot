package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.BookMediaDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto

class BookDownloadIndexTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordedBooksAreListedNewestFirst() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))

        index.record(
            CachedBookEntry(
                bookId = "book-1",
                title = "First",
                seriesTitle = "Series A",
                pageCount = 10,
                cachedPages = 10,
                isOneShot = false,
                updatedAt = 100
            )
        )
        index.record(
            CachedBookEntry(
                bookId = "book-2",
                title = "Second",
                seriesTitle = "Series B",
                pageCount = 8,
                cachedPages = 4,
                isOneShot = true,
                updatedAt = 200
            )
        )

        val books = index.list()

        assertEquals(listOf("book-2", "book-1"), books.map { it.bookId })
        assertEquals(4, books.first().cachedPages)
    }

    @Test
    fun recordingSameBookReplacesPreviousEntry() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))

        index.record(CachedBookEntry(bookId = "book-1", title = "Old", pageCount = 10, cachedPages = 2))
        index.record(CachedBookEntry(bookId = "book-1", title = "New", pageCount = 10, cachedPages = 10))

        val books = index.list()

        assertEquals(1, books.size)
        assertEquals("New", books.single().title)
        assertEquals(10, books.single().cachedPages)
    }

    @Test
    fun removingBookDeletesItFromList() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))
        index.record(CachedBookEntry(bookId = "book-1", title = "First", pageCount = 10, cachedPages = 10))

        index.remove("book-1")

        assertTrue(index.list().isEmpty())
    }

    @Test
    fun clearingIndexDeletesAllEntries() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))
        index.record(CachedBookEntry(bookId = "book-1", title = "First", pageCount = 10, cachedPages = 10))
        index.record(CachedBookEntry(bookId = "book-2", title = "Second", pageCount = 8, cachedPages = 2))

        index.clear()

        assertTrue(index.list().isEmpty())
    }

    @Test
    fun corruptedIndexIsPreservedForRecovery() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val indexFile = java.io.File(cacheDir, "book_download_index.json")
        indexFile.writeText("{broken")
        val index = BookDownloadIndex(cacheDir)

        assertTrue(index.list().isEmpty())
        assertFalse(indexFile.exists())
        assertTrue(java.io.File(cacheDir, "book_download_index.json.corrupt").isFile)
    }

    @Test
    fun concurrentRecordsKeepEveryBookEntry() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..32).map { bookNumber ->
                executor.submit {
                    index.record(CachedBookEntry(bookId = "book-$bookNumber", updatedAt = bookNumber.toLong()))
                }
            }
            jobs.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals((1..32).map { "book-$it" }.toSet(), index.list().map { it.bookId }.toSet())
    }

    @Test
    fun cachedBookEntryUsesMetadataTitleAndSeriesTitle() {
        val book = BookDto(
            id = "book-1",
            name = "file.cbz",
            seriesTitle = "Series A",
            metadata = BookMetadataDto(title = "Book Title"),
            media = BookMediaDto(pagesCount = 12),
            oneshot = true
        )

        val entry = cachedBookEntry(book, BookDownloadProgress(completedPages = 6, totalPages = 12), updatedAt = 300)

        assertEquals("book-1", entry.bookId)
        assertEquals("Book Title", entry.title)
        assertEquals("Series A", entry.seriesTitle)
        assertEquals(12, entry.pageCount)
        assertEquals(6, entry.cachedPages)
        assertEquals(true, entry.isOneShot)
        assertEquals(300, entry.updatedAt)
    }
}
