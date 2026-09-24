package fail.tiger.komgarot.data.local

/**
 * Selects the opt-in horizontal layout behavior for a confirmed source
 * language.  Legacy remains the default so existing books keep their layout.
 */
internal enum class AiHorizontalTranslationPolicy(val storedValue: String) {
    LEGACY("legacy"),
    ENGLISH_V1("horizontal-en-v1"),
    KOREAN_V1("horizontal-ko-v1")
}

internal fun resolveAiHorizontalTranslationPolicy(
    state: AiSeriesSourceLanguageState,
    effectiveProfile: AiSourceTextProfile = state.sourceTextProfile
): AiHorizontalTranslationPolicy {
    if (state.origin == AiSourceLanguageOrigin.AI_PENDING) {
        return AiHorizontalTranslationPolicy.LEGACY
    }
    return when {
        state.normalizedCode.substringBefore('-').equals("en", ignoreCase = true) &&
            effectiveProfile == AiSourceTextProfile.HORIZONTAL_COMIC ->
            AiHorizontalTranslationPolicy.ENGLISH_V1
        state.normalizedCode.substringBefore('-').equals("ko", ignoreCase = true) &&
            effectiveProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON ->
            AiHorizontalTranslationPolicy.KOREAN_V1
        else -> AiHorizontalTranslationPolicy.LEGACY
    }
}

internal fun resolveAiHorizontalTranslationPolicy(
    sourceLanguageTag: String,
    effectiveProfile: AiSourceTextProfile
): AiHorizontalTranslationPolicy {
    val normalizedCode = normalizeAiSourceLanguageTag(sourceLanguageTag)
    val origin = if (normalizedCode.isBlank()) {
        AiSourceLanguageOrigin.AI_PENDING
    } else {
        AiSourceLanguageOrigin.AI
    }
    return resolveAiHorizontalTranslationPolicy(
        state = AiSeriesSourceLanguageState(
            normalizedCode = normalizedCode,
            origin = origin
        ),
        effectiveProfile = effectiveProfile
    )
}

internal fun horizontalVersionedCacheKey(
    legacyKey: String,
    policy: AiHorizontalTranslationPolicy
): String = when (policy) {
    AiHorizontalTranslationPolicy.LEGACY -> legacyKey
    else -> "$legacyKey:${policy.storedValue}"
}
