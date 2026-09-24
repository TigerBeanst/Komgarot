package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiHorizontalTranslationPolicy
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHorizontalDetectionTilesTest {
    @Test
    fun onlyLongKoreanPagesUseDetectionTiles() {
        assertTrue(
            shouldUseAiHorizontalDetectionTiles(
                policy = AiHorizontalTranslationPolicy.KOREAN_V1,
                sourceWidth = 1200,
                sourceHeight = 7000,
                maxEdge = 3072
            )
        )
        assertFalse(
            shouldUseAiHorizontalDetectionTiles(
                policy = AiHorizontalTranslationPolicy.ENGLISH_V1,
                sourceWidth = 1200,
                sourceHeight = 7000,
                maxEdge = 3072
            )
        )
        assertFalse(
            shouldUseAiHorizontalDetectionTiles(
                policy = AiHorizontalTranslationPolicy.KOREAN_V1,
                sourceWidth = 1200,
                sourceHeight = 3000,
                maxEdge = 3072
            )
        )
    }

    @Test
    fun plannedTilesCoverThePageWithBoundedOverlap() {
        val tiles = planAiHorizontalDetectionTiles(
            sourceWidth = 1200,
            sourceHeight = 7000,
            maxEdge = 3072
        )

        assertTrue(tiles.size >= 3)
        assertEquals(0, tiles.first().top)
        assertEquals(7000, tiles.last().bottom)
        tiles.zipWithNext().forEach { (first, second) ->
            assertTrue(second.top < first.bottom)
            assertTrue(second.top > first.top)
            assertTrue(second.bottom <= 7000)
        }
    }

    @Test
    fun tileCoordinatesRestorePageNormalizedGeometry() {
        val tile = AiHorizontalDetectionTile(index = 1, left = 100, top = 2000, right = 1300, bottom = 5000)
        val region = AiTranslationLocalTextRegion(
            id = "tile-region",
            rect = AiTranslationRect(0.10f, 0.20f, 0.30f, 0.05f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.9f,
            estimatedFontScale = 1f,
            sourceLines = listOf(AiTranslationRect(0.12f, 0.30f, 0.25f, 0.04f)),
            horizontalLayoutVersion = 1
        )

        val mapped = mapAiHorizontalDetectionTileRegion(
            region = region,
            tile = tile,
            pageWidth = 2000,
            pageHeight = 8000,
            id = "page-region"
        )

        assertEquals("page-region", mapped.id)
        assertEquals(0.11f, mapped.rect.x, 0.0001f)
        assertEquals(0.325f, mapped.rect.y, 0.0001f)
        assertEquals(0.18f, mapped.rect.width, 0.0001f)
        assertEquals(0.01875f, mapped.rect.height, 0.0001f)
        assertEquals(0.3625f, mapped.sourceLines.single().y, 0.0001f)
    }

    @Test
    fun dedupeKeepsLargestBubbleWhenSortedCandidateOverlapsEarlierBubbles() {
        val a = AiBubbleRegion(rect = AiTranslationRect(x = 0f, y = 0f, width = 0.2f, height = 0.2f))
        val b = AiBubbleRegion(rect = AiTranslationRect(x = 0.29f, y = 0.01f, width = 0.2f, height = 0.2f))
        val c = AiBubbleRegion(rect = AiTranslationRect(x = 0.07f, y = 0.02f, width = 0.43f, height = 0.2f))

        val deduped = dedupeAiHorizontalDetectionBubbles(listOf(a, b, c))

        assertEquals(listOf(c), deduped)
    }

    @Test
    fun dedupeRetainsIndependentBubblesInPageOrder() {
        val lower = AiBubbleRegion(rect = AiTranslationRect(x = 0.4f, y = 0.3f, width = 0.1f, height = 0.1f))
        val upperRight = AiBubbleRegion(rect = AiTranslationRect(x = 0.8f, y = 0.1f, width = 0.1f, height = 0.1f))
        val upperLeft = AiBubbleRegion(rect = AiTranslationRect(x = 0.2f, y = 0.1f, width = 0.1f, height = 0.1f))

        val deduped = dedupeAiHorizontalDetectionBubbles(listOf(lower, upperRight, upperLeft))

        assertEquals(listOf(upperLeft, upperRight, lower), deduped)
    }

    @Test
    fun dedupeRetainsBubblesWithSameRectAndDisjointOutlines() {
        val rect = AiTranslationRect(x = 0.1f, y = 0.1f, width = 0.4f, height = 0.4f)
        val first = AiBubbleRegion(
            rect = rect,
            outline = listOf(
                AiTranslationPoint(0.1f, 0.1f),
                AiTranslationPoint(0.5f, 0.1f),
                AiTranslationPoint(0.1f, 0.5f)
            )
        )
        val second = AiBubbleRegion(
            rect = rect,
            outline = listOf(
                AiTranslationPoint(0.5f, 0.5f),
                AiTranslationPoint(0.5f, 0.1f),
                AiTranslationPoint(0.1f, 0.5f)
            )
        )

        assertEquals(2, dedupeAiHorizontalDetectionBubbles(listOf(first, second)).size)
    }

    @Test
    fun dedupeRemovesRepeatedOutlineWithSameRect() {
        val bubble = AiBubbleRegion(
            rect = AiTranslationRect(x = 0.1f, y = 0.1f, width = 0.4f, height = 0.4f),
            outline = listOf(
                AiTranslationPoint(0.1f, 0.1f),
                AiTranslationPoint(0.5f, 0.1f),
                AiTranslationPoint(0.5f, 0.5f),
                AiTranslationPoint(0.1f, 0.5f)
            )
        )

        assertEquals(1, dedupeAiHorizontalDetectionBubbles(listOf(bubble, bubble)).size)
    }

    @Test
    fun dedupeKeepsLegacyRectOverlapBehaviorWhenOutlinesAreMissing() {
        val first = AiBubbleRegion(AiTranslationRect(0.1f, 0.1f, 0.4f, 0.4f))
        val second = AiBubbleRegion(AiTranslationRect(0.1f, 0.1f, 0.4f, 0.4f))

        assertEquals(1, dedupeAiHorizontalDetectionBubbles(listOf(first, second)).size)
    }

    @Test
    fun dedupeRemovesHighlyOverlappingOffsetBubblesWithSameOutline() {
        val outline = listOf(
            AiTranslationPoint(0.1f, 0.1f),
            AiTranslationPoint(0.5f, 0.1f),
            AiTranslationPoint(0.5f, 0.5f),
            AiTranslationPoint(0.1f, 0.5f)
        )
        val first = AiBubbleRegion(AiTranslationRect(0.1f, 0.1f, 0.4f, 0.4f), outline = outline)
        val second = AiBubbleRegion(AiTranslationRect(0.11f, 0.11f, 0.4f, 0.4f), outline = outline)

        assertEquals(1, dedupeAiHorizontalDetectionBubbles(listOf(first, second)).size)
    }
}
