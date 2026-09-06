package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSplitPagesTest {
    @Test
    fun splitRectMapsSelectedHalfToFullSegmentWidth() {
        val left = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.2f, height = 0.1f)
            .forReaderPageSegment(ReaderPageSegment.LEFT_HALF)
        val right = AiTranslationRect(x = 0.6f, y = 0.2f, width = 0.2f, height = 0.1f)
            .forReaderPageSegment(ReaderPageSegment.RIGHT_HALF)

        assertEquals(0.2f, left?.x ?: -1f, 0.001f)
        assertEquals(0.4f, left?.width ?: -1f, 0.001f)
        assertEquals(0.2f, right?.x ?: -1f, 0.001f)
        assertEquals(0.4f, right?.width ?: -1f, 0.001f)
        assertNull(
            AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.2f, height = 0.1f)
                .forReaderPageSegment(ReaderPageSegment.RIGHT_HALF)
        )
    }

    @Test
    fun splitTranslationPageKeepsBlocksFromSelectedHalf() {
        val page = AiTranslatedPage(
            imageWidth = 2400,
            imageHeight = 1600,
            blocks = listOf(
                AiTranslationBlock(
                    localRegionId = "left",
                    rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.1f, height = 0.1f)
                ),
                AiTranslationBlock(
                    localRegionId = "right",
                    rect = AiTranslationRect(x = 0.7f, y = 0.2f, width = 0.1f, height = 0.1f)
                )
            )
        )

        val right = page.forReaderPageSegment(ReaderPageSegment.RIGHT_HALF)

        assertEquals(1200, right.imageWidth)
        assertEquals(listOf("right"), right.blocks.map(AiTranslationBlock::localRegionId))
    }

    @Test
    fun splitPageClipsBubbleOutlineAtCenterBoundary() {
        val page = AiTranslatedPage(
            imageWidth = 2000,
            imageHeight = 3000,
            blocks = listOf(
                AiTranslationBlock(
                    rect = AiTranslationRect(0.38f, 0.20f, 0.10f, 0.20f),
                    translationRect = AiTranslationRect(0.36f, 0.18f, 0.12f, 0.24f),
                    bubbleOutline = listOf(
                        AiTranslationPoint(0.34f, 0.18f),
                        AiTranslationPoint(0.54f, 0.22f),
                        AiTranslationPoint(0.52f, 0.44f),
                        AiTranslationPoint(0.35f, 0.42f)
                    )
                )
            )
        )

        val left = page.forReaderPageSegment(ReaderPageSegment.LEFT_HALF)
        val outline = left.blocks.single().bubbleOutline

        assertTrue(outline.size >= 4)
        assertTrue(outline.all { it.x in 0f..1f })
        assertTrue(outline.any { it.x == 1f })
    }
}
