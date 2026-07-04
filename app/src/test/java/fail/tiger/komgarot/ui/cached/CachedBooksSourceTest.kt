package fail.tiger.komgarot.ui.cached

import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CachedBookEntry
import fail.tiger.komgarot.data.local.ReaderPageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachedBooksSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadReadsCachedBooksFromDownloadIndex() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val index = BookDownloadIndex(cacheDir)
        index.record(CachedBookEntry(bookId = "book-1", title = "First", pageCount = 10, cachedPages = 6, updatedAt = 100))
        index.record(CachedBookEntry(bookId = "book-2", title = "Second", pageCount = 8, cachedPages = 8, updatedAt = 200))

        val books = CachedBooksSource(index, cacheDir).load()

        assertEquals(listOf("book-2", "book-1"), books.map { it.bookId })
    }

    @Test
    fun clearAllRemovesCachedBookIndexAndReaderPageFiles() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val index = BookDownloadIndex(cacheDir)
        index.record(CachedBookEntry(bookId = "book-1", title = "First", pageCount = 10, cachedPages = 6, updatedAt = 100))
        val cachedPage = ReaderPageCache.cacheFile(cacheDir, seriesId = "series-1", bookId = "book-1", url = "page-1")
        cachedPage.parentFile?.mkdirs()
        cachedPage.writeText("page")

        CachedBooksSource(index, cacheDir).clearAll()

        assertEquals(emptyList<CachedBookEntry>(), index.list())
        assertFalse(cachedPage.exists())
    }
}
