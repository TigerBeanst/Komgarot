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
        assertTrue(viewModelSource.contains("seriesLanguages = loadMetadataLanguages { repo.getLanguages() }"))
    }
}
