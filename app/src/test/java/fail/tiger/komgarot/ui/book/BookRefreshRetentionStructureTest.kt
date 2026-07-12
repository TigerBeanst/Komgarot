package fail.tiger.komgarot.ui.book

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRefreshRetentionStructureTest {
    @Test
    fun bookListRefreshUsesRetainingPagingPath() {
        val source = File("src/main/java/fail/tiger/komgarot/ui/book/BookViewModel.kt").readText()
        val start = source.indexOf("fun refresh()")
        val end = source.indexOf("fun loadMore()", start)
        val refreshSource = source.substring(start, end)

        assertTrue(refreshSource.contains("paging.refresh"))
        assertFalse(refreshSource.contains("paging.reset()"))
    }

    @Test
    fun bookDetailRefreshKeepsLoadedBookVisible() {
        val source = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailViewModel.kt").readText()
        val start = source.indexOf("fun refresh()")
        val end = source.indexOf("fun markRead()", start)
        val refreshSource = source.substring(start, end)

        assertFalse(refreshSource.contains("book = null"))
        assertFalse(refreshSource.contains("metadata = null"))
        assertTrue(refreshSource.contains("load(currentBookId, currentServerUrl)"))
    }
}
