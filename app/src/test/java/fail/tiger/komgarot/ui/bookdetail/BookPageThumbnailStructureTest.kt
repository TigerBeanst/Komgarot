package fail.tiger.komgarot.ui.bookdetail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPageThumbnailStructureTest {
    private val detailSource = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt").readText()
    private val scaffoldSource = File("src/main/java/fail/tiger/komgarot/ui/components/ImmersiveDetailHeader.kt").readText()
    private val graphSource = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()

    @Test
    fun bookDetailUsesOneLazyGridForMetadataAndThumbnails() {
        assertTrue(scaffoldSource.contains("LazyVerticalGrid("))
        assertTrue(scaffoldSource.contains("GridCells.Adaptive(104.dp)"))
        assertTrue(scaffoldSource.contains("GridItemSpan(maxLineSpan)"))
        assertTrue(detailSource.contains("gridContent ="))
        assertTrue(detailSource.contains("contentType = { \"book-page-thumbnail\" }"))
    }

    @Test
    fun thumbnailsUseKomgaEndpointStableKeysAndPageLabels() {
        assertTrue(detailSource.contains("KomgaUrls.pageThumbnail(serverUrl, bookId, pageNumber)"))
        assertTrue(detailSource.contains("cacheKey = \"book-page-thumbnail:${'$'}bookId:${'$'}pageNumber\""))
        assertTrue(detailSource.contains("aspectRatio(0.67f)"))
        assertTrue(detailSource.contains("text = pageNumber.toString()"))
    }

    @Test
    fun settingControlsCompositionAndPageClickNavigation() {
        assertTrue(detailSource.contains("showBookThumbnails && resolvedBookId.isNotBlank() && resolvedPageCount > 0"))
        assertTrue(detailSource.contains("onPageThumbnailClick(resolvedBookId, pageNumber)"))
        assertTrue(graphSource.contains("onPageThumbnailClick = { id, page ->"))
        assertTrue(graphSource.contains("Screen.Reader.go(id, page, !alwaysIncognito)"))
    }
}
