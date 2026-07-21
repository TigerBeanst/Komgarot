package fail.tiger.komgarot.data.local

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BookThumbnailPreferencesStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/local/AuthPreferences.kt").readText()

    @Test
    fun bookThumbnailsAreEnabledByDefaultAndPersisted() {
        assertTrue(source.contains("booleanPreferencesKey(\"show_book_thumbnails\")"))
        assertTrue(source.contains("it[SHOW_BOOK_THUMBNAILS] ?: true"))
        assertTrue(source.contains("suspend fun setShowBookThumbnails(value: Boolean)"))
        assertTrue(source.contains("it[SHOW_BOOK_THUMBNAILS] = value"))
    }
}
