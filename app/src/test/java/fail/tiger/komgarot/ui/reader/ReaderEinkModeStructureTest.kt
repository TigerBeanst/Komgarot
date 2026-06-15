package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEinkModeStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()

    @Test
    fun einkModeUsesPagerReader() {
        assertTrue(source.contains("if (einkMode) {\n            PagerReader"))
    }

    @Test
    fun einkModeHidesModeToggle() {
        assertTrue(source.contains("showModeToggle = false"))
        assertTrue(source.contains("if (showModeToggle)"))
    }

    @Test
    fun pagerDragFollowsEinkMode() {
        assertTrue(source.contains("userScrollEnabled = !einkMode"))
    }
}
