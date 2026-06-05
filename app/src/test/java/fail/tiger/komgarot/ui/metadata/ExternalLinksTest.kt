package fail.tiger.komgarot.ui.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun blankUrlReturnsNull() {
        assertNull(normalizeExternalUrl("   "))
    }

    @Test
    fun hostWithoutSchemeGetsHttpsScheme() {
        assertEquals("https://example.com/title", normalizeExternalUrl(" example.com/title "))
    }

    @Test
    fun existingSchemeIsPreserved() {
        assertEquals("komga://series/1", normalizeExternalUrl("komga://series/1"))
        assertEquals("https://example.com", normalizeExternalUrl("https://example.com"))
    }
}
