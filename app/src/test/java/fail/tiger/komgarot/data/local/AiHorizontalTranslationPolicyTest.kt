package fail.tiger.komgarot.data.local

import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHorizontalTranslationPolicyTest {
    @Test
    fun confirmedEnglishUsesEnglishHorizontalPolicy() {
        val state = AiSeriesSourceLanguageState(
            normalizedCode = "en-US",
            origin = AiSourceLanguageOrigin.KOMGA
        )

        assertEquals(
            AiHorizontalTranslationPolicy.ENGLISH_V1,
            resolveAiHorizontalTranslationPolicy(state, state.sourceTextProfile)
        )
    }

    @Test
    fun confirmedKoreanUsesKoreanHorizontalPolicy() {
        val state = AiSeriesSourceLanguageState(
            normalizedCode = "ko-KR",
            origin = AiSourceLanguageOrigin.AI
        )

        assertEquals(
            AiHorizontalTranslationPolicy.KOREAN_V1,
            resolveAiHorizontalTranslationPolicy(state, state.sourceTextProfile)
        )
    }

    @Test
    fun japaneseAndPendingSourcesKeepLegacyPolicy() {
        val japanese = AiSeriesSourceLanguageState(
            normalizedCode = "ja-JP",
            origin = AiSourceLanguageOrigin.KOMGA
        )
        val pending = AiSeriesSourceLanguageState(origin = AiSourceLanguageOrigin.AI_PENDING)

        assertEquals(
            AiHorizontalTranslationPolicy.LEGACY,
            resolveAiHorizontalTranslationPolicy(japanese, japanese.sourceTextProfile)
        )
        assertEquals(
            AiHorizontalTranslationPolicy.LEGACY,
            resolveAiHorizontalTranslationPolicy(pending, AiSourceTextProfile.HORIZONTAL_COMIC)
        )
    }

    @Test
    fun legacyCacheKeyAndEmptyLineGeometryStayCompatible() {
        val legacyKey = "local-v22-mask-placement:local-detection-v1"
        assertEquals(
            legacyKey,
            horizontalVersionedCacheKey(legacyKey, AiHorizontalTranslationPolicy.LEGACY)
        )

        val region = AiTranslationLocalTextRegion(
            id = "r1",
            rect = AiTranslationRect(x = 0.1f, y = 0.1f, width = 0.2f, height = 0.1f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.8f,
            estimatedFontScale = 1f
        )
        assertTrue(region.sourceLines.isEmpty())
        assertEquals(0, region.horizontalLayoutVersion)
        assertTrue(AiTranslationBlock().sourceLines.isEmpty())
        assertEquals(0, AiTranslationBlock().horizontalLayoutVersion)
    }

    @Test
    fun invalidVersionedLineGeometryFallsBackSafely() {
        val safe = AiTranslationBlock(
            sourceLines = listOf(AiTranslationRect(x = Float.NaN, y = 0.1f, width = 0.2f, height = 0.04f)),
            horizontalLayoutVersion = 1
        ).renderSafe()

        assertTrue(safe.sourceLines.isEmpty())
    }

    @Test
    fun unknownHorizontalLayoutVersionsUseLegacyRendering() {
        val safe = AiTranslationBlock(horizontalLayoutVersion = 7).renderSafe()

        assertEquals(0, safe.horizontalLayoutVersion)
    }
}
