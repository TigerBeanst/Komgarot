package fail.tiger.komgarot

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPrivacyLockStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/MainActivity.kt").readText()

    @Test
    fun appLockCoversContentBeforeAsyncResumeCheck() {
        assertTrue(source.contains("private val privacyCovered = mutableStateOf(false)"))
        assertTrue(source.contains("privacyCovered.value = app.authPreferences.appLockEnabledBlocking"))
        assertTrue(source.contains("val privacyActive = locked.value || privacyCovered.value"))
        assertTrue(source.contains("if (privacyActive && !einkMode) Modifier.blur(20.dp) else Modifier"))
        assertTrue(source.contains("if (privacyActive)"))
    }

    @Test
    fun appLockCoversContentWhenActivityLeavesForeground() {
        assertTrue(source.contains("private fun coverForAppLockIfEnabled()"))
        assertTrue(source.contains("override fun onPause()"))
        assertTrue(source.contains("coverForAppLockIfEnabled()"))
        assertTrue(source.contains("privacyCovered.value = true"))
        assertTrue(source.contains("private fun revealUnlockedContent()"))
    }
}
