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
}
