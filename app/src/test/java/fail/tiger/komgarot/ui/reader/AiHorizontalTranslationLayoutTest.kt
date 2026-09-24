package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHorizontalTranslationLayoutTest {
    @Test
    fun narrowMeasuredLayoutFitsWideLettersAndClosingPunctuation() {
        for ((text, locale) in listOf("WWWW" to "en-US", "示例。" to "zh-CN")) {
            val measure: (String, Float) -> AiMeasuredGlyphSize = { value, size ->
                AiMeasuredGlyphSize(value.length * size, size * 1.2f)
            }
            val layout = fitMeasuredHorizontalAiTranslationText(
                lines = listOf(text), rectWidthDp = 8f, rectHeightDp = 80f,
                baseFontSizeSp = 12f,
                kind = fail.tiger.komgarot.data.local.AiTranslationBlockKind.DIALOGUE,
                lineGapDp = 0f, targetLocale = locale, measureText = measure
            )
            assertEquals(text, layout.lines.joinToString(""))
            val bounds = measuredHorizontalLayoutBounds(layout, 0f, measure)
            assertTrue("$locale width=${bounds.widthDp}", bounds.widthDp <= 8f)
            assertTrue(bounds.heightDp <= 80f)
        }
    }

    @Test
    fun shortMeasuredLayoutFitsShortHeight() {
        val measure: (String, Float) -> AiMeasuredGlyphSize = { value, size ->
            AiMeasuredGlyphSize(value.length * size, size * 1.2f)
        }
        val layout = fitMeasuredHorizontalAiTranslationText(
            lines = listOf("short"), rectWidthDp = 120f, rectHeightDp = 3f,
            baseFontSizeSp = 12f,
            kind = fail.tiger.komgarot.data.local.AiTranslationBlockKind.DIALOGUE,
            lineGapDp = 0f, targetLocale = "en-US", measureText = measure
        )

        assertEquals("short", layout.lines.joinToString(""))
        val bounds = measuredHorizontalLayoutBounds(layout, 0f, measure)
        assertTrue("width=${bounds.widthDp}", bounds.widthDp <= 120f)
        assertTrue("height=${bounds.heightDp}", bounds.heightDp <= 3f)
    }

    @Test
    fun englishTargetKeepsWholeWordsWhenWrapping() {
        val lines = horizontalTargetTextLines(
            lines = listOf("Sample tokens remain split"),
            widthDp = 48f,
            fontSizeSp = 12f,
            targetLocale = "en-US"
        )

        assertEquals(listOf("Sample", "tokens", "remain", "split"), lines)
        assertTrue(lines.none { it.endsWith(" ") })
    }

    @Test
    fun koreanTargetKeepsWordSpacesAndSplitsOnlyLongWords() {
        val lines = horizontalTargetTextLines(
            lines = listOf("가나다 라마바 사아자"),
            widthDp = 48f,
            fontSizeSp = 12f,
            targetLocale = "ko-KR"
        )

        assertEquals(listOf("가나다", "라마바", "사아자"), lines)
    }

    @Test
    fun cjkTargetAttachesClosingPunctuationToPreviousLine() {
        val lines = horizontalTargetTextLines(
            lines = listOf("示例文本。"),
            widthDp = 24f,
            fontSizeSp = 12f,
            targetLocale = "zh-CN"
        )

        assertEquals(listOf("示例", "文本。"), lines)
    }

    @Test
    fun cjkPunctuationStaysWithAFullPreviousLine() {
        val lines = horizontalTargetTextLines(
            lines = listOf("示例。"),
            widthDp = 24f,
            fontSizeSp = 12f,
            targetLocale = "zh-CN"
        )

        assertEquals(listOf("示例。"), lines)
    }

    @Test
    fun emptyTargetLocaleUsesExistingHorizontalWrapping() {
        val lines = horizontalTargetTextLines(
            lines = listOf("alpha beta"),
            widthDp = 60f,
            fontSizeSp = 12f,
            targetLocale = ""
        )

        assertEquals(balancedHorizontalLines(listOf("alpha beta"), 60f, 12f), lines)
    }

    @Test
    fun horizontalSourceLineMasksStaySeparatedForVersionedBlocks() {
        val block = AiTranslationBlock(
            rect = AiTranslationRect(x = 0.2f, y = 0.2f, width = 0.5f, height = 0.2f),
            sourceLines = listOf(
                AiTranslationRect(x = 0.22f, y = 0.22f, width = 0.46f, height = 0.06f),
                AiTranslationRect(x = 0.22f, y = 0.30f, width = 0.40f, height = 0.06f)
            ),
            horizontalLayoutVersion = 1,
            textDirection = AiTranslationTextDirection.HORIZONTAL
        )

        val masks = aiTranslationSourceMaskRects(
            block = block,
            pageWidthDp = 1000f,
            pageHeightDp = 1000f,
            translationMode = fail.tiger.komgarot.data.local.AiTranslationMode.HIGH_ACCURACY
        )

        assertEquals(2, masks.size)
        assertTrue(masks[0].y < masks[1].y)
    }

    @Test
    fun graphemeWrappingKeepsSurrogatePairsAndCombiningMarksTogether() {
        val lines = horizontalTargetTextLines(
            lines = listOf("🙂e\u0301a"),
            widthDp = 7f,
            fontSizeSp = 12f,
            targetLocale = "zh-CN"
        )

        assertEquals(listOf("🙂", "e\u0301", "a"), lines)
    }

    @Test
    fun graphemeWrappingKeepsZeroWidthJoinerEmojiTogether() {
        val lines = horizontalTargetTextLines(
            lines = listOf("👩‍💻a"),
            widthDp = 7f,
            fontSizeSp = 12f,
            targetLocale = "zh-CN"
        )

        assertEquals(listOf("👩‍💻", "a"), lines)
    }

    @Test
    fun wrappedTargetLinesStayWithinEstimatedAvailableWidth() {
        val texts = listOf(
            "The quick brown fox jumps over the lazy dog near the riverbank every single morning",
            "가나다라 마바사아 자차카타 파하가나 다라마바",
            "示例文本一行，用于验证换行宽度正常。"
        )
        val locales = listOf("en-US", "ko-KR", "zh-CN")
        for (fontSizeSp in listOf(16f, 12f, 9f, 6f)) {
            texts.forEachIndexed { index, text ->
                val lines = horizontalTargetTextLines(
                    lines = listOf(text),
                    widthDp = 120f,
                    fontSizeSp = fontSizeSp,
                    targetLocale = locales[index]
                )
                assertTrue("locale=${locales[index]} produced no lines", lines.isNotEmpty())
                val widest = horizontalTextLayoutWidthDp(lines, fontSizeSp)
                assertTrue(
                    "locale=${locales[index]} fontSize=$fontSizeSp width=$widest",
                    widest <= 120f + 0.01f
                )
            }
        }
    }
}
