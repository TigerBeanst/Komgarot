package fail.tiger.komgarot.ui.book

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/book/BookScreen.kt").readText()

    @Test
    fun seriesBookGridUsesBookThumbnailCacheKeys() {
        assertTrue(source.contains("ThumbnailVersion.get(book.id)"))
        assertTrue(source.contains("KomgaUrls.bookThumbnail(serverUrl, book.id, thumbnailVersion)"))
        assertTrue(source.contains("thumbnailCacheKey(ThumbnailCacheTarget.Book(book.id))"))
    }

    @Test
    fun seriesBookGridTitleCanWrapToTwoLines() {
        val gridStart = source.indexOf("items(vm.books, key = { it.id })")
        assertTrue(gridStart >= 0)
        val titleStart = source.indexOf("book.metadata.title.ifEmpty { book.name }", gridStart)
        assertTrue(titleStart >= 0)
        val titleSource = source.substring(titleStart, source.indexOf("Text(\n                                            stringResource", titleStart))

        assertTrue(titleSource.contains("maxLines = 2"))
    }
}
