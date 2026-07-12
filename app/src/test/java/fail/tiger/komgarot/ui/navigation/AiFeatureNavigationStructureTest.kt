package fail.tiger.komgarot.ui.navigation

import java.io.File
import org.junit.Assert.assertFalse
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

    @Test
    fun seriesNavigationDoesNotWaitForSingleBookLookup() {
        val start = source.indexOf("val openSeries:")
        val end = source.indexOf("val backStackEntry", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val openSeriesSource = source.substring(start, end)

        assertTrue(openSeriesSource.contains("navController.navigate(Screen.Books.go(seriesId, bookCount))"))
        assertTrue(openSeriesSource.contains("launchSingleTop = true"))
        assertFalse(openSeriesSource.contains("getBooks(seriesId, 0)"))
    }

    @Test
    fun oneShotSeriesAutoNavigationRemovesIntermediateBookList() {
        val booksStart = source.indexOf("BookScreen(")
        val booksEnd = source.indexOf("onMetadataClick =", booksStart)
        assertTrue(booksStart >= 0)
        assertTrue(booksEnd > booksStart)
        val booksSource = source.substring(booksStart, booksEnd)

        assertTrue(booksSource.contains("onBookClick = { id, name, pages, isOneShot ->"))
        assertTrue(booksSource.contains("if (isOneShot || bookCount == 1)"))
        assertTrue(booksSource.contains("popUpTo(Screen.Books.go(seriesId, bookCount)) { inclusive = true }"))
    }
}
