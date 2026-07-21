package fail.tiger.komgarot.data.local

import java.util.Locale

enum class AiSourceLanguageOrigin(val storedValue: String) {
    KOMGA("komga"),
    AI_PENDING("ai_pending"),
    AI("ai");

    companion object {
        fun fromStoredValue(value: String?): AiSourceLanguageOrigin =
            entries.firstOrNull { it.storedValue == value } ?: AI_PENDING
    }
}

enum class AiSourceReadingDirection(val storedValue: String) {
    LEFT_TO_RIGHT("left_to_right"),
    RIGHT_TO_LEFT("right_to_left"),
    TOP_TO_BOTTOM("top_to_bottom"),
    UNKNOWN("unknown");

    companion object {
        fun fromKomgaValue(value: String?): AiSourceReadingDirection = when (
            value.orEmpty().trim().replace('-', '_').uppercase(Locale.ROOT)
        ) {
            "LEFT_TO_RIGHT" -> LEFT_TO_RIGHT
            "RIGHT_TO_LEFT" -> RIGHT_TO_LEFT
            "VERTICAL", "WEBTOON", "TOP_TO_BOTTOM" -> TOP_TO_BOTTOM
            else -> UNKNOWN
        }

        fun fromStoredValue(value: String?): AiSourceReadingDirection =
            entries.firstOrNull { it.storedValue == value } ?: UNKNOWN
    }
}

data class AiSeriesSourceLanguageState(
    val seriesId: String = "",
    val normalizedCode: String = "",
    val rawKomgaValue: String = "",
    val origin: AiSourceLanguageOrigin = AiSourceLanguageOrigin.AI_PENDING,
    val readingDirection: AiSourceReadingDirection = AiSourceReadingDirection.UNKNOWN,
    val evidence: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val sourceTextProfile: AiSourceTextProfile
        get() = when (normalizedCode.substringBefore('-')) {
            "ja" -> AiSourceTextProfile.JAPANESE_MANGA
            "" -> AiSourceTextProfile.AUTO
            else -> AiSourceTextProfile.HORIZONTAL_COMIC
        }

    fun detectionCacheKey(): String = listOf(
        normalizedCode,
        rawKomgaValue.trim().lowercase(Locale.ROOT),
        origin.storedValue,
        readingDirection.storedValue
    ).joinToString(":")

    fun recordAiEvidence(detectedSourceLanguage: String?): AiSeriesSourceLanguageState {
        if (origin != AiSourceLanguageOrigin.AI_PENDING || evidence.size >= AI_SOURCE_LANGUAGE_MAX_EVIDENCE) return this
        val normalized = normalizeAiSourceLanguageTag(detectedSourceLanguage)
        if (normalized.isBlank()) return this
        val updatedEvidence = evidence + normalized
        val confirmed = updatedEvidence
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value >= AI_SOURCE_LANGUAGE_CONFIRMATION_COUNT }
            ?.key
        return copy(
            normalizedCode = confirmed.orEmpty(),
            origin = if (confirmed == null) AiSourceLanguageOrigin.AI_PENDING else AiSourceLanguageOrigin.AI,
            evidence = updatedEvidence,
            updatedAt = System.currentTimeMillis()
        )
    }
}

fun resolveAiSourceLanguageFromKomga(
    seriesId: String,
    rawLanguage: String?,
    rawReadingDirection: String?,
    cachedState: AiSeriesSourceLanguageState?
): AiSeriesSourceLanguageState {
    val rawKomgaValue = rawLanguage.orEmpty().trim()
    val normalizedCode = normalizeAiSourceLanguageTag(rawKomgaValue)
    val readingDirection = AiSourceReadingDirection.fromKomgaValue(rawReadingDirection)
    if (normalizedCode.isNotBlank()) {
        return AiSeriesSourceLanguageState(
            seriesId = seriesId,
            normalizedCode = normalizedCode,
            rawKomgaValue = rawKomgaValue,
            origin = AiSourceLanguageOrigin.KOMGA,
            readingDirection = readingDirection
        )
    }
    val reusableAiState = cachedState?.takeIf {
        it.seriesId == seriesId &&
            it.origin == AiSourceLanguageOrigin.AI &&
            normalizeAiSourceLanguageTag(it.normalizedCode).isNotBlank()
    }
    if (reusableAiState != null && rawKomgaValue.isBlank()) {
        return reusableAiState.copy(
            rawKomgaValue = "",
            readingDirection = readingDirection,
            updatedAt = System.currentTimeMillis()
        )
    }
    val reusableEvidence = cachedState
        ?.takeIf {
            it.seriesId == seriesId &&
                it.origin == AiSourceLanguageOrigin.AI_PENDING &&
                it.rawKomgaValue == rawKomgaValue
        }
        ?.evidence
        .orEmpty()
        .take(AI_SOURCE_LANGUAGE_MAX_EVIDENCE)
    return AiSeriesSourceLanguageState(
        seriesId = seriesId,
        rawKomgaValue = rawKomgaValue,
        origin = AiSourceLanguageOrigin.AI_PENDING,
        readingDirection = readingDirection,
        evidence = reusableEvidence
    )
}

fun aiSourceLanguageOnMetadataFailure(
    seriesId: String,
    cachedState: AiSeriesSourceLanguageState?
): AiSeriesSourceLanguageState = cachedState
    ?.takeIf { it.seriesId == seriesId }
    ?: AiSeriesSourceLanguageState(seriesId = seriesId)

fun normalizeAiSourceLanguageTag(value: String?): String {
    val clean = value.orEmpty().trim().replace('_', '-')
    if (!AI_SOURCE_LANGUAGE_TAG_PATTERN.matches(clean)) return ""
    val parts = clean.split('-').toMutableList()
    val sourceLanguage = parts.first().lowercase(Locale.ROOT)
    val normalizedLanguage = AI_SOURCE_LANGUAGE_ALIASES[sourceLanguage] ?: sourceLanguage
    if (normalizedLanguage !in validAiSourceLanguageCodes) return ""
    parts[0] = normalizedLanguage
    val normalized = Locale.forLanguageTag(parts.joinToString("-")).toLanguageTag()
    return normalized.takeUnless { it.isBlank() || it.equals("und", ignoreCase = true) }.orEmpty()
}

private val validAiSourceLanguageCodes: Set<String> by lazy {
    val iso2 = Locale.getISOLanguages().map { it.lowercase(Locale.ROOT) }
    val iso3 = Locale.getAvailableLocales().mapNotNull { locale ->
        runCatching { locale.isO3Language.lowercase(Locale.ROOT) }.getOrNull()
    }
    (iso2 + iso3 + AI_SOURCE_LANGUAGE_ALIASES.keys).toSet()
}

private val AI_SOURCE_LANGUAGE_ALIASES = mapOf(
    "eng" to "en",
    "jpn" to "ja",
    "kor" to "ko",
    "zho" to "zh",
    "chi" to "zh",
    "tha" to "th"
)

private val AI_SOURCE_LANGUAGE_TAG_PATTERN = Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{1,8})*$")
private const val AI_SOURCE_LANGUAGE_MAX_EVIDENCE = 3
private const val AI_SOURCE_LANGUAGE_CONFIRMATION_COUNT = 2
