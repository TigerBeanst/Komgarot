package fail.tiger.komgarot.ui.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLoadingSkeletonNavigationTest {
    @Test
    fun onlyOneShotSeriesUsesDirectBookDetailDestination() {
        val screen = File("src/main/java/fail/tiger/komgarot/ui/navigation/Screen.kt").readText()
        val graph = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()

        assertTrue(screen.contains("object BookDetailFromSeries"))
        assertTrue(screen.contains("fun go(seriesId: String)"))
        assertTrue(graph.contains("if (oneShot)"))
        assertTrue(!graph.contains("oneShot || bookCount == 1"))
        assertTrue(graph.contains("navController.navigate(Screen.BookDetailFromSeries.go(seriesId))"))
        assertTrue(graph.contains("Screen.BookDetailFromSeries.route"))
        assertTrue(graph.contains("seriesIdToResolve = seriesId"))
    }
}
