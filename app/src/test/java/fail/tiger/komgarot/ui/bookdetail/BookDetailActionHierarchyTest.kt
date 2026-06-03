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
    fun readStatusActionsUseLowPriorityTextButtons() {
        assertTrue(source.contains("BookDetailReadStatusActions("))
        assertTrue(source.contains("TextButton("))
        assertTrue(source.contains("heightIn(min = 36.dp)"))
    }
}
