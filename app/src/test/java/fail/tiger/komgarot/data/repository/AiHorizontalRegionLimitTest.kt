package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiHorizontalTranslationPolicy
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.*
import org.junit.Test

class AiHorizontalRegionLimitTest {
    @Test fun exactLimitKeepsEveryRegion() {
        val regions = (1..256).toList()
        assertEquals(regions, regions.checkedHorizontalRegionLimit(AiHorizontalTranslationPolicy.KOREAN_V1, 256, "paragraphs"))
    }

    @Test fun excessRegionsFailWithDiagnostic() {
        for (policy in listOf(AiHorizontalTranslationPolicy.ENGLISH_V1, AiHorizontalTranslationPolicy.KOREAN_V1)) {
            try {
                (1..257).toList().checkedHorizontalRegionLimit(policy, 256, "paragraphs")
                fail("Excess regions must fail")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("count=257"))
                assertTrue(error.message.orEmpty().contains("limit=256"))
            }
        }
    }

    @Test fun legacyKeepsExistingLimitBehavior() {
        assertEquals(listOf(1, 2), listOf(1, 2, 3).checkedHorizontalRegionLimit(AiHorizontalTranslationPolicy.LEGACY, 2, "legacy"))
        assertEquals(64, horizontalDetectionProbeLimit(AiHorizontalTranslationPolicy.LEGACY, 64))
    }

    @Test fun detectorReadsOneExtraCandidateToDistinguishExactLimit() {
        assertEquals(1025, horizontalDetectionProbeLimit(AiHorizontalTranslationPolicy.KOREAN_V1, 1024))
    }

    @Test fun fusedRegionStagesClampInsteadOfFailingThePage() {
        fun region(id: String, x: Float) = AiTranslationLocalTextRegion(
            id = id,
            rect = AiTranslationRect(x, 0.1f, 0.02f, 0.05f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.9f,
            estimatedFontScale = 0.6f
        )

        val paddleRegions = listOf(region("p1", 0.94f), region("p2", 0.96f))
        val heuristicRegions = (1..63).map { index -> region("h$index", index / 70f) }
        val selected = selectLocalTextDetectionRegions(
            paddleRegions = paddleRegions,
            heuristicRegions = { heuristicRegions },
            sourceTextProfile = fail.tiger.komgarot.data.local.AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON,
            maxRegions = 64,
            translationMode = AiTranslationMode.HIGH_ACCURACY,
            preserveLinesForBubbleGrouping = true,
            horizontalPolicy = AiHorizontalTranslationPolicy.KOREAN_V1
        )
        assertEquals(64, selected.size)

        val refined = refineLocalTextRegionsWithBubbles(
            regions = (1..300).map { index -> region("r$index", index / 1000f) },
            bubbles = emptyList<Any>(),
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            maxRegions = 256
        )
        assertEquals(256, refined.size)
    }
}
