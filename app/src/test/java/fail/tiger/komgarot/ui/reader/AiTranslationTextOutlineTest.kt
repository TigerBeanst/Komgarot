package fail.tiger.komgarot.ui.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationTextOutlineTest {
    @Test
    fun lightTranslationTextUsesDarkOutline() {
        assertEquals(Color.Black, aiTranslationOutlineColor(Color.White))
        assertEquals(Color.Black, aiTranslationOutlineColor(Color(0.8f, 0.8f, 0.8f)))
    }

    @Test
    fun darkTranslationTextUsesLightOutline() {
        assertEquals(Color.White, aiTranslationOutlineColor(Color.Black))
        assertEquals(Color.White, aiTranslationOutlineColor(Color(0.2f, 0.2f, 0.2f)))
    }

    @Test
    fun outlineWidthGrowsWithTranslationFontSize() {
        val small = aiTranslationOutlineWidthSp(8f)
        val medium = aiTranslationOutlineWidthSp(16f)
        val large = aiTranslationOutlineWidthSp(24f)

        assertTrue(small < medium)
        assertTrue(medium < large)
    }

    @Test
    fun outlineWidthStaysWithinReadableBounds() {
        assertEquals(0.6f, aiTranslationOutlineWidthSp(0.01f), 0.0001f)
        assertEquals(2f, aiTranslationOutlineWidthSp(100f), 0.0001f)
    }

    @Test
    fun invalidFontSizesUseFinitePositiveFallbackWidth() {
        val fallback = aiTranslationOutlineWidthSp(12f)
        val invalidWidths = listOf(
            aiTranslationOutlineWidthSp(0f),
            aiTranslationOutlineWidthSp(-4f),
            aiTranslationOutlineWidthSp(Float.NaN),
            aiTranslationOutlineWidthSp(Float.POSITIVE_INFINITY)
        )

        assertFalse(fallback.isNaN())
        assertTrue(fallback > 0f)
        invalidWidths.forEach { width ->
            assertFalse(width.isNaN())
            assertTrue(width.isFinite())
            assertTrue(width > 0f)
            assertEquals(fallback, width, 0.0001f)
        }
    }
}
