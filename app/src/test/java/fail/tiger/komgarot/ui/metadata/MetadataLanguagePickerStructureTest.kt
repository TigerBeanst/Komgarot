package fail.tiger.komgarot.ui.metadata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataLanguagePickerStructureTest {
    private val screenSource = File("src/main/java/fail/tiger/komgarot/ui/metadata/MetadataScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/metadata/MetadataViewModel.kt").readText()
    private val repositorySource = File("src/main/java/fail/tiger/komgarot/data/repository/BookRepository.kt").readText()

    @Test
    fun seriesLanguageUsesDropdownAndLockState() {
        assertTrue(screenSource.contains("MetadataLanguageField("))
        assertTrue(screenSource.contains("languageLock = true"))
        assertTrue(screenSource.contains("LockSwitch(stringResource(R.string.metadata_language), languageLock)"))
    }

    @Test
    fun seriesLanguageLoadsKomgaReferentialValuesWithFallback() {
        assertTrue(repositorySource.contains("suspend fun getLanguages(): List<String> = api.getLanguages()"))
        assertTrue(viewModelSource.contains("metadataLanguages = loadMetadataLanguages { repo.getLanguages() }"))
    }

    @Test
    fun bookMetadataShowsAndEditsItsKomgaSeriesLanguage() {
        val bookStart = screenSource.indexOf("fun BookMetadataContent(")
        val bookEnd = screenSource.indexOf("private fun MetadataScaffold(", bookStart)
        val bookSource = screenSource.substring(bookStart, bookEnd)

        assertTrue(bookSource.contains("val seriesMeta = vm.bookSeriesMeta"))
        assertTrue(bookSource.contains("MetadataLanguageField("))
        assertTrue(bookSource.contains("R.string.metadata_book_language_series_scope"))
        assertTrue(bookSource.contains("seriesLanguage = language"))
        assertTrue(bookSource.contains("seriesLanguageLock = languageLock"))
        assertTrue(bookSource.contains("LockSwitch(stringResource(R.string.metadata_language), languageLock)"))
    }

    @Test
    fun bookMetadataLoadsAndPartiallyUpdatesItsSeriesLanguage() {
        assertTrue(viewModelSource.contains("bookSeriesId = book.seriesId"))
        assertTrue(viewModelSource.contains("repo.getSeriesMetadata(book.seriesId)"))
        assertTrue(viewModelSource.contains("repo.updateSeriesLanguage(bookSeriesId, body)"))
        assertTrue(repositorySource.contains("suspend fun updateSeriesLanguage("))
    }
}
