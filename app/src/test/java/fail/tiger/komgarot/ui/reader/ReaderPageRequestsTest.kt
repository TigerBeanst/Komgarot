package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.remote.dto.PageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderPageRequestsTest {
    @Test
    fun directImagePageUrlUsesOriginalEndpoint() {
        val page = PageDto(number = 3, mediaType = "image/jpeg", width = 100, height = 200)

        assertEquals(
            "https://komga.test/api/v1/books/book-1/pages/3",
            readerPageUrl("https://komga.test", "book-1", page)
        )
    }

    @Test
    fun nonDirectImagePageUrlRequestsPngConversion() {
        val page = PageDto(number = 4, mediaType = "application/pdf", width = 100, height = 200)

        assertEquals(
            "https://komga.test/api/v1/books/book-1/pages/4?convert=png",
            readerPageUrl("https://komga.test", "book-1", page)
        )
    }

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
    fun pagerProgressJumpSyncsFromBoundaryPages() {
        val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val effectStart = source.indexOf("LaunchedEffect(vm.currentPage)")
        assertTrue(effectStart >= 0)
        val effectEnd = source.indexOf("LaunchedEffect(pagerState.currentPage", effectStart)
        val effectSource = source.substring(effectStart, effectEnd)

        assertTrue(effectSource.contains("pagerState.scrollToPage(targetPage)"))
        assertFalse(effectSource.contains("currentPagerPage is ReaderPagerPage.Actual &&"))
    }

    @Test
    fun readerPreloadBudgetShrinksOnSmallHeaps() {
        assertEquals(2, readerMemoryAwarePreloadPages(requestedPreloadPages = 8, maxMemoryBytes = 192L * 1024L * 1024L))
        assertEquals(3, readerMemoryAwarePreloadPages(requestedPreloadPages = 8, maxMemoryBytes = 384L * 1024L * 1024L))
        assertEquals(5, readerMemoryAwarePreloadPages(requestedPreloadPages = 8, maxMemoryBytes = 768L * 1024L * 1024L))
    }

    @Test
    fun readerPreloadBudgetKeepsZeroPreloadDisabled() {
        assertEquals(0, readerMemoryAwarePreloadPages(requestedPreloadPages = 0, maxMemoryBytes = 512L * 1024L * 1024L))
    }

    @Test
    fun einkPagerComposesAdjacentPageAheadOfTurn() {
        assertEquals(1, readerPagerBeyondViewportPageCount(einkMode = true))
        assertEquals(0, readerPagerBeyondViewportPageCount(einkMode = false))
    }

    @Test
    fun einkReaderRetainsOnlyNormalDecodedPagesInMemory() {
        assertTrue(readerShouldRetainPageInMemory(einkMode = true, renderMode = ReaderPageRenderMode.COIL))
        assertFalse(readerShouldRetainPageInMemory(einkMode = false, renderMode = ReaderPageRenderMode.COIL))
        assertFalse(readerShouldRetainPageInMemory(einkMode = true, renderMode = ReaderPageRenderMode.TILED))
    }

    @Test
    fun hugeReaderPagesUseTiledRendering() {
        assertEquals(
            ReaderPageRenderMode.TILED,
            readerPageRenderMode(PageDto(number = 1, mediaType = "image/jpeg", width = 12000, height = 18000))
        )
        assertEquals(
            ReaderPageRenderMode.TILED,
            readerPageRenderMode(PageDto(number = 2, mediaType = "image/jpeg", width = 1600, height = 16000))
        )
        assertEquals(
            ReaderPageRenderMode.TILED,
            readerPageRenderMode(PageDto(number = 3, mediaType = "image/jpeg", width = 4000, height = 6000))
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 4, mediaType = "image/jpeg", width = 1600, height = 2400))
        )
    }

    @Test
    fun tiledRenderingUsesSharperTilesWhenZoomed() {
        assertEquals(4, readerTileSampleSize(12000, 18000, 1080, 2400, zoomScale = 1f))
        assertEquals(1, readerTileSampleSize(12000, 18000, 1080, 2400, zoomScale = 5f))
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
