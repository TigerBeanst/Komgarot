package fail.tiger.komgarot.ui.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryStructureTest {
    private val navSource = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()
    private val settingsSource = File("src/main/java/fail/tiger/komgarot/ui/settings/SettingsScreen.kt").readText()

    @Test
    fun appResumeRefreshesSessionThroughSingleFlightManager() {
        assertTrue(navSource.contains("LifecycleEventEffect(Lifecycle.Event.ON_RESUME)"))
        assertTrue(navSource.contains("sessionVm.refresh(force = true)"))
        assertTrue(navSource.contains("sessionState.userOrNull"))
    }

    @Test
    fun meScreenExposesSessionSyncAndManualRetry() {
        assertTrue(settingsSource.contains("sessionSyncing: Boolean"))
        assertTrue(settingsSource.contains("sessionRetryable: Boolean"))
        assertTrue(settingsSource.contains("onSessionRetry: () -> Unit"))
        assertTrue(settingsSource.contains("R.string.session_retry"))
    }
}
