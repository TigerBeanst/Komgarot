package fail.tiger.komgarot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlNormalizerTest {
    @Test
    fun keepsExplicitHttpUrl() {
        assertEquals("http://192.0.2.8:25600", normalizeServerUrl(" http://192.0.2.8:25600/ "))
    }

    @Test
    fun keepsExplicitHttpsUrl() {
        assertEquals("https://komga.example.com/base", normalizeServerUrl("https://komga.example.com/base/"))
    }

    @Test
    fun prefixesHttpForHostOnlyUrl() {
        assertEquals("http://komga.example.test:25600", normalizeServerUrl("komga.example.test:25600"))
    }

    @Test
    fun blankInputNormalizesToBlank() {
        assertEquals("", normalizeServerUrl("   "))
    }
}
