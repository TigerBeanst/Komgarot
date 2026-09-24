package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiHorizontalTranslationPolicy
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHorizontalTextRegionTest {
    @Test
    fun englishPolicyRecordsOneSourceLinePerDetectedRegion() {
        val first = region("r1", 0.10f, 0.10f, 0.18f, 0.04f)
        val second = region("r2", 0.11f, 0.17f, 0.22f, 0.04f)

        val result = annotateHorizontalTextRegions(
            regions = listOf(first, second),
            policy = AiHorizontalTranslationPolicy.ENGLISH_V1
        )

        assertEquals(listOf(first.rect), result[0].sourceLines)
        assertEquals(listOf(second.rect), result[1].sourceLines)
        assertEquals(1, result[0].horizontalLayoutVersion)
        assertEquals(AiTranslationTextDirection.HORIZONTAL, result[0].textDirection)
    }

    @Test
    fun englishPolicyDeduplicatesOverlappingDetectionsIntoOneUnionSourceLine() {
        val first = region("r1", 0.10f, 0.20f, 0.10f, 0.04f)
        val second = region("r2", 0.101f, 0.201f, 0.10f, 0.04f)

        val collapsed = collapseHighlyOverlappingLocalTextRegions(listOf(first, second))
        val annotated = annotateHorizontalTextRegions(
            regions = collapsed,
            policy = AiHorizontalTranslationPolicy.ENGLISH_V1
        )

        assertEquals(1, collapsed.size)
        assertEquals(1, annotated.size)
        assertEquals(1, annotated.single().sourceLines.size)
        val union = annotated.single().sourceLines.single()
        assertTrue(union.x <= first.rect.x)
        assertTrue(union.y <= first.rect.y)
        assertTrue(union.x + union.width >= first.rect.x + first.rect.width)
        assertTrue(union.y + union.height >= first.rect.y + first.rect.height)
        assertTrue(union.x <= second.rect.x)
        assertTrue(union.y <= second.rect.y)
        assertTrue(union.x + union.width >= second.rect.x + second.rect.width)
        assertTrue(union.y + union.height >= second.rect.y + second.rect.height)
    }

    @Test
    fun englishPolicyKeepsAdjacentIndependentLinesSeparate() {
        val first = region("r1", 0.10f, 0.20f, 0.10f, 0.04f)
        val second = region("r2", 0.10f, 0.26f, 0.10f, 0.04f)

        val collapsed = collapseHighlyOverlappingLocalTextRegions(listOf(first, second))
        val annotated = annotateHorizontalTextRegions(collapsed, AiHorizontalTranslationPolicy.ENGLISH_V1)

        assertEquals(2, collapsed.size)
        assertEquals(2, annotated.size)
        assertEquals(2, annotated.sumOf { it.sourceLines.size })
    }

    @Test
    fun legacyPolicyReturnsOriginalRegions() {
        val original = region("r1", 0.10f, 0.10f, 0.18f, 0.04f)

        val result = annotateHorizontalTextRegions(
            regions = listOf(original),
            policy = AiHorizontalTranslationPolicy.LEGACY
        )

        assertEquals(listOf(original), result)
        assertSame(original, result.single())
    }

    @Test
    fun horizontalPolicyRaisesCandidateBudgetButKeepsLegacyBudget() {
        assertEquals(1024, horizontalCandidateMaxRegions(AiHorizontalTranslationPolicy.ENGLISH_V1, 64))
        assertEquals(256, horizontalFinalMaxRegions(AiHorizontalTranslationPolicy.KOREAN_V1, 64))
        assertEquals(64, horizontalCandidateMaxRegions(AiHorizontalTranslationPolicy.LEGACY, 64))
        assertEquals(64, horizontalFinalMaxRegions(AiHorizontalTranslationPolicy.LEGACY, 64))
    }

    @Test
    fun mergedHorizontalFragmentsRetainBothSourceLines() {
        val first = annotateHorizontalTextRegions(
            listOf(region("r1", 0.10f, 0.20f, 0.10f, 0.04f)),
            AiHorizontalTranslationPolicy.ENGLISH_V1
        ).single()
        val second = annotateHorizontalTextRegions(
            listOf(region("r2", 0.22f, 0.20f, 0.10f, 0.04f)),
            AiHorizontalTranslationPolicy.ENGLISH_V1
        ).single()

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            regions = listOf(first, second),
            sourceTextProfile = fail.tiger.komgarot.data.local.AiSourceTextProfile.HORIZONTAL_COMIC
        )

        assertEquals(1, merged.size)
        assertEquals(2, merged.single().sourceLines.size)
        assertEquals(1, merged.single().horizontalLayoutVersion)
    }

    @Test
    fun horizontalPolicyAnnotatesUnmatchedBubbleFallbacks() {
        val fallback = groupLocalTextRegionsByBubbles(
            regions = emptyList(),
            bubbles = listOf(AiTranslationRect(0.2f, 0.2f, 0.3f, 0.2f))
        )

        val annotated = annotateHorizontalTextRegions(
            regions = fallback,
            policy = AiHorizontalTranslationPolicy.ENGLISH_V1
        )

        assertEquals(1, annotated.single().horizontalLayoutVersion)
        assertEquals(listOf(annotated.single().rect), annotated.single().sourceLines)
    }

    @Test
    fun horizontalHighAccuracyFusesHeuristicRegionsAcrossDensePages() {
        val paddle = (0 until 3).map { index ->
            region("p$index", 0.1f, 0.1f + index * 0.12f, 0.18f, 0.05f)
        }
        val heuristic = region("h", 0.7f, 0.75f, 0.16f, 0.05f)

        val selected = selectLocalTextDetectionRegions(
            paddleRegions = paddle,
            heuristicRegions = { listOf(heuristic) },
            sourceTextProfile = fail.tiger.komgarot.data.local.AiSourceTextProfile.HORIZONTAL_COMIC,
            maxRegions = 1024,
            translationMode = fail.tiger.komgarot.data.local.AiTranslationMode.HIGH_ACCURACY,
            horizontalPolicy = AiHorizontalTranslationPolicy.ENGLISH_V1,
            fuseHeuristicForHorizontalPolicy = true
        )

        assertEquals(4, selected.size)
        assertEquals("h", selected.last().id)
        assertEquals(listOf(heuristic.rect), selected.last().sourceLines)
    }

    private fun region(id: String, x: Float, y: Float, width: Float, height: Float) =
        AiTranslationLocalTextRegion(
            id = id,
            rect = AiTranslationRect(x, y, width, height),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.8f,
            estimatedFontScale = 1f
        )
}
