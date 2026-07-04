package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AiTranslationOverlayLayoutTest {
    @Test
    fun singleLongHorizontalLineWrapsToFitAvailableWidth() {
        val lines = balancedHorizontalLines(
            lines = listOf("示例文本需要换行"),
            widthDp = 44f,
            fontSizeSp = 10f
        )

        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 7 })
        assertEquals("示例文本需要换行", lines.joinToString(""))
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
        val baseFontSize = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 64f,
            rectHeightDp = 150f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 3,
            textLength = 18
        )
        val layout = fitVerticalAiTranslationText(
            lines = listOf("示例文本需要较多字符换列显示"),
            rectWidthDp = 64f,
            rectHeightDp = 150f,
            baseFontSizeSp = baseFontSize,
            kind = AiTranslationBlockKind.DIALOGUE,
            columnGapDp = 1f
        )
        val width = verticalTextLayoutWidthDp(layout.fontSizeSp, layout.columns.size, columnGapDp = 1f)
        val height = verticalTextLayoutHeightDp(
            maxColumnLength = layout.columns.maxOf { it.length },
            fontSizeSp = layout.fontSizeSp
        )

        assertTrue(layout.fontSizeSp <= baseFontSize)
        assertTrue(width <= 64f)
        assertTrue(height <= 150f)
    }

    @Test
    fun verticalCharsPerColumnUsesActualGlyphAdvance() {
        assertEquals(9, verticalCharsPerColumn(heightDp = 80f, fontSizeSp = 10f))
        assertEquals(9, verticalCharsPerColumn(heightDp = 80f, fontSizeSp = 10f, glyphSpacingMultiplier = 0.86f))
        assertEquals(10, verticalCharsPerColumn(heightDp = 80f, fontSizeSp = 10f, glyphSpacingMultiplier = 0.70f))
        assertEquals(6, verticalCharsPerColumn(heightDp = 80f, fontSizeSp = 10f, glyphSpacingMultiplier = 1.30f))
    }

    @Test
    fun verticalGlyphPlacementAllowsCompactConfiguredAdvance() {
        assertEquals(12, verticalGlyphPlacementAdvancePx(requestedAdvancePx = 12, tallestGlyphPx = 15))
        assertEquals(12, verticalGlyphPlacementAdvancePx(requestedAdvancePx = 9, tallestGlyphPx = 15))
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

        assertTrue(fontSize >= 13.5f)
        assertTrue(fontSize <= 14.5f)
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

        assertTrue(columnWidth.value >= 11.6f)
        assertTrue(columnWidth.value <= 12.4f)
    }

    @Test
    fun verticalLayoutShrinksUntilColumnsFitInsideRegionBounds() {
        val layout = fitVerticalAiTranslationText(
            lines = listOf("那么马上开始吧", "嗯！"),
            rectWidthDp = 58f,
            rectHeightDp = 132f,
            baseFontSizeSp = 19f,
            kind = AiTranslationBlockKind.DIALOGUE,
            columnGapDp = 1f
        )

        val width = verticalTextLayoutWidthDp(layout.fontSizeSp, layout.columns.size, columnGapDp = 1f)
        val height = verticalTextLayoutHeightDp(
            maxColumnLength = layout.columns.maxOf { it.length },
            fontSizeSp = layout.fontSizeSp
        )

        assertTrue(width <= 58f)
        assertTrue(height <= 132f)
    }

    @Test
    fun horizontalLayoutShrinksAfterWrappingSoLinesStayInsideRegionBounds() {
        val layout = fitHorizontalAiTranslationText(
            lines = listOf("想知道男同士之间该怎么做爱"),
            rectWidthDp = 64f,
            rectHeightDp = 48f,
            baseFontSizeSp = 18f,
            kind = AiTranslationBlockKind.DIALOGUE,
            lineGapDp = 1f
        )

        val width = horizontalTextLayoutWidthDp(layout.lines, layout.fontSizeSp)
        val height = horizontalTextLayoutHeightDp(layout.lines.size, layout.fontSizeSp, lineGapDp = 1f)

        assertTrue(width <= 64f)
        assertTrue(height <= 48f)
    }

    @Test
    fun veryNarrowVerticalLayoutStillFitsInsideRegionBounds() {
        val layout = fitVerticalAiTranslationText(
            lines = listOf("护理教员", "32岁"),
            rectWidthDp = 12f,
            rectHeightDp = 58f,
            baseFontSizeSp = 12f,
            kind = AiTranslationBlockKind.DIALOGUE
        )

        val width = verticalTextLayoutWidthDp(layout.fontSizeSp, layout.columns.size)
        val height = verticalTextLayoutHeightDp(
            maxColumnLength = layout.columns.maxOf { it.length },
            fontSizeSp = layout.fontSizeSp
        )

        assertTrue(width <= 12f)
        assertTrue(height <= 58f)
    }

    @Test
    fun veryNarrowHorizontalLayoutCanWrapToSingleCharactersToFit() {
        val layout = fitHorizontalAiTranslationText(
            lines = listOf("医务室"),
            rectWidthDp = 12f,
            rectHeightDp = 48f,
            baseFontSizeSp = 18f,
            kind = AiTranslationBlockKind.SIGN
        )

        val width = horizontalTextLayoutWidthDp(layout.lines, layout.fontSizeSp)
        val height = horizontalTextLayoutHeightDp(layout.lines.size, layout.fontSizeSp)

        assertTrue(width <= 12f)
        assertTrue(height <= 48f)
    }

    @Test
    fun horizontalWrappingKeepsTrailingPunctuationWithPreviousText() {
        val lines = balancedHorizontalLines(
            lines = listOf("示例文本需要断行！"),
            widthDp = 30f,
            fontSizeSp = 10f
        )

        assertEquals(listOf("示例文", "本需要", "断行！"), lines)
    }

    @Test
    fun koreanHorizontalWrappingKeepsWordsTogether() {
        val lines = balancedHorizontalLines(
            lines = listOf("우리는 학교에서 만났어!"),
            widthDp = 42f,
            fontSizeSp = 10f
        )

        assertEquals(listOf("우리는", "학교에서", "만났어!"), lines)
    }

    @Test
    fun sfxFontSizeStaysSmallerThanDialogueFontSize() {
        val dialogue = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 160f,
            rectHeightDp = 64f,
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            lineCount = 1,
            textLength = 2,
            kind = AiTranslationBlockKind.DIALOGUE
        )
        val sfx = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 160f,
            rectHeightDp = 64f,
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            lineCount = 1,
            textLength = 2,
            kind = AiTranslationBlockKind.SFX
        )

        assertTrue(sfx < dialogue * 0.76f)
        assertTrue(sfx <= 16f)
    }

    @Test
    fun verticalColumnsKeepTrailingPunctuationWithPreviousText() {
        val columns = verticalTextColumnsForDisplay(
            lines = listOf("示例文本！"),
            charsPerColumn = 2
        )

        assertEquals(listOf("文本！", "示例"), columns)
    }

    @Test
    fun verticalColumnsAvoidSingleCharacterTailByAllowingSmallOverflow() {
        val columns = verticalTextColumnsForDisplay(
            lines = listOf("示例文本测试结尾"),
            charsPerColumn = 4,
            kind = AiTranslationBlockKind.DIALOGUE
        )

        assertEquals(listOf("测试结尾", "示例文本"), columns)
    }

    @Test
    fun verticalDialogueMergesAiReturnedLinesBeforeBalancingColumns() {
        val columns = verticalTextColumnsForDisplay(
            lines = listOf("想知道", "男同志之间", "怎么做爱明明要知道吗！？"),
            charsPerColumn = 8,
            kind = AiTranslationBlockKind.DIALOGUE
        )

        assertEquals("想知道男同志之间怎么做爱明明要知道吗！？", columns.asReversed().joinToString(""))
        assertTrue(columns.all { it.length >= 5 })
        assertTrue(columns.size <= 3)
    }

    @Test
    fun verticalDialogueUsesLargestReadableFontAfterBalancedColumnBreaks() {
        val lines = listOf("想知道", "男同志之间", "怎么做爱明明要知道吗！？")
        val baseFontSize = aiTranslationFontSizeSp(
            baseScale = 1f,
            rectWidthDp = 54f,
            rectHeightDp = 140f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = lines.size,
            textLength = lines.sumOf { it.length }
        )
        val layout = fitVerticalAiTranslationText(
            lines = lines,
            rectWidthDp = 54f,
            rectHeightDp = 140f,
            baseFontSizeSp = baseFontSize,
            kind = AiTranslationBlockKind.DIALOGUE,
            columnGapDp = 1f
        )
        val width = verticalTextLayoutWidthDp(layout.fontSizeSp, layout.columns.size, columnGapDp = 1f)
        val height = verticalTextLayoutHeightDp(
            maxColumnLength = layout.columns.maxOf { it.length },
            fontSizeSp = layout.fontSizeSp
        )

        assertTrue(layout.fontSizeSp >= 11.5f)
        assertTrue("width=$width font=${layout.fontSizeSp} columns=${layout.columns}", width <= 54f * 0.94f)
        assertTrue("height=$height font=${layout.fontSizeSp} columns=${layout.columns}", height <= 140f * 0.96f)
    }

    @Test
    fun verticalDialogueFitsWithInnerPaddingInsideBubbleBounds() {
        val lines = listOf("那么", "马上开始吧", "嗯！")
        val layout = fitVerticalAiTranslationText(
            lines = lines,
            rectWidthDp = 58f,
            rectHeightDp = 132f,
            baseFontSizeSp = 19f,
            kind = AiTranslationBlockKind.DIALOGUE,
            columnGapDp = 1f
        )
        val width = verticalTextLayoutWidthDp(layout.fontSizeSp, layout.columns.size, columnGapDp = 1f)
        val height = verticalTextLayoutHeightDp(
            maxColumnLength = layout.columns.maxOf { it.length },
            fontSizeSp = layout.fontSizeSp
        )

        assertTrue("font=${layout.fontSizeSp} columns=${layout.columns}", layout.fontSizeSp <= 16f)
        assertTrue("width=$width font=${layout.fontSizeSp} columns=${layout.columns}", width <= 58f * 0.94f)
        assertTrue("height=$height font=${layout.fontSizeSp} columns=${layout.columns}", height <= 132f * 0.96f)
    }

    @Test
    fun signVerticalFontSizeStaysReadableWithoutOverflowingNarrowNameplate() {
        val fontSize = aiTranslationFontSizeSp(
            baseScale = 1.2f,
            rectWidthDp = 30f,
            rectHeightDp = 116f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 3,
            textLength = 10,
            kind = AiTranslationBlockKind.SIGN
        )

        assertTrue(fontSize <= 14f)
    }

    @Test
    fun overlappingTranslationRectsShiftApartWithoutMovingSourceMask() {
        val sourceRect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.08f, height = 0.12f)
        val first = AiTranslationBlock(
            localRegionId = "r1",
            translatedLines = listOf("甲"),
            rect = sourceRect,
            translationRect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.10f, height = 0.06f)
        )
        val secondSourceRect = AiTranslationRect(x = 0.10f, y = 0.15f, width = 0.08f, height = 0.06f)
        val second = AiTranslationBlock(
            localRegionId = "r2",
            translatedLines = listOf("乙"),
            rect = secondSourceRect,
            translationRect = AiTranslationRect(x = 0.10f, y = 0.15f, width = 0.10f, height = 0.06f)
        )

        val adjusted = listOf(first, second).withNonOverlappingTranslationRects(gap = 0.01f)

        assertEquals(first.translationRect, adjusted[0].translationRect)
        assertEquals(secondSourceRect, adjusted[1].rect)
        assertFalse(adjusted[0].translationRect.overlapsAiTranslationRect(adjusted[1].translationRect, gap = 0.01f))
    }

    @Test
    fun defaultOverlapAvoidanceMovesTightBubbleTextFarEnoughToSeparate() {
        val first = AiTranslationBlock(
            localRegionId = "r1",
            translatedLines = listOf("甲"),
            rect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.10f, height = 0.06f),
            translationRect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.10f, height = 0.06f)
        )
        val second = AiTranslationBlock(
            localRegionId = "r2",
            translatedLines = listOf("乙"),
            rect = AiTranslationRect(x = 0.10f, y = 0.12f, width = 0.10f, height = 0.06f),
            translationRect = AiTranslationRect(x = 0.10f, y = 0.12f, width = 0.10f, height = 0.06f)
        )

        val adjusted = listOf(first, second).withNonOverlappingTranslationRects(gap = 0.01f)

        assertFalse(adjusted[0].translationRect.overlapsAiTranslationRect(adjusted[1].translationRect, gap = 0.01f))
        assertTrue(abs(adjusted[1].translationRect.y - second.translationRect.y) <= 0.06f)
    }

    @Test
    fun overlapAvoidanceKeepsTranslationNearSourceWhenClearPlacementIsFarAway() {
        val first = AiTranslationBlock(
            localRegionId = "r1",
            translatedLines = listOf("甲"),
            rect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.30f, height = 0.30f),
            translationRect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.30f, height = 0.30f)
        )
        val second = AiTranslationBlock(
            localRegionId = "r2",
            translatedLines = listOf("乙"),
            rect = AiTranslationRect(x = 0.14f, y = 0.16f, width = 0.08f, height = 0.12f),
            translationRect = AiTranslationRect(x = 0.14f, y = 0.16f, width = 0.08f, height = 0.12f)
        )

        val adjusted = listOf(first, second).withNonOverlappingTranslationRects(gap = 0.01f)
        val moved = adjusted[1].translationRect

        assertTrue(abs(moved.x - second.translationRect.x) <= 0.06f)
        assertTrue(abs(moved.y - second.translationRect.y) <= 0.06f)
    }

    @Test
    fun verticalDialogueFontSizeUsesModerateMangaCap() {
        val fontSize = aiTranslationFontSizeSp(
            baseScale = 1.4f,
            rectWidthDp = 72f,
            rectHeightDp = 150f,
            textDirection = AiTranslationTextDirection.VERTICAL,
            lineCount = 2,
            textLength = 7,
            kind = AiTranslationBlockKind.DIALOGUE
        )

        assertTrue(fontSize <= 14.5f)
    }

    @Test
    fun verticalDialogueFitKeepsReadableMinimumFontSize() {
        val layout = fitVerticalAiTranslationText(
            lines = listOf("示例文本需要保持可读"),
            rectWidthDp = 18f,
            rectHeightDp = 40f,
            baseFontSizeSp = 12f,
            kind = AiTranslationBlockKind.DIALOGUE,
            columnGapDp = 1f
        )

        assertTrue(layout.fontSizeSp >= 7.2f)
    }
}
