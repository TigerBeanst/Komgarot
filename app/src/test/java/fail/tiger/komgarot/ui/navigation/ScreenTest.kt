package fail.tiger.komgarot.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {
    @Test
    fun bookDetailRouteEncodesPathArguments() {
        val route = Screen.BookDetail.go(
            id = "book/1+2",
            name = "A+B / C D",
            seriesName = "Series/One",
            pageCount = 42,
            isOneShot = true
        )

        val parts = route.split("/")
        assertEquals(6, parts.size)
        assertEquals("bookdetail", parts[0])
        assertEquals("book/1+2", Screen.decodeArg(parts[1]))
        assertEquals("A+B / C D", Screen.decodeArg(parts[2]))
        assertEquals("Series/One", Screen.decodeArg(parts[3]))
        assertEquals("42", parts[4])
        assertEquals("true", parts[5])
    }

    @Test
    fun seriesRouteEncodesSearchQuery() {
        val route = Screen.Series.go(id = null, search = "author:Sean Murphy/Writer+Artist")

        assertEquals(
            "series/all?search=author%3ASean%20Murphy%2FWriter%2BArtist",
            route
        )
        assertEquals(
            "author:Sean Murphy/Writer+Artist",
            Screen.decodeArg(route.substringAfter("search="))
        )
    }

    @Test
    fun seriesRouteEncodesTagFilter() {
        val route = Screen.Series.go(id = null, tag = "sci fi")

        assertEquals("series/all?tag=sci%20fi", route)
    }

    @Test
    fun metadataCoverRouteEncodesCoverUri() {
        val uri = "content://fail.tiger.komgarot.provider/cache/cover candidate+1.jpg"
        val route = Screen.Metadata.goBookCover("book/1+2", uri)

        assertEquals(
            "metadata/book/book%2F1%2B2?coverUri=content%3A%2F%2Ffail.tiger.komgarot.provider%2Fcache%2Fcover%20candidate%2B1.jpg&coverFocus=true",
            route
        )
        assertEquals(uri, Screen.decodeArg(route.substringAfter("coverUri=").substringBefore("&")))
    }

    @Test
    fun metadataRouteWithoutCoverArgsKeepsExistingShape() {
        assertEquals("metadata/series/series%2F1", Screen.Metadata.go("series", "series/1"))
    }

    @Test
    fun cachedBooksRouteIsStable() {
        assertEquals("cached-books", Screen.CachedBooks.route)
    }

    @Test
    fun aiTranslationTasksRouteIsStable() {
        assertEquals("ai_translation_tasks", Screen.AiTranslationTasks.route)
    }
}
