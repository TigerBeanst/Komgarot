package fail.tiger.komgarot.ui.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLoadingSkeletonNavigationTest {
    @Test
    fun booksRouteCarriesKnownBookCount() {
        val screen = File("src/main/java/fail/tiger/komgarot/ui/navigation/Screen.kt").readText()
        val graph = File("src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt").readText()

        assertTrue(screen.contains("bookCount={bookCount}"))
        assertTrue(screen.contains("fun go(id: String, bookCount: Int)"))
        assertTrue(graph.contains("Screen.Books.go(seriesId, bookCount)"))
        assertTrue(graph.contains("initialBookCount = bookCount"))
    }
}
