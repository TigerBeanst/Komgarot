package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageQualityStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
    private val requestSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderPageRequests.kt").readText()
    private val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()

    @Test
    fun readerDisplayRequestsDeclarePageSpecificDecodeSize() {
        val displayRequestBlock = Regex(
            "private fun rememberReaderPageRequest[\\s\\S]*?return ReaderPageImageRequestState"
        ).find(source)?.value.orEmpty()
        val pagerStart = source.indexOf("fun PagerReader(")
        val pagerEnd = source.indexOf("private fun saveBitmapToGallery", pagerStart)
        val pagerSource = source.substring(pagerStart, pagerEnd)
        val pagerRequestStart = pagerSource.indexOf("val pageRequestState = rememberReaderPageRequest(")
        val pagerRequestEnd = pagerSource.indexOf(")", pagerRequestStart)
        val pagerRequestSource = pagerSource.substring(pagerRequestStart, pagerRequestEnd)
        val scrollStart = source.indexOf("fun ScrollReader(")
        val scrollSource = source.substring(scrollStart)

        assertTrue(displayRequestBlock.contains("originalSize: Boolean"))
        assertTrue(displayRequestBlock.contains("originalSize = originalSize"))
        assertTrue(pagerRequestSource.contains("originalSize = false"))
        assertTrue(pagerSource.contains("displayQualityScale = if (isDisplayTarget) 1.25f else 1f"))
        assertTrue(pagerSource.contains("displayMaxDecodedBytes = if (isDisplayTarget)"))
        assertTrue(scrollSource.contains("originalSize = false"))
        assertTrue(scrollSource.contains("displayQualityScale = READER_CURRENT_PREVIEW_QUALITY_SCALE"))
        assertTrue(scrollSource.contains("displayMaxDecodedBytes = scrollRenderMemoryBudget.currentPreviewBytes"))
        assertFalse(scrollSource.contains("displayQualityScale = if (index == vm.currentPage)"))
        assertTrue(requestSource.contains("size(displayDecodeSize.width, displayDecodeSize.height)"))
    }

    @Test
    fun scrollReaderKeepsRequestsAndPageGeometryStableWhileCurrentPageChanges() {
        val scrollStart = source.indexOf("fun ScrollReader(")
        val scrollSource = source.substring(scrollStart)

        assertTrue(scrollSource.contains(".aspectRatio(pageAspectRatio)"))
        assertTrue(scrollSource.contains("modifier = Modifier.fillMaxSize()"))
        assertTrue(scrollSource.contains("listPositionInitialized"))
        assertTrue(scrollSource.contains("scrollSessionObserved"))
        assertTrue(scrollSource.contains("listState.isScrollInProgress to dominantPage"))
        assertTrue(scrollSource.contains("key = { index, url -> \"${'$'}index:${'$'}url\" }"))
        assertTrue(scrollSource.contains("val resolvedPageAspectRatios"))
        assertTrue(scrollSource.contains("val decodedAspectRatio = readerImageAspectRatio("))
        assertTrue(scrollSource.contains("resolvedPageAspectRatios[url] = decodedAspectRatio"))
        assertTrue(scrollSource.contains("contentScale = ContentScale.Fit"))
        assertTrue(scrollSource.contains("displayWidthPx / pageAspectRatio"))
        assertFalse(scrollSource.contains(".wrapContentHeight()"))
    }

    @Test
    fun readerPreloadsCacheFilesWithoutDecodingImages() {
        assertTrue(source.contains("ensureReaderPageFileCached("))
        assertFalse(source.contains("imageLoader.enqueue("))
    }

    @Test
    fun pagerUsesSplitSegmentsForLandscapePages() {
        val pagerStart = source.indexOf("fun PagerReader(")
        val pagerEnd = source.indexOf("private fun saveBitmapToGallery", pagerStart)
        val pagerSource = source.substring(pagerStart, pagerEnd)

        assertTrue(pagerSource.contains("vm.prefs.splitLandscapePages.collectAsStateWithLifecycle"))
        assertTrue(pagerSource.contains("vm.prefs.landscapePageSplitOrder.collectAsStateWithLifecycle"))
        assertTrue(pagerSource.contains("splitLandscapePages = splitLandscapePages"))
        assertTrue(pagerSource.contains("val pageSegment = readerPage.segment"))
        assertTrue(pagerSource.contains("viewportWidthPx * 2"))
        assertTrue(pagerSource.contains("pageSegment = pageSegment"))
        assertTrue(pagerSource.contains("forReaderPageSegment(pageSegment)"))
        assertTrue(pagerSource.contains("key(vm.currentBookId, splitLandscapePages, splitOrder)"))
    }

    @Test
    fun readerPageRequestsKeepDecodedImagesOutOfCoilMemoryCache() {
        assertTrue(requestSource.contains("retainInMemory: Boolean = false"))
        assertTrue(requestSource.contains(".memoryCachePolicy(if (retainInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED)"))
        assertTrue(requestSource.contains(".placeholderMemoryCacheKey(memoryKey)"))
        assertTrue(requestSource.contains("if (originalSize) {"))
        assertTrue(requestSource.contains("size(Size.ORIGINAL)"))
        assertTrue(requestSource.contains(".allowRgb565(false)"))
        assertTrue(requestSource.contains("readerDisplayDecodeSize("))
        assertTrue(requestSource.contains("maxDecodedBytes = displayMaxDecodedBytes"))
    }

    @Test
    fun pagerPrecomposesEinkPagesAndAdjacentHugePages() {
        assertTrue(requestSource.contains("fun readerPagerBeyondViewportPageCount("))
        assertTrue(requestSource.contains("fun readerShouldRetainPageInMemory("))
        assertTrue(source.contains("beyondViewportPageCount = readerPagerBeyondViewportPageCount("))
        assertTrue(source.contains("hasTiledPages = hasTiledPages"))
        assertTrue(source.contains("retainInMemory = readerShouldRetainPageInMemory("))
    }

    @Test
    fun einkReaderUsesLightweightTapPathWithoutChangingNormalZoomPath() {
        val contentStart = source.indexOf("private fun ZoomableReaderPageContent(")
        val contentEnd = source.indexOf("@Composable\nprivate fun ReaderBoundaryPage", contentStart)
        val contentSource = source.substring(contentStart, contentEnd)

        assertTrue(contentSource.contains("if (einkMode)"))
        assertTrue(contentSource.contains("detectTapGestures("))
        assertTrue(contentSource.contains("onLongPress = { onLongPress() }"))
        assertTrue(contentSource.contains(".zoomable("))
    }

    @Test
    fun readerPreloadUsesMemoryAwareBudget() {
        assertTrue(requestSource.contains("fun readerMemoryAwarePreloadPages("))
        assertTrue(source.contains("readerMemoryAwarePreloadPages(preloadPages)"))
        assertTrue(source.contains("val memoryAwarePreloadPages = readerMemoryAwarePreloadPages(preloadPages)"))
        assertTrue(source.contains("preloadPages = memoryAwarePreloadPages"))
    }

    @Test
    fun readerCompactControlsUseExplicitHighContrastColors() {
        assertTrue(source.contains("ReaderQuickChipLabel("))
        assertTrue(source.contains("readerQuickAssistChipColors()"))
        assertTrue(source.contains("ReaderQuickChipContainerColor"))
        assertTrue(source.contains("ReaderQuickChipTextColor"))
        assertTrue(source.contains("ReaderAiTranslationProgressControl("))
    }

    @Test
    fun readerUsesBoundedCoilPreviewBeforeTiledFallback() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(requestSource.contains("enum class ReaderPageRenderMode"))
        assertTrue(requestSource.contains("fun readerPageRenderMode("))
        assertTrue(requestSource.contains("): ReaderPageRenderMode = ReaderPageRenderMode.COIL"))
        assertTrue(source.contains("vm.pageInfo(actualPageIndex)"))
        assertTrue(source.contains("readerPageRenderMode(pageInfo)"))
        assertTrue(source.contains("else ReaderPageRenderMode.COIL"))
        assertTrue(source.contains("var forceTiledRender by remember(pageUrl, renderMode)"))
        assertTrue(requestSource.contains("readerBitmapExceedsCanvasSafeSize("))
        assertTrue(requestSource.contains("fun readerDrawableExceedsCanvasSafeSize("))
        assertTrue(requestSource.contains("val bitmap = (drawable as? BitmapDrawable)?.bitmap"))
        assertTrue(requestSource.contains("bitmap?.byteCount"))
        assertTrue(requestSource.contains("if (bitmap?.isRecycled == true) return true"))
        assertEquals(2, Regex("readerDrawableExceedsCanvasSafeSize\\(drawable\\)").findAll(source).count())
        assertTrue(source.contains("forceTiledRender = true"))
        assertTrue(source.contains("val effectiveRenderMode = if (forceTiledRender) ReaderPageRenderMode.TILED else renderMode"))
        assertTrue(source.contains("ReaderTiledImage("))
        assertTrue(tiledSource.contains("BitmapRegionDecoder"))
        assertTrue(tiledSource.contains("decodeRegion("))
        assertTrue(tiledSource.contains("LruCache<String, Bitmap>"))
        assertTrue(tiledSource.contains("private const val READER_TILE_SOURCE_SIZE = 512"))
        assertTrue(tiledSource.contains("tileCache.resize(readerActiveTileCacheBytes(visibleTileBytes)"))
        assertTrue(tiledSource.contains("bitmap.recycle()"))
        assertTrue(tiledSource.contains("Bitmap.Config.ARGB_8888"))
        assertTrue(tiledSource.contains("KomgarotApp"))
        assertTrue(tiledSource.contains("Request.Builder()"))
        assertTrue(tiledSource.contains("ReaderPageCache.entry(context, seriesId, bookId, url)"))
        assertTrue(tiledSource.contains("ReaderPageCache.commit(context, entry, maxSizeBytes)"))
        assertTrue(tiledSource.contains("ReaderPageCache.discard(entry)"))
    }

    @Test
    fun tiledRendererDoesNotDecodeRegionsOnDrawThread() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val drawStart = tiledSource.indexOf("override fun onDraw(canvas: Canvas)")
        val drawEnd = tiledSource.indexOf("private fun", drawStart)
        assertTrue(drawStart >= 0)
        assertTrue(drawEnd > drawStart)
        val drawSource = tiledSource.substring(drawStart, drawEnd)

        assertTrue(tiledSource.contains("ReaderPageLoadCoordinator.executeTile"))
        assertTrue(tiledSource.contains("ReaderPageLoadCoordinator.executePreview"))
        assertTrue(tiledSource.contains("private val pendingTileKeys"))
        assertTrue(drawSource.contains("requestTileDecode("))
        assertFalse(drawSource.contains("decodeRegion("))
        assertFalse(drawSource.contains("tileBitmap("))
    }

    @Test
    fun tiledRendererDrawsPreviewBeforeZoomTiles() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val drawStart = tiledSource.indexOf("override fun onDraw(canvas: Canvas)")
        val drawEnd = tiledSource.indexOf("private fun", drawStart)
        assertTrue(drawStart >= 0)
        assertTrue(drawEnd > drawStart)
        val drawSource = tiledSource.substring(drawStart, drawEnd)

        assertTrue(tiledSource.contains("private var previewBitmap: Bitmap? = null"))
        assertTrue(tiledSource.contains("requestPreviewDecode("))
        assertTrue(drawSource.contains("drawPreviewBitmap("))
        assertTrue(drawSource.contains("if (!shouldDrawReaderTiles(zoomScale, previewNeedsDetailTiles)) return"))
        assertTrue(drawSource.indexOf("drawPreviewBitmap(") < drawSource.indexOf("if (!shouldDrawReaderTiles(zoomScale, previewNeedsDetailTiles)) return"))
        assertTrue(tiledSource.contains("readerPreviewSampleSize("))
    }

    @Test
    fun tiledRendererBuildsInitialPreviewWithDecoderOpen() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val openStart = tiledSource.indexOf("private fun openImageAsync(")
        val openEnd = tiledSource.indexOf("override fun onDraw", openStart)
        assertTrue(openStart >= 0)
        assertTrue(openEnd > openStart)
        val openSource = tiledSource.substring(openStart, openEnd)

        assertTrue(openSource.contains("decodeReaderPreviewFile("))
        assertTrue(openSource.contains("publishOpenedPreview(openedPreviewResult, openedPreviewKey, openedPreviewCacheKey)"))
        assertTrue(tiledSource.contains("previewBitmap = result.bitmap"))
        assertTrue(tiledSource.contains("previewKey = key"))
        assertTrue(tiledSource.contains("previewCacheKey = cacheKey"))
        assertTrue(tiledSource.contains("override fun onSizeChanged("))
        assertTrue(tiledSource.contains("requestPreviewDecode(key)"))
    }

    @Test
    fun prefetchedPreviewIsPublishedBeforeDisplayQualityUpgrade() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val openStart = tiledSource.indexOf("private fun openImageAsync(")
        val openEnd = tiledSource.indexOf("override fun onDraw", openStart)
        val openSource = tiledSource.substring(openStart, openEnd)
        val publishIndex = openSource.indexOf("publishOpenedPreview(")
        val configurationChangedIndex = openSource.indexOf("openedPreviewQualityScale != previewQualityScale")

        assertTrue(publishIndex >= 0)
        assertTrue(configurationChangedIndex > publishIndex)
    }

    @Test
    fun tiledRendererReusesPreviewBitmapsAcrossShortRecomposition() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(tiledSource.contains("private val readerPreviewBitmapCache"))
        assertTrue(tiledSource.contains("readerPreviewCacheKey("))
        assertTrue(tiledSource.contains("readerPreviewBitmapCache.get("))
        assertTrue(tiledSource.contains("readerPreviewBitmapCache.put("))
    }

    @Test
    fun tiledRendererReportsReadyAfterPreviewBitmapExists() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(tiledSource.contains("var previewReady by remember(file)"))
        assertTrue(tiledSource.contains("view.onPreviewReady = {"))
        assertTrue(tiledSource.contains("if (!previewReady)"))
        assertTrue(tiledSource.contains("previewReady = true"))
        assertTrue(tiledSource.contains("onImageReady()"))
        assertTrue(tiledSource.contains("if (file != null && !previewReady) {"))
        assertFalse(tiledSource.contains("view.setImageFile(file, fillWidth, zoomScale)\n                    onImageReady()"))
    }

    @Test
    fun tiledRendererKeepsPreviewAndReportsDecodeFailure() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val releaseStart = tiledSource.indexOf("private fun releaseDecoder(")
        val releaseEnd = tiledSource.indexOf("\n    }\n}", releaseStart)
        val releaseSource = tiledSource.substring(releaseStart, releaseEnd)

        assertTrue(tiledSource.contains("previewQualityScale"))
        assertTrue(tiledSource.contains("onDecodeError"))
        assertTrue(tiledSource.contains("onAttachedToWindow"))
        assertTrue(tiledSource.contains("decoderReleasePending"))
        assertTrue(releaseSource.contains("ReaderPageLoadCoordinator.executePreview"))
        assertTrue(tiledSource.contains("key(retryKey)"))
        assertTrue(tiledSource.contains("Os.stat(file.absolutePath)"))
        assertFalse(tiledSource.contains("tileDecodeExecutor.shutdownNow()"))
    }

    @Test
    fun readerPassesActivePageQualityAndUsesStableLargePageComposition() {
        assertTrue(source.contains("val hasTiledPages = vm.pageUrls.indices.any"))
        assertTrue(source.contains("direction = targetPreloadDirection"))
        assertTrue(source.contains("previewQualityScale = if (isSettledPage || (isDisplayTarget && !pagerState.isScrollInProgress))"))
        assertTrue(source.contains("previewQualityScale = if (index == vm.currentPage)"))
        assertTrue(source.contains("isActive = isDisplayTarget"))
        assertTrue(source.contains("isActive = index == vm.currentPage"))
        assertTrue(source.contains("priority = ReaderPageLoadPriority.PREFETCH"))
        assertTrue(source.contains("async {"))
        assertTrue(source.contains("awaitAll()"))
    }

    @Test
    fun tiledRendererUsesIndependentPreviewFallbackAndZoomAwareTileBounds() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(tiledSource.contains("BitmapFactory.decodeFile("))
        assertTrue(tiledSource.contains("readerZoomVisibleBounds("))
        assertTrue(tiledSource.contains("zoomOffsetX"))
        assertTrue(tiledSource.contains("zoomOffsetY"))
        assertTrue(tiledSource.contains("tileDecodeGeneration"))
        assertTrue(tiledSource.contains("ReaderPageCache.removeCachedFile("))
        assertTrue(tiledSource.contains("onTileDecodeRecovered"))
        assertTrue(tiledSource.contains("if (decoder == null && isActive)"))
        assertTrue(tiledSource.contains("openImageAsync(file, decoderGeneration)"))
    }

    @Test
    fun tiledRendererOpensDecoderOffDrawThread() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val setterStart = tiledSource.indexOf("fun setImageFile(")
        val setterEnd = tiledSource.indexOf("private fun openImageAsync", setterStart)
        assertTrue(setterStart >= 0)
        assertTrue(setterEnd > setterStart)
        val setterSource = tiledSource.substring(setterStart, setterEnd)

        assertTrue(tiledSource.contains("openImageAsync("))
        assertFalse(setterSource.contains("newReaderBitmapRegionDecoder(file)"))
    }

    @Test
    fun readerExportActionsKeepOriginalImageSize() {
        val exportStart = source.indexOf("suspend fun loadBitmap(pageUrl: String)")
        val exportEnd = source.indexOf("val result = imageLoader.execute(req)", exportStart)
        val exportSource = source.substring(exportStart, exportEnd)

        assertTrue(exportSource.contains("originalSize = true"))
    }

    @Test
    fun imageLoaderUsesSmallerMemoryCacheForReaderPages() {
        assertTrue(appSource.contains(".maxSizePercent(0.12)"))
    }
}
