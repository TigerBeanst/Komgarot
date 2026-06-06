package fail.tiger.komgarot.ui.cached

import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CachedBookEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachedBooksSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadReadsCachedBooksFromDownloadIndex() {
        val index = BookDownloadIndex(temporaryFolder.newFolder("cache"))
        index.record(CachedBookEntry(bookId = "book-1", title = "First", pageCount = 10, cachedPages = 6, updatedAt = 100))
        index.record(CachedBookEntry(bookId = "book-2", title = "Second", pageCount = 8, cachedPages = 8, updatedAt = 200))

        val books = CachedBooksSource(index).load()

        assertEquals(listOf("book-2", "book-1"), books.map { it.bookId })
    }
}
