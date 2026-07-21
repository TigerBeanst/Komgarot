package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiSourceLanguageTest {
    @Test
    fun commonLanguageAliasesNormalizeToBcp47Tags() {
        assertEquals("en", normalizeAiSourceLanguageTag("eng"))
        assertEquals("ja-JP", normalizeAiSourceLanguageTag("JPN_jp"))
        assertEquals("ko", normalizeAiSourceLanguageTag("kor"))
        assertEquals("zh-Hans", normalizeAiSourceLanguageTag("zho-Hans"))
        assertEquals("th", normalizeAiSourceLanguageTag("tha"))
        assertEquals("fr-CA", normalizeAiSourceLanguageTag("fr-ca"))
    }

    @Test
    fun unknownLanguageTagsStayUnresolved() {
        assertEquals("", normalizeAiSourceLanguageTag("jp-JP"))
        assertEquals("", normalizeAiSourceLanguageTag("unknown"))
        assertEquals("", normalizeAiSourceLanguageTag(""))
    }

    @Test
    fun komgaLanguageOverridesAiInference() {
        val cached = AiSeriesSourceLanguageState(
            seriesId = "series-1",
            normalizedCode = "ko",
            origin = AiSourceLanguageOrigin.AI
        )

        val resolved = resolveAiSourceLanguageFromKomga(
            seriesId = "series-1",
            rawLanguage = "ja-JP",
            rawReadingDirection = "RIGHT_TO_LEFT",
            cachedState = cached
        )

        assertEquals("ja-JP", resolved.normalizedCode)
        assertEquals(AiSourceLanguageOrigin.KOMGA, resolved.origin)
        assertEquals(AiSourceReadingDirection.RIGHT_TO_LEFT, resolved.readingDirection)
        assertEquals(AiSourceTextProfile.JAPANESE_MANGA, resolved.sourceTextProfile)
    }

    @Test
    fun blankKomgaLanguageReusesConfirmedAiInference() {
        val cached = AiSeriesSourceLanguageState(
            seriesId = "series-1",
            normalizedCode = "en",
            origin = AiSourceLanguageOrigin.AI
        )

        val resolved = resolveAiSourceLanguageFromKomga(
            seriesId = "series-1",
            rawLanguage = "",
            rawReadingDirection = "LEFT_TO_RIGHT",
            cachedState = cached
        )

        assertEquals("en", resolved.normalizedCode)
        assertEquals(AiSourceLanguageOrigin.AI, resolved.origin)
        assertEquals(AiSourceTextProfile.HORIZONTAL_COMIC, resolved.sourceTextProfile)
    }

    @Test
    fun twoMatchingDetectionsWithinThreeEvidenceConfirmAiLanguage() {
        val pending = AiSeriesSourceLanguageState(seriesId = "series-1")

        val confirmed = pending
            .recordAiEvidence("ko")
            .recordAiEvidence("en")
            .recordAiEvidence("KOR")

        assertEquals(AiSourceLanguageOrigin.AI, confirmed.origin)
        assertEquals("ko", confirmed.normalizedCode)
        assertEquals(listOf("ko", "en", "ko"), confirmed.evidence)
    }

    @Test
    fun invalidEvidenceDoesNotConsumeConfirmationWindow() {
        val pending = AiSeriesSourceLanguageState(seriesId = "series-1")

        val updated = pending
            .recordAiEvidence("unknown")
            .recordAiEvidence(null)

        assertEquals(AiSourceLanguageOrigin.AI_PENDING, updated.origin)
        assertEquals(emptyList<String>(), updated.evidence)
    }

    @Test
    fun invalidKomgaLanguageKeepsRawValueAndStartsFreshInference() {
        val resolved = resolveAiSourceLanguageFromKomga(
            seriesId = "series-1",
            rawLanguage = "unknown",
            rawReadingDirection = "LEFT_TO_RIGHT",
            cachedState = AiSeriesSourceLanguageState(
                seriesId = "series-1",
                normalizedCode = "ja",
                origin = AiSourceLanguageOrigin.AI
            )
        )

        assertEquals("", resolved.normalizedCode)
        assertEquals("unknown", resolved.rawKomgaValue)
        assertEquals(AiSourceLanguageOrigin.AI_PENDING, resolved.origin)
        assertEquals(emptyList<String>(), resolved.evidence)
    }

    @Test
    fun metadataFailureUsesSeriesCacheOrPendingState() {
        val cached = AiSeriesSourceLanguageState(
            seriesId = "series-1",
            normalizedCode = "th",
            origin = AiSourceLanguageOrigin.KOMGA
        )

        assertEquals(cached, aiSourceLanguageOnMetadataFailure("series-1", cached))
        assertEquals(
            AiSourceLanguageOrigin.AI_PENDING,
            aiSourceLanguageOnMetadataFailure("series-2", cached).origin
        )
    }

    @Test
    fun komgaReadingDirectionsNormalizeForPagedComics() {
        assertEquals(
            AiSourceReadingDirection.RIGHT_TO_LEFT,
            AiSourceReadingDirection.fromKomgaValue("RIGHT_TO_LEFT")
        )
        assertEquals(
            AiSourceReadingDirection.LEFT_TO_RIGHT,
            AiSourceReadingDirection.fromKomgaValue("left-to-right")
        )
        assertEquals(
            AiSourceReadingDirection.TOP_TO_BOTTOM,
            AiSourceReadingDirection.fromKomgaValue("vertical")
        )
        assertEquals(
            AiSourceReadingDirection.UNKNOWN,
            AiSourceReadingDirection.fromKomgaValue(null)
        )
    }

    @Test
    fun languageAndReadingDirectionParticipateInDetectionCacheKey() {
        val japanese = AiSeriesSourceLanguageState(
            seriesId = "series-1",
            normalizedCode = "ja",
            rawKomgaValue = "ja",
            origin = AiSourceLanguageOrigin.KOMGA,
            readingDirection = AiSourceReadingDirection.RIGHT_TO_LEFT
        )

        assertNotEquals(
            japanese.detectionCacheKey(),
            japanese.copy(readingDirection = AiSourceReadingDirection.LEFT_TO_RIGHT).detectionCacheKey()
        )
        assertNotEquals(
            japanese.detectionCacheKey(),
            japanese.copy(normalizedCode = "en", rawKomgaValue = "en").detectionCacheKey()
        )
    }
}
