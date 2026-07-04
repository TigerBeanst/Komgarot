package fail.tiger.komgarot.ui.cached

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedBooksScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/cached/CachedBooksScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/cached/CachedBooksViewModel.kt").readText()

    @Test
    fun cachedBooksTopBarHasClearAllActionWithTwoConfirmations() {
        assertTrue(source.contains("showClearAllFirstConfirmation"))
        assertTrue(source.contains("showClearAllFinalConfirmation"))
        assertTrue(source.contains("actions = {"))
        assertTrue(source.contains("Icon(Icons.Default.Delete"))
        assertTrue(source.contains("R.string.cached_books_clear_all_title"))
        assertTrue(source.contains("R.string.cached_books_clear_all_message_first"))
        assertTrue(source.contains("R.string.cached_books_clear_all_title_final"))
        assertTrue(source.contains("R.string.cached_books_clear_all_message_final"))
        assertTrue(source.contains("vm.clearAll()"))
    }

    @Test
    fun cachedBooksViewModelClearsAndRefreshesList() {
        assertTrue(viewModelSource.contains("fun clearAll()"))
        assertTrue(viewModelSource.contains("source.clearAll()"))
        assertTrue(viewModelSource.contains("load()"))
    }
}
