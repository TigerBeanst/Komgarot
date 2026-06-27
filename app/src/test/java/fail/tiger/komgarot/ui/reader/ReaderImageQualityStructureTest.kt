package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageQualityStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
    private val requestSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderPageRequests.kt").readText()
    private val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()

    @Test
    fun readerDisplayRequestsOriginalImages() {
        val displayRequestBlock = Regex(
            "private fun rememberReaderPageRequest[\\s\\S]*?return ReaderPageImageRequestState"
        ).find(source)?.value.orEmpty()

        assertTrue(displayRequestBlock.contains("originalSize = true"))
    }

    @Test
    fun readerPreloadsCacheFilesWithoutDecodingImages() {
        assertTrue(source.contains("ensureReaderPageFileCached("))
        assertFalse(source.contains("imageLoader.enqueue("))
    }

    @Test
    fun readerPageRequestsKeepDecodedImagesOutOfCoilMemoryCache() {
        assertTrue(requestSource.contains("retainInMemory: Boolean = false"))
        assertTrue(requestSource.contains(".memoryCachePolicy(if (retainInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED)"))
        assertTrue(requestSource.contains(".placeholderMemoryCacheKey(memoryKey)"))
        assertTrue(requestSource.contains("size(Size.ORIGINAL)"))
        assertTrue(requestSource.contains(".allowRgb565(false)"))
        assertFalse(requestSource.contains("readerDisplayDecodeSize"))
    }

    @Test
    fun pagerPrecomposesEinkPagesAndAdjacentHugePages() {
        assertTrue(requestSource.contains("fun readerPagerBeyondViewportPageCount("))
        assertTrue(requestSource.contains("fun readerShouldRetainPageInMemory("))
        assertTrue(source.contains("beyondViewportPageCount = readerPagerBeyondViewportPageCount("))
        assertTrue(source.contains("pagerPages = pagerPages"))
        assertTrue(source.contains("currentPagerIndex = pagerState.currentPage"))
        assertTrue(source.contains("pageInfo = vm::pageInfo"))
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
    fun readerUsesTiledRendererForHugePages() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(requestSource.contains("enum class ReaderPageRenderMode"))
        assertTrue(requestSource.contains("fun readerPageRenderMode(page: PageDto): ReaderPageRenderMode"))
        assertTrue(source.contains("vm.pageInfo(actualPageIndex)"))
        assertTrue(source.contains("readerPageRenderMode(pageInfo)"))
        assertTrue(source.contains("var forceTiledRender by remember(pageUrl, renderMode)"))
        assertTrue(source.contains("readerBitmapExceedsCanvasSafeSize("))
        assertTrue(source.contains("forceTiledRender = true"))
        assertTrue(source.contains("val effectiveRenderMode = if (forceTiledRender) ReaderPageRenderMode.TILED else renderMode"))
        assertTrue(source.contains("ReaderTiledImage("))
        assertTrue(tiledSource.contains("BitmapRegionDecoder"))
        assertTrue(tiledSource.contains("decodeRegion("))
        assertTrue(tiledSource.contains("LruCache<String, Bitmap>"))
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

        assertTrue(tiledSource.contains("private val tileDecodeExecutor"))
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
        assertTrue(drawSource.contains("if (!shouldDrawReaderTiles(zoomScale)) return"))
        assertTrue(drawSource.indexOf("drawPreviewBitmap(") < drawSource.indexOf("if (!shouldDrawReaderTiles(zoomScale)) return"))
        assertTrue(tiledSource.contains("readerPreviewSampleSize("))
    }

    @Test
    fun tiledRendererBuildsInitialPreviewWithDecoderOpen() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val openStart = tiledSource.indexOf("private fun openDecoderAsync(")
        val openEnd = tiledSource.indexOf("override fun onDraw", openStart)
        assertTrue(openStart >= 0)
        assertTrue(openEnd > openStart)
        val openSource = tiledSource.substring(openStart, openEnd)

        assertTrue(openSource.contains("val openedPreview = decodePreviewBitmap("))
        assertTrue(openSource.contains("previewBitmap = openedPreview"))
        assertTrue(openSource.contains("previewKey = openedPreviewKey"))
        assertTrue(tiledSource.contains("override fun onSizeChanged("))
        assertTrue(tiledSource.contains("requestPreviewDecode(readerPreviewKey("))
    }

    @Test
    fun tiledRendererReportsReadyAfterPreviewBitmapExists() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()

        assertTrue(tiledSource.contains("var previewReady by remember(file)"))
        assertTrue(tiledSource.contains("view.onPreviewReady = {"))
        assertTrue(tiledSource.contains("if (!previewReady)"))
        assertTrue(tiledSource.contains("previewReady = true"))
        assertTrue(tiledSource.contains("onImageReady()"))
        assertTrue(tiledSource.contains("if (file != null && !previewReady) loadingContent()"))
        assertFalse(tiledSource.contains("view.setImageFile(file, fillWidth, zoomScale)\n                    onImageReady()"))
    }

    @Test
    fun tiledRendererOpensDecoderOffDrawThread() {
        val tiledSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderTiledImage.kt").readText()
        val setterStart = tiledSource.indexOf("fun setImageFile(")
        val setterEnd = tiledSource.indexOf("private fun openDecoderAsync", setterStart)
        assertTrue(setterStart >= 0)
        assertTrue(setterEnd > setterStart)
        val setterSource = tiledSource.substring(setterStart, setterEnd)

        assertTrue(tiledSource.contains("openDecoderAsync("))
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
