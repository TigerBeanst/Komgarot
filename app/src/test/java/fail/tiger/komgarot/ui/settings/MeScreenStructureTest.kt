package fail.tiger.komgarot.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/settings/SettingsScreen.kt").readText()
    private val navSource = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()

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

    @Test
    fun meScreenUsesBottomBarInsetFromNavigationShell() {
        val overlayStart = navSource.indexOf("private fun usesOverlayBottomBar(")
        val overlayEnd = navSource.indexOf("@Composable", overlayStart)
        val overlaySource = navSource.substring(overlayStart, overlayEnd)

        assertTrue(overlaySource.contains("route == Screen.Library.route"))
        assertFalse(overlaySource.contains("route == Screen.Me.route"))
    }

    @Test
    fun accountCardAllowsValidatedServerUrlChanges() {
        val meStart = source.indexOf("fun MeScreen(")
        val settingsPageStart = source.indexOf("private enum class SettingsPage", meStart)
        val meSource = source.substring(meStart, settingsPageStart)

        assertTrue(meSource.contains("serverUrl: String"))
        assertTrue(meSource.contains("onUpdateServerUrl: suspend (String) -> Result<Unit>"))
        assertTrue(meSource.contains("ServerUrlSettingDialog("))
        assertTrue(meSource.contains("val result = onUpdateServerUrl(value)"))
        assertTrue(meSource.contains("onServerChanged()"))
        assertTrue(navSource.contains("onUpdateServerUrl = app.authRepository::updateServerUrl"))
    }
}
