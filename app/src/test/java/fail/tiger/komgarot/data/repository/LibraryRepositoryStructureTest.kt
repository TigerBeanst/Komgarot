package fail.tiger.komgarot.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/repository/LibraryRepository.kt").readText()

    @Test
    fun continueReadingUsesKomgaKeepReadingSearchSource() {
        assertTrue(source.contains("suspend fun getContinueReadingBooks("))
        assertTrue(source.contains("api.getBooks("))
        assertTrue(source.contains("SearchCondition.book(\"readStatus\", isCondition(\"IN_PROGRESS\"))"))
        assertTrue(source.contains("sort = listOf(\"readProgress.readDate,desc\")"))
        assertFalse(source.contains("override suspend fun getBooksOnDeck("))
    }
}
