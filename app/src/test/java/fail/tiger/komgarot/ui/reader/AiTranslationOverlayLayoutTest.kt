package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationOverlayLayoutTest {
    @Test
    fun singleLongHorizontalLineWrapsToFitAvailableWidth() {
        val lines = balancedHorizontalLines(
            lines = listOf("但现在还不能回去啊"),
            widthDp = 44f,
            fontSizeSp = 10f
        )

        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 7 })
        assertEquals("但现在还不能回去啊", lines.joinToString(""))
    }

    @Test
    fun longHorizontalTranslationUsesSmallerFontThanShortTranslation() {
        val short = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 80f,
            rectHeightDp = 36f,
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            lineCount = 1,
            textLength = 4
        )
        val long = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 80f,
            rectHeightDp = 36f,
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            lineCount = 1,
            textLength = 18
        )

        assertTrue(long < short)
        assertTrue(long >= 9.5f)
    }

    @Test
    fun verticalTranslationKeepsReadableFontWhenTextNeedsMultipleColumns() {
        val fontSize = aiTranslationFontSizeSp(
            baseScale = 0.72f,
            rectWidthDp = 10f,
            rectHeightDp = 80f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 1,
            textLength = 14
        )

        assertTrue(fontSize >= 8.5f)
    }

    @Test
    fun verticalLongTranslationUsesConservativeFontSizeForColumnHeight() {
        val fontSize = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 64f,
            rectHeightDp = 150f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 3,
            textLength = 18
        )

        assertTrue(fontSize <= 12.5f)
    }

    @Test
    fun verticalCharsPerColumnUsesActualGlyphAdvance() {
        assertEquals(7, verticalCharsPerColumn(heightDp = 80f, fontSizeSp = 10f))
    }

    @Test
    fun verticalTextStartsSlightlyAboveDetectedTop() {
        val offset = verticalTextTopOffsetDp(fontSizeSp = 20f)

        assertTrue(offset.value < 0f)
        assertTrue(offset.value <= -2.5f)
    }

    @Test
    fun verticalTranslationFontStillRespondsToDetectedColumnWidth() {
        val fontSize = aiTranslationFontSizeSp(
            baseScale = 1.2f,
            rectWidthDp = 24f,
            rectHeightDp = 180f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 1,
            textLength = 5
        )

        assertTrue(fontSize >= 20f)
        assertTrue(fontSize <= 28f)
    }

    @Test
    fun normalTextKindsUseSolidMaskAndSfxKeepsLightweightTextBackground() {
        assertTrue(AiTranslationBlockKind.DIALOGUE.usesSolidAiTranslationMask())
        assertTrue(AiTranslationBlockKind.NARRATION.usesSolidAiTranslationMask())
        assertTrue(AiTranslationBlockKind.SIGN.usesSolidAiTranslationMask())
        assertTrue(!AiTranslationBlockKind.SFX.usesSolidAiTranslationMask())
        assertTrue(!AiTranslationBlockKind.OTHER.usesSolidAiTranslationMask())
        assertTrue(normalAiTranslationMaskAlpha(0.78f) >= 0.86f)
    }

    @Test
    fun verticalColumnWidthLeavesRoomForSemiboldCjkGlyphs() {
        val columnWidth = verticalColumnWidthDp(fontSizeSp = 10f)

        assertTrue(columnWidth.value >= 11.5f)
    }

    @Test
    fun horizontalWrappingKeepsTrailingPunctuationWithPreviousText() {
        val lines = balancedHorizontalLines(
            lines = listOf("变成超级大虫怪快过来！"),
            widthDp = 30f,
            fontSizeSp = 10f
        )

        assertEquals(listOf("变成超级大", "虫怪快过来！"), lines)
    }

    @Test
    fun verticalColumnsKeepTrailingPunctuationWithPreviousText() {
        val columns = verticalTextColumnsForDisplay(
            lines = listOf("变成虫吧！"),
            charsPerColumn = 2
        )

        assertEquals(listOf("虫吧！", "变成"), columns)
    }

    @Test
    fun verticalColumnsAvoidSingleCharacterTailByAllowingSmallOverflow() {
        val columns = verticalTextColumnsForDisplay(
            lines = listOf("只剩最后的一次机会"),
            charsPerColumn = 4
        )

        assertEquals(listOf("的一次机会", "只剩最后"), columns)
    }
}
