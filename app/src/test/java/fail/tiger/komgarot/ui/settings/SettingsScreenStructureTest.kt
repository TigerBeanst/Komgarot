package fail.tiger.komgarot.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/settings/SettingsScreen.kt").readText()

    @Test
    fun settingsContentUsesSectionHeadersAndDividers() {
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_cache))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_reading))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_security))"))
        assertTrue(source.contains("HorizontalDivider(Modifier.padding(vertical = 8.dp))"))
    }

    @Test
    fun settingsContentIncludesEinkReaderOptions() {
        assertTrue(source.contains("R.string.settings_eink_mode"))
        assertTrue(source.contains("R.string.settings_tap_page_turn"))
        assertTrue(source.contains("if (!einkMode)"))
        assertTrue(source.contains("prefs.setEinkMode"))
        assertTrue(source.contains("prefs.setTapPageTurn"))
    }
}
