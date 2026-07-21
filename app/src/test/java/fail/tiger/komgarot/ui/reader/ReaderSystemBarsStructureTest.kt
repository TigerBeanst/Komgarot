package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSystemBarsStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()

    @Test
    fun readerUsesLightSystemBarIconsAndRestoresCurrentThemeAppearance() {
        assertTrue(source.contains("val useDarkSystemBarIcons = !isSystemInDarkTheme()"))
        assertTrue(source.contains("rememberUpdatedState(useDarkSystemBarIcons)"))
        assertTrue(source.contains("isAppearanceLightStatusBars = false"))
        assertTrue(source.contains("isAppearanceLightNavigationBars = false"))
        assertTrue(source.contains("isAppearanceLightStatusBars = restoreDarkSystemBarIcons"))
        assertTrue(source.contains("isAppearanceLightNavigationBars = restoreDarkSystemBarIcons"))
    }
}
