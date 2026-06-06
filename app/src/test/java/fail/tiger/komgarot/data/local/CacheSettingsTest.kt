package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheSettingsTest {
    @Test
    fun defaultCacheSizeIsTwoGigabytes() {
        assertEquals(2048, CacheSizeOption.default.sizeMb)
        assertEquals(2L * 1024L * 1024L * 1024L, CacheSizeOption.default.bytes)
    }

    @Test
    fun cacheSizeOptionsUseStableMegabyteValues() {
        assertEquals(listOf(256, 512, 1024, 2048, 4096), CacheSizeOption.values.map { it.sizeMb })
    }

    @Test
    fun unknownCacheSizeFallsBackToDefault() {
        assertEquals(CacheSizeOption.default, CacheSizeOption.fromMb(123))
    }
}
