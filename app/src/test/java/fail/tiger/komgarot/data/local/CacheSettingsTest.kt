package fail.tiger.komgarot.data.local

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheSettingsTest {
    @Test
    fun defaultCacheSizeIsTwoGigabytes() {
        assertEquals(2048, CacheSizeOption.default.sizeMb)
        assertEquals(2L * 1024L * 1024L * 1024L, CacheSizeOption.default.bytes)
    }

    @Test
    fun cacheSizeOptionsUseStableMegabyteValues() {
        assertEquals(listOf(256, 512, 1024, 2048, 4096), CacheSizeOption.values.map { it.sizeMb })
    }

    @Test
    fun unknownCacheSizeFallsBackToDefault() {
        assertEquals(CacheSizeOption.default, CacheSizeOption.fromMb(123))
    }

    @Test
    fun readerPageCacheCountsOnlyCachedBookFilesForBookSummary() {
        val cacheDir = Files.createTempDirectory("reader-cache-test").toFile()
        try {
            val first = ReaderPageCache.cacheFile(cacheDir, seriesId = "series", bookId = "book-a", url = "page-1")
            val second = ReaderPageCache.cacheFile(cacheDir, seriesId = "series", bookId = "book-a", url = "page-2")
            val other = ReaderPageCache.cacheFile(cacheDir, seriesId = "series", bookId = "book-b", url = "page-1")
            first.parentFile?.mkdirs()
            first.writeBytes(ByteArray(7))
            second.writeBytes(ByteArray(11))
            other.writeBytes(ByteArray(13))

            val summaryBytes = ReaderPageCache.cachedBooksSize(
                cacheDir,
                listOf(CachedBookEntry(bookId = "book-a", cachedPages = 2, pageCount = 2))
            )

            assertEquals(18L, summaryBytes)
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
