package fail.tiger.komgarot.ui.bookdetail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSeriesNavigationStructureTest {
    @Test
    fun regularBookShowsClickableContainingSeries() {
        val source = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt").readText()

        assertTrue(source.contains("!book.oneshot && book.seriesId.isNotBlank()"))
        assertTrue(source.contains("R.string.book_belongs_to_series"))
        assertTrue(source.contains("onSeriesClick(book.seriesId)"))
    }
}
