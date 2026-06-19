package fail.tiger.komgarot.ui.metadata

import fail.tiger.komgarot.data.remote.dto.UserDto
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPermissionsTest {
    private val screenSource = File("src/main/java/fail/tiger/komgarot/ui/metadata/MetadataScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/metadata/MetadataViewModel.kt").readText()

    @Test
    fun adminCanEditMetadata() {
        assertTrue(canEditKomgaMetadata(UserDto(roles = listOf("USER", "ADMIN"))))
    }

    @Test
    fun regularUserCannotEditMetadata() {
        assertFalse(canEditKomgaMetadata(UserDto(roles = listOf("USER"))))
    }

    @Test
    fun missingUserCannotEditMetadata() {
        assertFalse(canEditKomgaMetadata(null))
    }

    @Test
    fun coverCropAndSaveControlsAppearOnlyAfterCoverCandidateExists() {
        assertTrue(screenSource.contains("if (canEditMetadata && candidate != null)"))
        assertTrue(screenSource.contains("CoverCrop.entries.forEach"))
        assertTrue(screenSource.contains("R.string.metadata_cover_save"))
    }

    @Test
    fun bookCoverUploadInvalidatesSeriesThumbnailCacheToo() {
        assertTrue(viewModelSource.contains("val seriesId = repo.getBookById(id).getOrNull()?.seriesId"))
        assertTrue(viewModelSource.contains("imageCacheInvalidator.invalidateBookThumbnail(id)"))
        assertTrue(viewModelSource.contains("imageCacheInvalidator.invalidateSeriesThumbnail(it)"))
    }
}
