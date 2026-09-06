package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBubbleTextRegionGrouperTest {
    @Test
    fun groupedRegionCarriesBubbleOutlineAndModelSafeTextBounds() {
        val text = region("center", AiTranslationRect(0.45f, 0.30f, 0.04f, 0.16f), 0.8f)
        val safeBounds = AiTranslationRect(0.38f, 0.22f, 0.20f, 0.34f)
        val outline = listOf(
            AiTranslationPoint(0.32f, 0.18f),
            AiTranslationPoint(0.64f, 0.18f),
            AiTranslationPoint(0.64f, 0.62f),
            AiTranslationPoint(0.32f, 0.62f)
        )

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(text),
            bubbles = listOf(
                AiBubbleRegion(
                    rect = AiTranslationRect(0.32f, 0.18f, 0.32f, 0.44f),
                    safeTextRect = safeBounds,
                    outline = outline,
                    solidFill = true
                )
            )
        ).single()

        assertEquals(safeBounds, grouped.renderBounds)
        assertEquals(outline, grouped.bubbleOutline)
        assertTrue(grouped.bubbleSolidFill)
    }

    @Test
    fun verticalColumnsInsideOneBubbleShareOneRenderRegionAndFontBaseline() {
        val rightColumn = region(
            id = "right",
            rect = AiTranslationRect(0.70f, 0.20f, 0.04f, 0.22f),
            fontScale = 0.54f
        )
        val leftColumn = region(
            id = "left",
            rect = AiTranslationRect(0.65f, 0.21f, 0.04f, 0.21f),
            fontScale = 0.78f
        )
        val bubble = AiTranslationRect(0.60f, 0.14f, 0.20f, 0.36f)

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(rightColumn, leftColumn),
            bubbles = listOf(bubble)
        )

        assertEquals(1, grouped.size)
        assertEquals(0.78f, grouped.single().estimatedFontScale, 0.0001f)
        assertEquals(bubble, grouped.single().aiCropBounds)
        assertEquals(2, grouped.single().sourceColumns.size)
        assertTrue(grouped.single().renderBounds.width < bubble.width)
        assertTrue(grouped.single().renderBounds.height < bubble.height)
        assertTrue(grouped.single().renderBounds.x >= bubble.x + bubble.width * 0.12f)
        assertTrue(grouped.single().renderBounds.y >= bubble.y + bubble.height * 0.09f)
    }

    @Test
    fun tallBubbleKeepsVerticalDirectionWhenOneMemberIsMisclassifiedAsHorizontal() {
        val vertical = region(
            id = "vertical",
            rect = AiTranslationRect(0.44f, 0.18f, 0.035f, 0.24f),
            fontScale = 0.72f,
            direction = AiTranslationTextDirection.VERTICAL
        )
        val horizontalNoise = region(
            id = "horizontal",
            rect = AiTranslationRect(0.40f, 0.30f, 0.12f, 0.035f),
            fontScale = 0.74f,
            direction = AiTranslationTextDirection.HORIZONTAL
        )

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(horizontalNoise, vertical),
            bubbles = listOf(AiTranslationRect(0.34f, 0.10f, 0.22f, 0.42f))
        ).single()

        assertEquals(AiTranslationTextDirection.VERTICAL, grouped.textDirection)
    }

    @Test
    fun overlappingBubblesAssignRegionToSmallestContainingBubble() {
        val text = region(
            id = "text",
            rect = AiTranslationRect(0.42f, 0.32f, 0.04f, 0.12f),
            fontScale = 0.64f
        )
        val large = AiTranslationRect(0.20f, 0.10f, 0.60f, 0.70f)
        val small = AiTranslationRect(0.38f, 0.26f, 0.16f, 0.26f)

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(text),
            bubbles = listOf(large, small)
        )

        assertEquals(small, grouped.single().aiCropBounds)
    }

    @Test
    fun regionOutsideEveryBubbleKeepsOriginalGeometryAlongsideBubbleFallback() {
        val text = region(
            id = "outside",
            rect = AiTranslationRect(0.08f, 0.70f, 0.12f, 0.06f),
            fontScale = 0.62f
        )

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(text),
            bubbles = listOf(AiTranslationRect(0.60f, 0.10f, 0.30f, 0.30f))
        )

        assertTrue(grouped.any { it === text })
        assertTrue(grouped.any { it.aiCropBounds == AiTranslationRect(0.60f, 0.10f, 0.30f, 0.30f) })
    }

    @Test
    fun detectedBubbleWithoutOcrRegionStillCreatesRemoteVisionRegion() {
        val detectedText = region(
            id = "detected",
            rect = AiTranslationRect(0.68f, 0.18f, 0.04f, 0.18f),
            fontScale = 0.72f
        )
        val detectedBubble = AiBubbleRegion(
            rect = AiTranslationRect(0.62f, 0.12f, 0.16f, 0.30f),
            safeTextRect = AiTranslationRect(0.65f, 0.16f, 0.10f, 0.22f),
            solidFill = true
        )
        val missingOutline = listOf(
            AiTranslationPoint(0.18f, 0.58f),
            AiTranslationPoint(0.38f, 0.58f),
            AiTranslationPoint(0.38f, 0.84f),
            AiTranslationPoint(0.18f, 0.84f)
        )
        val missingBubble = AiBubbleRegion(
            rect = AiTranslationRect(0.18f, 0.58f, 0.20f, 0.26f),
            safeTextRect = AiTranslationRect(0.21f, 0.61f, 0.14f, 0.20f),
            outline = missingOutline,
            solidFill = true
        )

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(detectedText),
            bubbles = listOf(detectedBubble, missingBubble)
        )

        assertEquals(2, grouped.size)
        val fallback = grouped.single { it.aiCropBounds == missingBubble.rect }
        assertEquals(missingBubble.safeTextRect, fallback.renderBounds)
        assertEquals(missingOutline, fallback.bubbleOutline)
        assertTrue(fallback.id.startsWith("bubble-1-fallback"))
    }

    @Test
    fun broadUnanchoredDetectionIsExcludedFromHighAccuracyRegions() {
        val broad = region(
            id = "broad",
            rect = AiTranslationRect(0.18f, 0.18f, 0.34f, 0.32f),
            fontScale = 1f,
            direction = AiTranslationTextDirection.HORIZONTAL
        )
        val narrow = region(
            id = "narrow",
            rect = AiTranslationRect(0.06f, 0.72f, 0.12f, 0.05f),
            fontScale = 0.68f,
            direction = AiTranslationTextDirection.HORIZONTAL
        )

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(broad, narrow),
            bubbles = listOf(AiTranslationRect(0.68f, 0.10f, 0.18f, 0.28f))
        )

        assertTrue(grouped.none { it.id == broad.id })
        assertTrue(grouped.any { it.id == narrow.id })
    }

    @Test
    fun distantTextGroupsInsideOneBubbleShareOneTranslationRegion() {
        val top = region(
            id = "top",
            rect = AiTranslationRect(0.42f, 0.16f, 0.12f, 0.05f),
            fontScale = 0.70f,
            direction = AiTranslationTextDirection.HORIZONTAL
        )
        val bottom = region(
            id = "bottom",
            rect = AiTranslationRect(0.43f, 0.48f, 0.11f, 0.05f),
            fontScale = 0.66f,
            direction = AiTranslationTextDirection.HORIZONTAL
        )
        val bubble = AiTranslationRect(0.32f, 0.10f, 0.34f, 0.52f)

        val grouped = groupLocalTextRegionsByBubbles(
            regions = listOf(top, bottom),
            bubbles = listOf(bubble)
        )

        assertEquals(1, grouped.size)
        assertEquals(bubble, grouped.single().aiCropBounds)
        assertTrue(grouped.all { it.aiCropBounds.x >= bubble.x })
        assertTrue(grouped.all { it.aiCropBounds.x + it.aiCropBounds.width <= bubble.x + bubble.width })
    }

    private fun region(
        id: String,
        rect: AiTranslationRect,
        fontScale: Float,
        direction: AiTranslationTextDirection = AiTranslationTextDirection.VERTICAL
    ) = AiTranslationLocalTextRegion(
        id = id,
        rect = rect,
        textDirection = direction,
        textColor = "#111111",
        backgroundColor = "#FFFFFF",
        confidence = 0.9f,
        estimatedFontScale = fontScale,
        textBounds = rect,
        sourceColumns = listOf(rect)
    )
}
