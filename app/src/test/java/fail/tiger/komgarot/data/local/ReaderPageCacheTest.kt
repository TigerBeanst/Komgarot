package fail.tiger.komgarot.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderPageCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun clearBookRemovesOnlyMatchingBookPages() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val firstBookPage = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/1")
        val secondBookPage = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-2", "https://example.test/books/book-2/pages/1")

        firstBookPage.writeTextCreatingParents("book-1")
        secondBookPage.writeTextCreatingParents("book-2")

        ReaderPageCache.clearBook(cacheDir, "book-1")

        assertFalse(firstBookPage.exists())
        assertTrue(secondBookPage.isFile)
    }

    @Test
    fun clearBookIgnoresBlankIds() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val page = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/1")

        page.writeTextCreatingParents("book-1")

        ReaderPageCache.clearBook(cacheDir, "")

        assertTrue(page.isFile)
    }

    @Test
    fun clearSeriesRemovesOnlyMatchingSeriesPages() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val firstSeriesPage = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/1")
        val secondSeriesPage = ReaderPageCache.cacheFile(cacheDir, "series-2", "book-2", "https://example.test/books/book-2/pages/1")

        firstSeriesPage.writeTextCreatingParents("series-1")
        secondSeriesPage.writeTextCreatingParents("series-2")

        ReaderPageCache.clearSeries(cacheDir, "series-1")

        assertFalse(firstSeriesPage.exists())
        assertTrue(secondSeriesPage.isFile)
    }

    private fun File.writeTextCreatingParents(value: String) {
        parentFile?.mkdirs()
        writeText(value)
    }
}
