package fail.tiger.komgarot.ui.bookdetail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailActionHierarchyTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt").readText()

    @Test
    fun readingActionsUseProminentMaterialButtons() {
        assertTrue(source.contains("BookDetailReadingActions("))
        assertTrue(source.contains("Button("))
        assertTrue(source.contains("FilledTonalButton("))
        assertTrue(source.contains("heightIn(min = 56.dp)"))
    }

    @Test
    fun readingActionsFillTheDetailContentWidth() {
        val componentStart = source.indexOf("private fun BookDetailReadingActions(")
        val componentEnd = source.indexOf("@Composable", componentStart + 1)
        val componentSource = source.substring(componentStart, componentEnd)

        assertTrue(componentSource.contains("Column("))
        assertTrue(componentSource.contains("Modifier.fillMaxWidth()"))
        assertTrue(componentSource.contains("modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)"))
        assertTrue(componentSource.contains("modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)"))
        assertTrue(!componentSource.contains("widthIn(max ="))
    }

    @Test
    fun readingActionsAppearBelowTheIdentityHeader() {
        val titleContentStart = source.indexOf("titleContent = {")
        val bodyContentStart = source.indexOf("bodyContent = {")
        val readingActionsCall = source.indexOf("BookDetailReadingActions(", titleContentStart)
        val readStatusActionsCall = source.indexOf("BookDetailReadStatusActions(", bodyContentStart)

        assertTrue(bodyContentStart < readingActionsCall)
        assertTrue(readingActionsCall < readStatusActionsCall)
    }

    @Test
    fun readStatusActionsUseLowPriorityTextButtons() {
        assertTrue(source.contains("BookDetailReadStatusActions("))
        assertTrue(source.contains("TextButton("))
        assertTrue(source.contains("heightIn(min = 36.dp)"))
    }

    @Test
    fun cacheActionUsesIconAndClearConfirmationDialog() {
        val cacheActionStart = source.indexOf("private fun BookDownloadAction(")
        val cacheActionEnd = source.indexOf("@Composable", cacheActionStart + 1)
        val cacheActionSource = source.substring(cacheActionStart, cacheActionEnd)

        assertTrue(cacheActionSource.contains("Icon(Icons.Default.Download"))
        assertTrue(source.contains("clear_book_cache_title"))
        assertTrue(source.contains("clear_book_cache_message"))
        assertTrue(source.contains("vm.clearOfflineCache()"))
    }

    @Test
    fun fileSourceDisplaysApiReturnedBookUrlDirectly() {
        assertTrue(source.contains("InfoRow(stringResource(R.string.file_source), book.url ?: unknown)"))
        assertTrue(!source.contains("bookFileSource("))
    }
}
