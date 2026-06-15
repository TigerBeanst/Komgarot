package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageQualityStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()

    @Test
    fun readerDisplayRequestsOriginalSizeImages() {
        val displayRequestBlock = Regex(
            "private fun rememberReaderPageRequest[\\s\\S]*?return ReaderPageImageRequestState"
        ).find(source)?.value.orEmpty()

        assertTrue(displayRequestBlock.contains("originalSize = true"))
    }

    @Test
    fun readerPreloadsRequestOriginalSizeImages() {
        val preloadRequests = Regex(
            "imageLoader\\.enqueue\\(\\s*readerPageRequest\\([\\s\\S]*?originalSize = true[\\s\\S]*?\\)\\s*\\)"
        ).findAll(source).count()

        assertTrue(preloadRequests >= 2)
    }
}
