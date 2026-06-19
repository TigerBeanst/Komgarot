package fail.tiger.komgarot.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MeScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/settings/SettingsScreen.kt").readText()

    @Test
    fun meScreenIncludesAboutSectionWithAppInfoAndUpdateCheck() {
        assertTrue(source.contains("AboutSection("))
        assertTrue(source.contains("R.string.about"))
        assertTrue(source.contains("R.mipmap.ic_launcher_foreground"))
        assertTrue(source.contains("BuildConfig.VERSION_NAME"))
        assertTrue(source.contains("R.string.check_updates"))
        assertTrue(source.contains("appUpdateRepository.checkForUpdate"))
    }

    @Test
    fun updateDialogShowsReleaseNotesAndOpensReleasePage() {
        assertTrue(source.contains("showUpdateDialog"))
        assertTrue(source.contains("availableUpdate"))
        assertTrue(source.contains("R.string.update_available"))
        assertTrue(source.contains("openExternalUrl(context, update.htmlUrl)"))
        assertTrue(source.contains("SelectionContainer"))
    }
}
