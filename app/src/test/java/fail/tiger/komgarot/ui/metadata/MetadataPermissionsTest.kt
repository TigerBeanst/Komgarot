package fail.tiger.komgarot.ui.metadata

import fail.tiger.komgarot.data.remote.dto.UserDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPermissionsTest {
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
}
