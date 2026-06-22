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
    fun einkPagerPrecomposesAndRetainsAdjacentNormalPages() {
        assertTrue(requestSource.contains("fun readerPagerBeyondViewportPageCount("))
        assertTrue(requestSource.contains("fun readerShouldRetainPageInMemory("))
        assertTrue(source.contains("beyondViewportPageCount = readerPagerBeyondViewportPageCount(einkMode)"))
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
