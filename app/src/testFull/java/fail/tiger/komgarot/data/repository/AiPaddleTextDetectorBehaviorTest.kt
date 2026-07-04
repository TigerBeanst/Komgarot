package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPaddleTextDetectorBehaviorTest {
    @Test
    fun broadPaddleTextRectCoveredBySmallerRectsIsRemoved() {
        val broad = PaddleTextRect(
            rect = AiTranslationRect(x = 0.20f, y = 0.20f, width = 0.34f, height = 0.42f),
            area = 1200
        )
        val upper = PaddleTextRect(
            rect = AiTranslationRect(x = 0.24f, y = 0.24f, width = 0.06f, height = 0.14f),
            area = 120
        )
        val middle = PaddleTextRect(
            rect = AiTranslationRect(x = 0.38f, y = 0.27f, width = 0.05f, height = 0.16f),
            area = 110
        )
        val lower = PaddleTextRect(
            rect = AiTranslationRect(x = 0.28f, y = 0.46f, width = 0.07f, height = 0.12f),
            area = 105
        )

        val filtered = filterBroadPaddleTextRects(listOf(broad, upper, middle, lower))

        assertFalse(filtered.contains(broad))
        assertEquals(listOf(upper, middle, lower), filtered)
    }

    @Test
    fun broadPaddleTextRectIsKeptWhenItHasNoSmallerCoverage() {
        val broad = PaddleTextRect(
            rect = AiTranslationRect(x = 0.20f, y = 0.20f, width = 0.28f, height = 0.32f),
            area = 900
        )
        val separate = PaddleTextRect(
            rect = AiTranslationRect(x = 0.70f, y = 0.70f, width = 0.06f, height = 0.12f),
            area = 90
        )

        val filtered = filterBroadPaddleTextRects(listOf(broad, separate))

        assertTrue(filtered.contains(broad))
        assertTrue(filtered.contains(separate))
    }
}
