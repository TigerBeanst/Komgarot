package fail.tiger.komgarot.data.repository

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBackupRepositoryStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/repository/WebDavBackupRepository.kt").readText()

    @Test
    fun repositorySupportsBackupWithoutCredentialFields() {
        assertTrue(source.contains("suspend fun backupNow()"))
        assertTrue(source.contains("secureWebDavSettingsStore.read()"))
        assertTrue(source.contains("buildBackupPayload()"))
        assertTrue(source.contains("aiTranslationStore.exportBooks()"))
        assertTrue(source.contains("aiBaseUrl = prefs.aiBaseUrl.first()"))
        assertTrue(source.contains("aiMaxImagesPerRequest = prefs.aiMaxImagesPerRequest.first()"))
        assertTrue(source.contains("val aiMaxImagesPerRequest: Int = 20"))
    }
}
