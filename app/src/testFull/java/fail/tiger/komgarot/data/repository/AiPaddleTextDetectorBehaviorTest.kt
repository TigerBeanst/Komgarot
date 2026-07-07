package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
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

    @Test
    fun paddleInkComponentsKeepDarkAndLightGlyphsInSameBounds() {
        val width = 120
        val height = 120
        val pixels = IntArray(width * height) { 0xFF888888.toInt() }
        fun drawGlyph(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top..bottom) {
                for (x in left..right) {
                    pixels[y * width + x] = color
                }
            }
        }
        drawGlyph(22, 28, 30, 44, 0xFF000000.toInt())
        drawGlyph(66, 28, 74, 44, 0xFFFFFFFF.toInt())

        val components = findPaddleInkComponentsForRect(
            pixels = pixels,
            imageWidth = width,
            imageHeight = height,
            left = 10,
            top = 14,
            right = 92,
            bottom = 78
        )

        assertTrue("components=$components", components.any { it.centerX in 20f..34f && it.darkPixels > 0 })
        assertTrue("components=$components", components.any { it.centerX in 62f..78f && it.lightPixels > 0 })
    }

    @Test
    fun broadPaddleRectSplitsIntoInkColumnsBeforeMangaTextBlockMerging() {
        val width = 240
        val height = 320
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        fun drawGlyph(left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top..bottom) {
                for (x in left..right) {
                    pixels[y * width + x] = 0xFF000000.toInt()
                }
            }
        }
        listOf(160, 146, 132).forEach { x ->
            listOf(48, 70, 92, 114).forEach { y ->
                drawGlyph(x, y, x + 6, y + 12)
            }
        }
        listOf(78, 100, 122).forEach { y ->
            drawGlyph(64, y, 74, y + 14)
        }
        val inkComponents = findPaddleInkComponentsForRect(
            pixels = pixels,
            imageWidth = width,
            imageHeight = height,
            left = 48,
            top = 38,
            right = 180,
            bottom = 154
        )

        val splitRects = splitPaddleTextRectIntoInkTextLineRects(
            rect = AiTranslationRect(x = 0.20f, y = 0.12f, width = 0.55f, height = 0.36f),
            pixels = pixels,
            imageWidth = width,
            imageHeight = height,
            sourceWidth = width,
            sourceHeight = height,
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA
        )
        val regions = splitRects.mapIndexed { index, rect ->
            AiTranslationLocalTextRegion(
                id = "p0-r${index + 1}",
                rect = rect,
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.90f,
                estimatedFontScale = 0.90f
            )
        }

        assertEquals("components=$inkComponents", 15, inkComponents.size)
        val merged = mergeLocalTextRegionsIntoTextBoxes(
            regions = regions,
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(splitRects.joinToString(), 4, splitRects.size)
        assertEquals("split=$splitRects merged=${merged.map { it.rect to it.sourceColumns }}", 2, merged.size)
        assertEquals(3, merged.first().sourceColumns.size)
        assertEquals(1, merged.last().sourceColumns.size)
    }

    @Test
    fun narrowVerticalPaddleRectKeepsOneTextLineWhenGlyphInkShiftsSideways() {
        val width = 240
        val height = 320
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        fun drawGlyph(left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top..bottom) {
                for (x in left..right) {
                    pixels[y * width + x] = 0xFF000000.toInt()
                }
            }
        }
        listOf(
            96 to 46,
            112 to 74,
            97 to 102,
            111 to 130
        ).forEach { (x, y) ->
            drawGlyph(x, y, x + 7, y + 13)
        }

        val splitRects = splitPaddleTextRectIntoInkTextLineRects(
            rect = AiTranslationRect(x = 0.36f, y = 0.12f, width = 0.18f, height = 0.42f),
            pixels = pixels,
            imageWidth = width,
            imageHeight = height,
            sourceWidth = width,
            sourceHeight = height,
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(splitRects.joinToString(), 1, splitRects.size)
    }
}
