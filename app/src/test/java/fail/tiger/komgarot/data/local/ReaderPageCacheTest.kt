package fail.tiger.komgarot.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderPageCacheTest {
    @Test
    fun removeCachedFileDeletesEveryCompatibleCacheLocation() {
        val cacheDir = temporaryFolder.newFolder("remove-cache")
        val url = "page-url"
        val scoped = ReaderPageCache.cacheFile(cacheDir, "series", "book", url)
        val book = ReaderPageCache.cacheFile(cacheDir, "book", url)
        scoped.parentFile?.mkdirs()
        scoped.writeBytes(byteArrayOf(1))
        book.writeBytes(byteArrayOf(2))

        ReaderPageCache.removeCachedFile(cacheDir, "series", "book", url)

        assertFalse(scoped.exists())
        assertFalse(book.exists())
    }

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

    @Test
    fun clearRemovesReaderPageCacheDirectoryFromCacheDir() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val page = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/1")
        page.writeTextCreatingParents("page")

        ReaderPageCache.clear(cacheDir)

        assertFalse(page.exists())
        assertEquals(0, ReaderPageCache.size(cacheDir))
    }

    @Test
    fun pruneKeepsReaderPagesWithinTargetSize() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val oldestPage = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/1")
        val newestPage = ReaderPageCache.cacheFile(cacheDir, "series-1", "book-1", "https://example.test/books/book-1/pages/2")

        oldestPage.writeBytesCreatingParents(ByteArray(6))
        newestPage.writeBytesCreatingParents(ByteArray(6))
        oldestPage.setLastModified(100)
        newestPage.setLastModified(200)

        ReaderPageCache.prune(cacheDir, maxSizeBytes = 10, targetSizeBytes = 6)

        assertFalse(oldestPage.exists())
        assertTrue(newestPage.isFile)
        assertEquals(6, ReaderPageCache.size(cacheDir))
    }

    private fun File.writeTextCreatingParents(value: String) {
        parentFile?.mkdirs()
        writeText(value)
    }

    private fun File.writeBytesCreatingParents(value: ByteArray) {
        parentFile?.mkdirs()
        writeBytes(value)
    }
}
