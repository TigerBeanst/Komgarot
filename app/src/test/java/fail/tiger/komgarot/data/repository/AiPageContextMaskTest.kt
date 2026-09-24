package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationPoint
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPageContextMaskTest {
    private fun region(
        columns: List<AiTranslationRect> = emptyList(),
        lines: List<AiTranslationRect> = emptyList(),
        horizontalVersion: Int = 0,
        textBounds: AiTranslationRect = AiTranslationRect(),
        outline: List<AiTranslationPoint> = emptyList(),
        solid: Boolean = false,
        rect: AiTranslationRect = AiTranslationRect(0.2f, 0.2f, 0.2f, 0.2f)
    ) = AiTranslationLocalTextRegion(
        id = "region",
        rect = rect,
        textDirection = AiTranslationTextDirection.VERTICAL,
        textColor = "#111111",
        backgroundColor = "#FFFFFF",
        confidence = 0.9f,
        estimatedFontScale = 1f,
        textBounds = textBounds,
        sourceColumns = columns,
        sourceLines = lines,
        horizontalLayoutVersion = horizontalVersion,
        bubbleOutline = outline,
        bubbleSolidFill = solid
    )

    @Test
    fun largeSourceColumnGrowsPaddingWithTextShortSide() {
        val rect = pageContextTextMaskRectsForAi(
            1000, 1000,
            listOf(region(columns = listOf(AiTranslationRect(0.40f, 0.20f, 0.04f, 0.20f))))
        ).single()

        assertTrue(rect.left <= 394)
        assertTrue(rect.right >= 446)
        assertTrue(rect.top <= 194)
        assertTrue(rect.bottom >= 406)
    }

    @Test
    fun fallbackTextBoundsAlsoExpandsAndClipsToImage() {
        val rect = pageContextTextMaskRectsForAi(
            1000, 1000,
            listOf(region(textBounds = AiTranslationRect(-0.01f, 0.98f, 0.08f, 0.08f)))
        ).single()

        assertEquals(0, rect.left)
        assertEquals(80, rect.right)
        assertEquals(970, rect.top)
        assertEquals(1000, rect.bottom)
    }

    @Test
    fun horizontalSourceLinesRemainIndependent() {
        val rects = pageContextTextMaskRectsForAi(
            1000, 1000,
            listOf(
                region(
                    horizontalVersion = 1,
                    lines = listOf(
                        AiTranslationRect(0.10f, 0.20f, 0.30f, 0.04f),
                        AiTranslationRect(0.10f, 0.30f, 0.20f, 0.05f)
                    ),
                    columns = listOf(AiTranslationRect(0.1f, 0.1f, 0.04f, 0.5f))
                )
            )
        )

        assertEquals(2, rects.size)
        assertEquals(AiPageContextMaskRect(92, 192, 408, 249), rects[0])
        assertEquals(AiPageContextMaskRect(90, 290, 310, 361), rects[1])
    }

    @Test
    fun solidSmallBubbleUsesWholeOutlineBoundsAndClip() {
        val outline = listOf(
            AiTranslationPoint(0.10f, 0.10f), AiTranslationPoint(0.30f, 0.10f),
            AiTranslationPoint(0.30f, 0.30f), AiTranslationPoint(0.10f, 0.30f)
        )
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline, solid = true)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(100, 100, 300, 300)), plan.rects)
        assertEquals(outline, plan.clipOutline)
    }

    @Test
    fun solidSmallNonRectangularBubblePreservesOutlineAndUsesItsBounds() {
        val outline = listOf(
            AiTranslationPoint(0.10f, 0.10f), AiTranslationPoint(0.30f, 0.10f),
            AiTranslationPoint(0.20f, 0.20f), AiTranslationPoint(0.30f, 0.30f),
            AiTranslationPoint(0.10f, 0.30f)
        )
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline, solid = true)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(100, 100, 300, 300)), plan.rects)
        assertEquals(outline, plan.clipOutline)
    }

    @Test
    fun texturedSmallBubbleKeepsLocalTextRectsAndUsesOutlineClip() {
        val outline = listOf(
            AiTranslationPoint(0.01f, 0.01f), AiTranslationPoint(0.20f, 0.01f),
            AiTranslationPoint(0.20f, 0.20f), AiTranslationPoint(0.01f, 0.20f)
        )
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(156, 156, 184, 204)), plan.rects)
        assertEquals(outline, plan.clipOutline)
    }

    @Test
    fun hugeSolidBubbleKeepsLocalTextRectsAndUsesOutlineClip() {
        val outline = listOf(AiTranslationPoint(0.01f, 0.01f), AiTranslationPoint(0.99f, 0.01f), AiTranslationPoint(0.99f, 0.99f), AiTranslationPoint(0.01f, 0.99f))
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline, solid = true)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(156, 156, 184, 204)), plan.rects)
        assertEquals(outline, plan.clipOutline)
    }

    @Test
    fun invalidOutlineFallsBackToTextRectsAndEmptyClip() {
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = listOf(AiTranslationPoint(Float.NaN, 0.1f)), solid = true)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(156, 156, 184, 204)), plan.rects)
        assertTrue(plan.clipOutline.isEmpty())
    }

    @Test
    fun degenerateAndOutOfBoundsOutlinesFallBackToTextRectsAndEmptyClip() {
        val outlines = listOf(
            listOf(AiTranslationPoint(0.1f, 0.1f), AiTranslationPoint(0.2f, 0.2f)),
            listOf(AiTranslationPoint(-2f, 0.1f), AiTranslationPoint(2f, 0.1f), AiTranslationPoint(2f, 2f))
        )

        outlines.forEach { outline ->
            val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
                region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline, solid = true)
            )).single()
            assertEquals(listOf(AiPageContextMaskRect(156, 156, 184, 204)), plan.rects)
            assertTrue(plan.clipOutline.isEmpty())
        }
    }

    @Test
    fun selfIntersectingOutlineFallsBackToTextRectsAndEmptyClip() {
        val outline = listOf(
            AiTranslationPoint(0.10f, 0.10f), AiTranslationPoint(0.30f, 0.30f),
            AiTranslationPoint(0.10f, 0.30f), AiTranslationPoint(0.25f, 0.10f)
        )
        val plan = pageContextMaskPlansForAi(1000, 1000, listOf(
            region(columns = listOf(AiTranslationRect(0.16f, 0.16f, 0.02f, 0.04f)), outline = outline, solid = true)
        )).single()

        assertEquals(listOf(AiPageContextMaskRect(156, 156, 184, 204)), plan.rects)
        assertTrue(plan.clipOutline.isEmpty())
    }

    @Test
    fun oversizedTextUsesShortSidePaddingCap() {
        val rect = pageContextTextMaskRectsForAi(
            1000, 1000,
            listOf(region(columns = listOf(AiTranslationRect(0.40f, 0.20f, 0.40f, 0.40f))))
        ).single()

        assertEquals(AiPageContextMaskRect(390, 190, 810, 610), rect)
    }
}
