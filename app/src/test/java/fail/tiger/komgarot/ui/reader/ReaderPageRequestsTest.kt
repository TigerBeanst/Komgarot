package fail.tiger.komgarot.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageRequestsTest {
    @Test
    fun readerPagerActualPreloadRangeMapsPagerPagesToActualPageIndices() {
        val pages = buildReaderPagerPages(
            pageCount = 5,
            previousBook = null,
            nextBook = null
        )

        val result = readerPagerActualPreloadRange(
            pagerPages = pages,
            currentPagerIndex = 3,
            preloadPages = 2
        )

        assertEquals(listOf(1, 3, 4), result)
    }

    @Test
    fun readerPagerActualPreloadRangeSkipsBoundaryAndCurrentPages() {
        val pages = buildReaderPagerPages(
            pageCount = 3,
            previousBook = null,
            nextBook = null
        )

        val result = readerPagerActualPreloadRange(
            pagerPages = pages,
            currentPagerIndex = 1,
            preloadPages = 3
        )

        assertEquals(listOf(1, 2), result)
    }

    @Test
    fun readerPageCacheKeysAreStableForSameUrlAndRequestMode() {
        val url = "https://komga.example.test/api/v1/books/book-1/pages/4?convert=png"

        assertEquals("reader-page:$url", readerPageDiskCacheKey(url))
        assertEquals(
            "reader-page:display:software:$url",
            readerPageMemoryCacheKey(url, allowHardware = false, originalSize = false, cacheVersion = 0)
        )
        assertEquals(
            "reader-page:original:hardware:$url",
            readerPageMemoryCacheKey(url, allowHardware = true, originalSize = true, cacheVersion = 0)
        )
    }

    @Test
    fun readerPageMemoryCacheKeyIncludesCacheVersionAfterInvalidation() {
        val url = "https://komga.example.test/api/v1/books/book-1/pages/4"

        assertEquals(
            "reader-page:display:software:v3:$url",
            readerPageMemoryCacheKey(url, allowHardware = false, originalSize = false, cacheVersion = 3)
        )
    }
}
