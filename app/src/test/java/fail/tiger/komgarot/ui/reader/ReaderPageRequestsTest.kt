package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.LandscapePageSplitOrder
import fail.tiger.komgarot.data.remote.dto.PageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderPageRequestsTest {
    @Test
    fun landscapePagesSplitInConfiguredOrder() {
        val pages = listOf(
            PageDto(number = 1, mediaType = "image/jpeg", width = 2400, height = 1600),
            PageDto(number = 2, mediaType = "image/jpeg", width = 1600, height = 2400)
        )

        val rightFirst = buildReaderPagerPages(
            pageCount = pages.size,
            previousBook = null,
            nextBook = null,
            splitLandscapePages = true,
            splitOrder = LandscapePageSplitOrder.RIGHT_FIRST,
            pageInfo = pages::getOrNull
        ).filterIsInstance<ReaderPagerPage.Actual>()
        val leftFirst = buildReaderPagerPages(
            pageCount = pages.size,
            previousBook = null,
            nextBook = null,
            splitLandscapePages = true,
            splitOrder = LandscapePageSplitOrder.LEFT_FIRST,
            pageInfo = pages::getOrNull
        ).filterIsInstance<ReaderPagerPage.Actual>()

        assertEquals(
            listOf(ReaderPageSegment.RIGHT_HALF, ReaderPageSegment.LEFT_HALF, ReaderPageSegment.FULL),
            rightFirst.map(ReaderPagerPage.Actual::segment)
        )
        assertEquals(
            listOf(ReaderPageSegment.LEFT_HALF, ReaderPageSegment.RIGHT_HALF, ReaderPageSegment.FULL),
            leftFirst.map(ReaderPagerPage.Actual::segment)
        )
    }

    @Test
    fun landscapePageSplittingDefaultsToDisabled() {
        val page = PageDto(number = 1, mediaType = "image/jpeg", width = 2400, height = 1600)
        val actualPages = buildReaderPagerPages(
            pageCount = 1,
            previousBook = null,
            nextBook = null,
            pageInfo = listOf(page)::getOrNull
        ).filterIsInstance<ReaderPagerPage.Actual>()

        assertEquals(listOf(ReaderPageSegment.FULL), actualPages.map(ReaderPagerPage.Actual::segment))
    }

    @Test
    fun decodedLandscapeDimensionsOverrideMissingOrIncorrectMetadata() {
        val pages = listOf(
            PageDto(number = 1, mediaType = "image/jpeg", width = 0, height = 0),
            PageDto(number = 2, mediaType = "image/jpeg", width = 1200, height = 1800)
        )

        val actualPages = buildReaderPagerPages(
            pageCount = pages.size,
            previousBook = null,
            nextBook = null,
            splitLandscapePages = true,
            observedPageLandscape = mapOf(0 to true, 1 to true),
            pageInfo = pages::getOrNull
        ).filterIsInstance<ReaderPagerPage.Actual>()

        assertEquals(
            listOf(
                ReaderPageSegment.RIGHT_HALF,
                ReaderPageSegment.LEFT_HALF,
                ReaderPageSegment.RIGHT_HALF,
                ReaderPageSegment.LEFT_HALF
            ),
            actualPages.map(ReaderPagerPage.Actual::segment)
        )
    }

    @Test
    fun splitPartNumberFollowsConfiguredReadingOrder() {
        assertEquals(1, ReaderPageSegment.RIGHT_HALF.splitPartNumber(LandscapePageSplitOrder.RIGHT_FIRST))
        assertEquals(2, ReaderPageSegment.LEFT_HALF.splitPartNumber(LandscapePageSplitOrder.RIGHT_FIRST))
        assertEquals(1, ReaderPageSegment.LEFT_HALF.splitPartNumber(LandscapePageSplitOrder.LEFT_FIRST))
        assertEquals(2, ReaderPageSegment.RIGHT_HALF.splitPartNumber(LandscapePageSplitOrder.LEFT_FIRST))
        assertNull(ReaderPageSegment.FULL.splitPartNumber(LandscapePageSplitOrder.RIGHT_FIRST))
    }

    @Test
    fun pagerKeysPreservePrimaryHalfWhenDecodedDimensionsEnableSplitting() {
        val fullPage = ReaderPagerPage.Actual(3, ReaderPageSegment.FULL)
        val rightHalf = ReaderPagerPage.Actual(3, ReaderPageSegment.RIGHT_HALF)
        val leftHalf = ReaderPagerPage.Actual(3, ReaderPageSegment.LEFT_HALF)

        assertEquals(
            fullPage.readerPagerStableKey(LandscapePageSplitOrder.RIGHT_FIRST),
            rightHalf.readerPagerStableKey(LandscapePageSplitOrder.RIGHT_FIRST)
        )
        assertNotEquals(
            fullPage.readerPagerStableKey(LandscapePageSplitOrder.RIGHT_FIRST),
            leftHalf.readerPagerStableKey(LandscapePageSplitOrder.RIGHT_FIRST)
        )
        assertEquals(
            fullPage.readerPagerStableKey(LandscapePageSplitOrder.LEFT_FIRST),
            leftHalf.readerPagerStableKey(LandscapePageSplitOrder.LEFT_FIRST)
        )
    }

    @Test
    fun imageAspectRatioRequiresUsableDimensions() {
        assertEquals(0.5f, readerImageAspectRatio(width = 1000, height = 2000) ?: 0f, 0.001f)
        assertNull(readerImageAspectRatio(width = 0, height = 2000))
        assertNull(readerImageAspectRatio(width = 1000, height = 0))
    }

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
            preloadPages = 2,
            direction = 1
        )

        assertEquals(listOf(3, 4, 1), result)
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
        val effectStart = source.indexOf("LaunchedEffect(vm.currentPage, pagerState)")
        assertTrue(effectStart >= 0)
        val effectEnd = source.indexOf("LaunchedEffect(vm.currentPage, memoryAwarePreloadPages", effectStart)
        val effectSource = source.substring(effectStart, effectEnd)

        assertTrue(effectSource.contains("pagerState.scrollToPage(targetPage)"))
        assertTrue(effectSource.contains("readerPagerNeedsProgressSync(currentPagerPage, vm.currentPage)"))
    }

    @Test
    fun pagerProgressSyncKeepsHalfPageSwipesAndStillLeavesBoundaries() {
        val actual = ReaderPagerPage.Actual(pageIndex = 4, segment = ReaderPageSegment.RIGHT_HALF)
        val boundary = ReaderPagerPage.Boundary(ReaderBoundaryDirection.NEXT, null)

        assertFalse(readerPagerNeedsProgressSync(actual, actualPageIndex = 4))
        assertTrue(readerPagerNeedsProgressSync(actual, actualPageIndex = 5))
        assertTrue(readerPagerNeedsProgressSync(boundary, actualPageIndex = 4))
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
    fun quickPreloadCyclesThroughUsefulReaderValues() {
        assertEquals(2, readerNextQuickPreloadPages(0))
        assertEquals(5, readerNextQuickPreloadPages(2))
        assertEquals(8, readerNextQuickPreloadPages(5))
        assertEquals(0, readerNextQuickPreloadPages(8))
    }

    @Test
    fun einkPagerComposesAdjacentPageAheadOfTurn() {
        assertEquals(1, readerPagerBeyondViewportPageCount(einkMode = true))
        assertEquals(0, readerPagerBeyondViewportPageCount(einkMode = false))
    }

    @Test
    fun pagerKeepsLargeCoilPreviewSingleViewport() {
        val pagerPages = buildReaderPagerPages(
            pageCount = 3,
            previousBook = null,
            nextBook = null
        )
        val pageInfos = listOf(
            PageDto(number = 1, mediaType = "image/jpeg", width = 1600, height = 2400),
            PageDto(number = 2, mediaType = "image/jpeg", width = 12000, height = 18000),
            PageDto(number = 3, mediaType = "image/jpeg", width = 1600, height = 2400)
        )

        assertEquals(
            0,
            readerPagerBeyondViewportPageCount(
                einkMode = false,
                pagerPages = pagerPages,
                currentPagerIndex = pagerPages.pagerIndexForActualPage(0),
                pageInfo = pageInfos::getOrNull
            )
        )
    }

    @Test
    fun pagerKeepsNormalPagesSingleViewport() {
        val pagerPages = buildReaderPagerPages(
            pageCount = 3,
            previousBook = null,
            nextBook = null
        )
        val pageInfos = listOf(
            PageDto(number = 1, mediaType = "image/jpeg", width = 1600, height = 2400),
            PageDto(number = 2, mediaType = "image/jpeg", width = 4000, height = 6000),
            PageDto(number = 3, mediaType = "image/jpeg", width = 1600, height = 2400)
        )

        assertEquals(
            0,
            readerPagerBeyondViewportPageCount(
                einkMode = false,
                pagerPages = pagerPages,
                currentPagerIndex = pagerPages.pagerIndexForActualPage(1),
                pageInfo = pageInfos::getOrNull
            )
        )
    }

    @Test
    fun booksContainingTiledPagesKeepStableAdjacentComposition() {
        assertEquals(1, readerPagerBeyondViewportPageCount(einkMode = false, hasTiledPages = true))
        assertEquals(0, readerPagerBeyondViewportPageCount(einkMode = false, hasTiledPages = false))
        assertEquals(1, readerPagerBeyondViewportPageCount(einkMode = true, hasTiledPages = false))
        assertEquals(
            1,
            readerPagerBeyondViewportPageCount(
                einkMode = false,
                hasTiledPages = false,
                hasSplitPages = true
            )
        )
    }

    @Test
    fun screenSizedDecodeKeepsAllPagesOnCoilFirstFrame() {
        assertFalse(readerPageNeedsStableAdjacentComposition(null))
        assertFalse(
            readerPageNeedsStableAdjacentComposition(
                PageDto(number = 1, mediaType = "image/jpeg", width = 0, height = 0)
            )
        )
        assertFalse(
            readerPageNeedsStableAdjacentComposition(
                PageDto(number = 2, mediaType = "image/jpeg", width = 12000, height = 18000)
            )
        )
        assertFalse(
            readerPageNeedsStableAdjacentComposition(
                PageDto(number = 3, mediaType = "image/jpeg", width = 1600, height = 2400)
            )
        )
    }

    @Test
    fun backwardPreloadPrioritizesPreviousPagesBeforeOppositeNeighbor() {
        val pages = buildReaderPagerPages(
            pageCount = 6,
            previousBook = null,
            nextBook = null
        )

        val result = readerPagerActualPreloadRange(
            pagerPages = pages,
            currentPagerIndex = pages.pagerIndexForActualPage(3),
            preloadPages = 3,
            direction = -1
        )

        assertEquals(listOf(2, 1, 0, 4), result)
    }

    @Test
    fun pagerTargetImmediatelyControlsReversePreloadDirection() {
        val pages = buildReaderPagerPages(pageCount = 6, previousBook = null, nextBook = null)

        assertEquals(
            -1,
            readerPagerPreloadDirection(
                pagerPages = pages,
                currentPagerIndex = pages.pagerIndexForActualPage(3),
                targetPagerIndex = pages.pagerIndexForActualPage(2),
                fallbackDirection = 1
            )
        )
    }

    @Test
    fun readerRetainsOnlyNormalDecodedPagesInMemory() {
        assertTrue(readerShouldRetainPageInMemory(einkMode = true, renderMode = ReaderPageRenderMode.COIL))
        assertTrue(readerShouldRetainPageInMemory(einkMode = false, renderMode = ReaderPageRenderMode.COIL))
        assertFalse(readerShouldRetainPageInMemory(einkMode = true, renderMode = ReaderPageRenderMode.TILED))
        assertFalse(readerShouldRetainPageInMemory(einkMode = false, renderMode = ReaderPageRenderMode.TILED))
    }

    @Test
    fun largeReaderPagesUseBoundedCoilPreview() {
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 1, mediaType = "image/jpeg", width = 12000, height = 18000))
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 2, mediaType = "image/jpeg", width = 1600, height = 16000))
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 3, mediaType = "image/jpeg", width = 4000, height = 6000))
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 4, mediaType = "image/jpeg", width = 1600, height = 2400))
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(PageDto(number = 5, mediaType = "image/jpeg", width = 0, height = 0))
        )
        assertTrue(readerBitmapExceedsCanvasSafeSize(width = 9049, height = 9049))
    }

    @Test
    fun actualBitmapByteCountTakesPriorityOverDensityScaledDimensions() {
        assertTrue(
            readerBitmapExceedsCanvasSafeSize(
                width = 1200,
                height = 1800,
                bitmapByteCount = 234_000_000L
            )
        )
        assertFalse(
            readerBitmapExceedsCanvasSafeSize(
                width = 12000,
                height = 18000,
                bitmapByteCount = 8L * 1024L * 1024L
            )
        )
    }

    @Test
    fun scrollDisplayDecodeKeepsQualityHeadroomWithinMemoryBudget() {
        assertEquals(
            ReaderDisplayDecodeSize(width = 1350, height = 1939),
            readerDisplayDecodeSize(
                layoutWidth = 1080,
                layoutHeight = 1551,
                qualityScale = 1.25f,
                maxDecodedBytes = 16L * 1024L * 1024L
            )
        )
        val constrained = readerDisplayDecodeSize(
            layoutWidth = 2400,
            layoutHeight = 3600,
            qualityScale = 1.25f,
            maxDecodedBytes = 8L * 1024L * 1024L
        )
        assertTrue(constrained.width.toLong() * constrained.height.toLong() * 4L <= 8L * 1024L * 1024L)
    }

    @Test
    fun lowMemoryDevicesKeepLargePagesOnBoundedCoilPreview() {
        val page = PageDto(number = 1, mediaType = "image/jpeg", width = 4000, height = 6000)

        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(page, maxMemoryBytes = 192L * 1024L * 1024L)
        )
        assertEquals(
            ReaderPageRenderMode.COIL,
            readerPageRenderMode(page, maxMemoryBytes = 512L * 1024L * 1024L)
        )
    }

    @Test
    fun tiledRenderingUsesSharperTilesWhenZoomed() {
        assertEquals(8, readerTileSampleSize(12000, 18000, 1080, 2400, zoomScale = 1f))
        assertEquals(2, readerTileSampleSize(12000, 18000, 1080, 2400, zoomScale = 5f))
    }

    @Test
    fun widthFitLongPageDetailTilesStaySharperThanItsPreview() {
        assertEquals(
            1,
            readerTileSampleSize(
                imageWidth = 1600,
                imageHeight = 16000,
                viewportWidth = 1080,
                viewportHeight = 2400,
                zoomScale = 1f,
                fillWidth = true
            )
        )
    }

    @Test
    fun tiledPreviewUsesSafeScreenSizedDecodeBeforeZoom() {
        assertFalse(shouldDrawReaderTiles(zoomScale = 1f))
        assertFalse(shouldDrawReaderTiles(zoomScale = 1.04f))
        assertTrue(shouldDrawReaderTiles(zoomScale = 1.05f))
        assertTrue(shouldDrawReaderTiles(zoomScale = 1f, previewNeedsDetailTiles = true))
        assertEquals(8, readerPreviewSampleSize(9049, 9049, 1080, 2400, fillWidth = false))
        assertEquals(1, readerPreviewSampleSize(1600, 16000, 1080, 2400, fillWidth = true))
    }

    @Test
    fun renderBudgetCapsCurrentPreviewAdjacentPreviewsAndActiveTilesTogether() {
        val megabyte = 1024L * 1024L
        val budget = readerRenderMemoryBudget(128L * megabyte)

        assertTrue(budget.hardLimitBytes <= 36L * megabyte)
        assertTrue(
            budget.currentPreviewBytes + budget.adjacentPreviewBytes * 2L + budget.activeTileBytes <=
                budget.hardLimitBytes
        )
        assertEquals(
            budget.currentPreviewBytes + budget.adjacentPreviewBytes * 2L,
            readerPreviewCacheBytes(128L * megabyte)
        )
    }

    @Test
    fun visibleTileWorkingSetCanExpandCacheWithinRenderBudget() {
        val megabyte = 1024L * 1024L
        val budget = readerRenderMemoryBudget(512L * megabyte)
        val visibleBytes = readerVisibleTileWorkingSetBytes(
            imageWidth = 10240,
            imageHeight = 12288,
            firstColumn = 0,
            lastColumn = 4,
            firstRow = 0,
            lastRow = 5,
            sampleSize = 4
        )

        assertTrue(visibleBytes > budget.activeTileBytes)
        assertEquals(visibleBytes, readerActiveTileCacheBytes(visibleBytes, budget))
        val cappedTileBytes = readerActiveTileCacheBytes(Long.MAX_VALUE, budget)
        assertTrue(
            budget.currentPreviewBytes + budget.adjacentPreviewBytes * 2L + cappedTileBytes <=
                budget.hardLimitBytes
        )
    }

    @Test
    fun cachedPreviewQualityUsesActualBitmapDimensions() {
        assertTrue(
            readerPreviewBitmapMeetsDisplayTarget(
                bitmapWidth = 1080,
                bitmapHeight = 2400,
                imageWidth = 4000,
                imageHeight = 6000,
                viewportWidth = 1080,
                viewportHeight = 2400,
                fillWidth = false
            )
        )
        assertFalse(
            readerPreviewBitmapMeetsDisplayTarget(
                bitmapWidth = 540,
                bitmapHeight = 800,
                imageWidth = 4000,
                imageHeight = 6000,
                viewportWidth = 1080,
                viewportHeight = 2400,
                fillWidth = false
            )
        )
    }

    @Test
    fun lowMemoryTileSampleKeepsWholeVisibleWorkingSetResident() {
        val budget = readerRenderMemoryBudget(128L * 1024L * 1024L)
        val cacheBytes = readerActiveTileCacheBytes(Long.MAX_VALUE, budget)

        val sampleSize = readerTileSampleSizeForCache(
            imageWidth = 12000,
            imageHeight = 18000,
            visibleLeft = 0,
            visibleTop = 0,
            visibleRight = 12000,
            visibleBottom = 18000,
            initialSampleSize = 8,
            maxCacheBytes = cacheBytes
        )

        assertEquals(16, sampleSize)
        val tileSize = 512 * sampleSize
        val visibleBytes = readerVisibleTileWorkingSetBytes(
            imageWidth = 12000,
            imageHeight = 18000,
            firstColumn = 0,
            lastColumn = (12000 - 1) / tileSize,
            firstRow = 0,
            lastRow = (18000 - 1) / tileSize,
            sampleSize = sampleSize
        )
        assertTrue(visibleBytes <= cacheBytes)
        assertEquals(
            64,
            readerTileSampleSizeForCache(
                imageWidth = 60000,
                imageHeight = 90000,
                visibleLeft = 0,
                visibleTop = 0,
                visibleRight = 60000,
                visibleBottom = 90000,
                initialSampleSize = 32,
                maxCacheBytes = cacheBytes
            )
        )
    }

    @Test
    fun previewDecodePlanReportsWhenMemoryFallbackNeedsVisibleDetailTiles() {
        val plan = readerPreviewDecodePlan(
            imageWidth = 1600,
            imageHeight = 16000,
            viewportWidth = 1080,
            viewportHeight = 2400,
            fillWidth = true,
            qualityScale = READER_CURRENT_PREVIEW_QUALITY_SCALE,
            maxDecodedBytes = 16L * 1024L * 1024L
        )

        assertEquals(4, plan.sampleSize)
        assertFalse(plan.meetsQualityTarget)
        assertFalse(plan.meetsDisplayTarget)
    }

    @Test
    fun zoomOffsetsLimitTileDecodeToTheTransformedVisibleArea() {
        val bounds = readerZoomVisibleBounds(
            viewportWidth = 1080,
            viewportHeight = 2400,
            zoomScale = 3f,
            zoomOffsetX = -540f,
            zoomOffsetY = 600f
        )

        assertEquals(540f, bounds.left, 0.01f)
        assertEquals(900f, bounds.right, 0.01f)
        assertEquals(600f, bounds.top, 0.01f)
        assertEquals(1400f, bounds.bottom, 0.01f)
    }

    @Test
    fun adjacentPreviewAllowsTinyUpscaleToReduceDecodedPixels() {
        assertEquals(
            4,
            readerPreviewSampleSize(
                imageWidth = 5000,
                imageHeight = 7000,
                viewportWidth = 1080,
                viewportHeight = 2400,
                fillWidth = false,
                qualityScale = READER_ADJACENT_PREVIEW_QUALITY_SCALE,
                maxUpscaleFraction = READER_PREVIEW_MAX_UPSCALE_FRACTION
            )
        )
    }

    @Test
    fun currentPreviewKeepsQualityHeadroomForMangaText() {
        assertEquals(
            2,
            readerPreviewSampleSize(
                imageWidth = 5000,
                imageHeight = 7000,
                viewportWidth = 1080,
                viewportHeight = 2400,
                fillWidth = false,
                qualityScale = READER_CURRENT_PREVIEW_QUALITY_SCALE,
                maxUpscaleFraction = READER_PREVIEW_MAX_UPSCALE_FRACTION
            )
        )
    }

    @Test
    fun largeMangaPageKeepsDisplayResolutionInsideLowMemoryBudget() {
        val budget = readerRenderMemoryBudget(128L * 1024L * 1024L)
        val plan = readerPreviewDecodePlan(
            imageWidth = 4206,
            imageHeight = 6040,
            viewportWidth = 1080,
            viewportHeight = 2400,
            fillWidth = false,
            qualityScale = READER_CURRENT_PREVIEW_QUALITY_SCALE,
            maxDecodedBytes = budget.currentPreviewBytes
        )

        assertEquals(4, plan.sampleSize)
        assertTrue(plan.meetsDisplayTarget)
        assertFalse(plan.meetsQualityTarget)
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

    @Test
    fun splitPageSegmentsUseIndependentMemoryCacheKeys() {
        val url = "https://komga.test/api/v1/books/book-1/pages/1"

        val left = readerPageMemoryCacheKey(
            url = url,
            allowHardware = false,
            originalSize = false,
            pageSegment = ReaderPageSegment.LEFT_HALF
        )
        val right = readerPageMemoryCacheKey(
            url = url,
            allowHardware = false,
            originalSize = false,
            pageSegment = ReaderPageSegment.RIGHT_HALF
        )

        assertTrue(left.contains("left_half"))
        assertTrue(right.contains("right_half"))
        assertFalse(left == right)
    }
}
