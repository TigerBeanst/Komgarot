package fail.tiger.komgarot.ui.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFeatureNavigationStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()

    @Test
    fun navigationUsesBuildConfigToHideAiTranslationEntrypointsInLiteBuilds() {
        assertTrue(source.contains("BuildConfig.AI_TRANSLATION_AVAILABLE"))
        assertTrue(source.contains("aiTranslationAvailable"))
        assertTrue(source.contains("if (aiTranslationAvailable)"))
        assertTrue(source.contains("onAiTranslationTasksClick ="))
        assertTrue(source.contains("SettingsScreen("))
    }

    @Test
    fun readerAndBookDetailReceiveAiFeatureAvailability() {
        assertTrue(source.contains("aiTranslationAvailable = aiTranslationAvailable"))
        assertTrue(source.contains("if (aiTranslationAvailable) app.aiTranslationRepositoryOrNull else null"))
        assertTrue(source.contains("AiTranslationTaskViewModel.Factory(app.aiTranslationStore, app.aiTranslationRepositoryOrNull, serverUrl)"))
    }
}
