package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageQualityStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
    private val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()

    @Test
    fun readerDisplayRequestsViewportSizedImages() {
        val displayRequestBlock = Regex(
            "private fun rememberReaderPageRequest[\\s\\S]*?return ReaderPageImageRequestState"
        ).find(source)?.value.orEmpty()

        assertTrue(displayRequestBlock.contains("originalSize = false"))
    }

    @Test
    fun readerPreloadsRequestViewportSizedImages() {
        val preloadRequests = Regex("imageLoader\\.enqueue\\([\\s\\S]*?readerPageRequest\\([\\s\\S]*?\\)\\s*\\)")
            .findAll(source)
            .map { it.value }
            .toList()

        assertTrue(preloadRequests.size >= 2)
        assertTrue(preloadRequests.all { it.contains("originalSize = false") })
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
