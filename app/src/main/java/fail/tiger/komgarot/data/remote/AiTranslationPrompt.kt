package fail.tiger.komgarot.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fail.tiger.komgarot.data.local.AiSeriesSourceLanguageState
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiSourceLanguageOrigin
import fail.tiger.komgarot.data.local.AiSourceReadingDirection
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiGlossaryEntry
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

data class AiTranslationLocalPageContext(
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val regions: List<AiTranslationLocalTextRegion>
)

data class AiTranslationLocalTextRegion(
    val id: String,
    val rect: AiTranslationRect,
    val textDirection: AiTranslationTextDirection,
    val textColor: String,
    val backgroundColor: String,
    val confidence: Float,
    val estimatedFontScale: Float,
    val rotationDegrees: Float = 0f,
    val textBounds: AiTranslationRect = AiTranslationRect(),
    val renderBounds: AiTranslationRect = AiTranslationRect(),
    val aiCropBounds: AiTranslationRect = AiTranslationRect(),
    val sourceColumns: List<AiTranslationRect> = emptyList()
)

data class AiTranslationRegionLayoutHints(
    val estimatedFontPx: Int,
    val suggestedColumns: Int,
    val maxCharsPerColumn: Int,
    val suggestedLines: Int,
    val maxCharsPerLine: Int
)

fun aiTranslationSystemPrompt(skipSoundEffects: Boolean = false): String = """
You are a manga translation engine.
Each request translates the current text-region crop images in their supplied order.
Page context images provide scene, speaker, tone, setting, and action context. Use page context for meaning.
The page context images are supporting context for the current crop.
The crop image is the only readable source text. Read the full crop as one coherent unit before translating.
Return only:
{"pageIndex":number,"translations":[{"regionOrdinal":number,"sourceText":string|string[],"translatedLines":string[],"kind":"dialogue"|"narration"|"sign"|"SFX","status":"translated"|"skipped_sfx"|"skipped_non_text","detectedSourceLanguage":string}]}
Return regionOrdinal for every crop. Coordinates, placement, mask, color, and font data are owned by the app and stay out of the response.
Return one translation item for every crop, including skipped crops.
Classify the crop as dialogue, narration, sign, or SFX.
${if (skipSoundEffects) {
    """Short calls, interjections, greetings, and spoken fragments remain dialogue even when they are visually isolated or contain only a few characters.
If the crop is a visual sound effect or onomatopoeia, return kind: "SFX", status: "skipped_sfx", sourceText, and translatedLines: []."""
} else {
    """If the crop is dominated by a sound effect, return kind: "SFX".
SFX translatedLines must stay very short and contain only the sound-effect translation."""
}}
If the crop contains dialogue or sign text plus nearby sound effects, translate the main crop text and omit surrounding sound effects.
If the crop is decorative noise, return status: "skipped_non_text", sourceText, and translatedLines: [].
If the crop is a pure number such as a page number, chapter number, score, price, or standalone numeric label, return status: "skipped_non_text", sourceText, and translatedLines: [].
Translate dialogue balloons, narration boxes, signs, and important in-image text in a natural manga style.
Keep character voice, pauses, shouting, hesitation, short punchy lines, and comic timing.
Use the supplied targetLocale and targetLanguageName as the translation language.
translatedLines must be written in targetLanguageName.
Before returning JSON, verify every translatedLines entry uses targetLanguageName.
When detectSourceLanguage is true, return detectedSourceLanguage as a valid IETF BCP 47 language tag for the crop text.
Punctuation in translatedLines follows visible source punctuation.
A single visible source ellipsis character … maps to one translated ellipsis …; repeated visible source ellipses keep the same count.
Short fragments with no visible sentence-final mark should end bare.
Punctuation attaches to the preceding word or phrase.
Line breaks must preserve word and phrase cohesion.
Use layoutHints for visual wrapping: maxCharsPerColumn for vertical text and maxCharsPerLine for horizontal text.
Prefer suggestedColumns and suggestedLines, while keeping connected phrases together.
""".trimIndent()

fun aiTranslationUserPrompt(
    bookId: String,
    targetLocale: String,
    targetLanguageName: String,
    translationMode: AiTranslationMode,
    localPageContexts: List<AiTranslationLocalPageContext>,
    customInstructions: String,
    skipSoundEffects: Boolean = false,
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO,
    sourceLanguage: AiSeriesSourceLanguageState? = null,
    glossary: List<AiGlossaryEntry> = emptyList()
): String = buildString {
    appendLine("bookId: $bookId")
    appendLine("targetLocale: $targetLocale")
    appendLine("targetLanguageName: $targetLanguageName")
    appendLine("sourceMode: ${translationMode.storedValue}")
    val effectiveSourceTextProfile = sourceLanguage?.sourceTextProfile
        ?.takeUnless { it == AiSourceTextProfile.AUTO }
        ?: sourceTextProfile
    appendSourceLanguageIfPresent(sourceLanguage)
    appendSourceTextProfileIfNeeded(effectiveSourceTextProfile)
    if (localPageContexts.isNotEmpty()) {
        appendLine("currentRegion:")
        appendLine(localContextGson.toJson(localPageContexts.toPromptCurrentRegionJson(effectiveSourceTextProfile)))
        appendLine("Translate the current crop.")
        appendLine("Image 1: context")
        appendLine("Images 2-${localPageContexts.first().regions.size + 1}: crop images in regionOrdinal order")
        appendLine("Use page context for scene, speaker, tone, and action.")
        appendLine("Read sourceText from the crop image.")
        appendLine("Use regionOrdinal to associate each response item with the corresponding crop. Keep all regionOrdinal values in request order.")
        if (effectiveSourceTextProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON) {
            appendLine("Korean horizontal webtoon source profile: read Hangul text left-to-right within each line and top-to-bottom across lines.")
            appendLine("Keep each Hangul syllable block intact and preserve Korean word spaces, particles, endings, names, honorifics, mixed Hanja, Latin text, and digits in sourceText.")
            appendLine("Use the complete crop to recover small Hangul strokes and distinguish adjacent syllable blocks before translating.")
        }
    }
    if (skipSoundEffects) {
        appendLine("skipSoundEffects: true")
        appendLine("Skip visual SFX and onomatopoeia while retaining one skipped_sfx response item for each crop.")
    }
    glossary.filter { it.source.isNotBlank() && it.target.isNotBlank() }
        .take(32)
        .takeIf { it.isNotEmpty() }
        ?.let { entries ->
            appendLine("Glossary:")
            entries.forEach { entry ->
                appendLine("- ${entry.source} => ${entry.target}${entry.note.takeIf { it.isNotBlank() }?.let { note -> " ($note)" }.orEmpty()}")
            }
            appendLine("Keep glossary source terms and targets consistent across the current page.")
        }
    if (customInstructions.isNotBlank()) {
        appendLine("Additional user instructions:")
        appendLine(customInstructions)
    }
}

private fun StringBuilder.appendSourceLanguageIfPresent(sourceLanguage: AiSeriesSourceLanguageState?) {
    if (sourceLanguage == null) return
    appendLine("sourceLanguageOrigin: ${sourceLanguage.origin.storedValue}")
    if (sourceLanguage.normalizedCode.isBlank()) {
        appendLine("sourceLanguage: auto")
        appendLine("detectSourceLanguage: true")
        appendLine("Detect the crop source language and return detectedSourceLanguage as an IETF BCP 47 tag.")
    } else {
        appendLine("sourceLanguage: ${sourceLanguage.normalizedCode}")
        appendLine("sourceLanguageName: ${sourceLanguage.normalizedCode.sourceLanguageName()}")
        appendLine("detectSourceLanguage: false")
        appendKnownSourceLanguageInstructions(sourceLanguage.normalizedCode)
    }
    if (sourceLanguage.readingDirection != AiSourceReadingDirection.UNKNOWN) {
        appendLine("sourceReadingDirection: ${sourceLanguage.readingDirection.storedValue}")
    }
    if (sourceLanguage.origin == AiSourceLanguageOrigin.KOMGA && sourceLanguage.rawKomgaValue.isNotBlank()) {
        appendLine("komgaSourceLanguage: ${sourceLanguage.rawKomgaValue}")
    }
}

private fun String.sourceLanguageName(): String =
    Locale.forLanguageTag(this)
        .getDisplayLanguage(Locale.ENGLISH)
        .takeIf { it.isNotBlank() }
        ?: this

private fun StringBuilder.appendKnownSourceLanguageInstructions(normalizedCode: String) {
    when (normalizedCode.substringBefore('-')) {
        "ja" -> {
            appendLine("Japanese source: read kana, kanji, visible punctuation, and vertical or horizontal text as shown.")
            appendLine("Preserve Japanese corner quotes 「」 and nested corner quotes 『』 when they carry source style or emphasis.")
        }
        "ko" -> {
            appendLine("Korean source: read Hangul syllable blocks left-to-right within each line and lines top-to-bottom.")
            appendLine("Preserve Korean word spaces, particles, endings, names, honorifics, mixed Hanja, Latin text, digits, and short spoken fragments in sourceText.")
        }
        "en" -> appendLine("English source: preserve capitalization, contractions, slang, emphasis, and short comic timing.")
        "zh" -> appendLine("Chinese source: preserve the visible script, names, punctuation, and concise comic phrasing.")
        "th" -> appendLine("Thai source: preserve word grouping, names, particles, tone, and visible punctuation.")
        else -> appendLine("Read the source text according to the supplied BCP 47 language tag.")
    }
}

private fun StringBuilder.appendSourceTextProfileIfNeeded(sourceTextProfile: AiSourceTextProfile) {
    if (sourceTextProfile != AiSourceTextProfile.AUTO) {
        appendLine("sourceTextProfile: ${sourceTextProfile.storedValue}")
        when (sourceTextProfile) {
            AiSourceTextProfile.JAPANESE_MANGA -> {
                appendLine("Japanese manga layout: preserve vertical columns from right to left, vertical punctuation, and short horizontal fragments inside vertical text.")
            }
            AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON -> {
                appendLine("Korean webtoon layout: keep horizontal lines top-to-bottom and size each response for its supplied render bounds.")
            }
            AiSourceTextProfile.HORIZONTAL_COMIC -> {
                appendLine("Horizontal comic layout: wrap on whole English words when the source is English and keep dialogue within the supplied render bounds.")
            }
            AiSourceTextProfile.AUTO -> Unit
        }
    }
}

private fun List<AiTranslationLocalPageContext>.toPromptCurrentRegionJson(
    sourceTextProfile: AiSourceTextProfile
): JsonObject = JsonObject().apply {
    val page = firstOrNull()
    val region = page?.regions?.firstOrNull()
    if (page != null) {
        addProperty("pageIndex", page.pageIndex)
        addProperty("regionCount", page.regions.size)
        add("regions", JsonArray().apply {
            page.regions.forEachIndexed { index, region ->
                add(JsonObject().apply {
                    addProperty("regionOrdinal", index)
                    addProperty("textDirection", region.textDirection.toPromptValue())
                    add("layoutHints", aiTranslationRegionLayoutHints(page.imageWidth, page.imageHeight, region).toCompactPromptJson(region.textDirection))
                })
            }
        })
    }
    if (sourceTextProfile != AiSourceTextProfile.AUTO) {
        addProperty("sourceTextProfile", sourceTextProfile.storedValue)
    }
    if (page != null && region != null) {
        addProperty("textDirection", region.textDirection.toPromptValue())
        add(
            "layoutHints",
            aiTranslationRegionLayoutHints(page.imageWidth, page.imageHeight, region)
                .toCompactPromptJson(region.textDirection)
        )
    }
}

fun aiTranslationRegionLayoutHints(
    pageImageWidth: Int,
    pageImageHeight: Int,
    region: AiTranslationLocalTextRegion
): AiTranslationRegionLayoutHints {
    val safeImageWidth = pageImageWidth.coerceAtLeast(1)
    val safeImageHeight = pageImageHeight.coerceAtLeast(1)
    val scale = region.estimatedFontScale.coerceIn(0.55f, 1.60f)
    val columns = region.sourceColumns.filter { it.width > 0f && it.height > 0f }
    return when (region.textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            val detectedColumnWidthNormX = columns
                .map { it.width }
                .sorted()
                .takeIf { it.isNotEmpty() }
                ?.let { it[it.size / 2] }
            val glyphWidthNormX = detectedColumnWidthNormX
                ?.let { it * 0.82f }
                ?: (0.045f * scale).coerceAtLeast(0.006f)
            val columnWidthNormX = detectedColumnWidthNormX ?: glyphWidthNormX
            val estimatedFontPx = (glyphWidthNormX * safeImageWidth).roundToInt().coerceAtLeast(8)
            val columnAdvanceNormX = columnWidthNormX * 1.45f
            val glyphAdvanceNormY = columnWidthNormX * safeImageWidth / safeImageHeight * 1.05f
            val heightNorm = columns
                .takeIf { it.isNotEmpty() }
                ?.maxOf { it.height }
                ?: region.rect.height
            AiTranslationRegionLayoutHints(
                estimatedFontPx = estimatedFontPx,
                suggestedColumns = columns.size.coerceIn(1, 8).takeIf { columns.isNotEmpty() }
                    ?: (region.rect.width / columnAdvanceNormX).roundToInt().coerceIn(1, 8),
                maxCharsPerColumn = floor(heightNorm / glyphAdvanceNormY).toInt().coerceIn(2, 40),
                suggestedLines = 1,
                maxCharsPerLine = 0
            )
        }
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> {
            val glyphHeightNormY = (0.055f * scale).coerceAtLeast(0.006f)
            val estimatedFontPx = (glyphHeightNormY * safeImageHeight).roundToInt().coerceAtLeast(8)
            val lineAdvanceNormY = glyphHeightNormY * 1.25f
            val charAdvanceNormX = estimatedFontPx * 0.58f / safeImageWidth
            AiTranslationRegionLayoutHints(
                estimatedFontPx = estimatedFontPx,
                suggestedColumns = 1,
                maxCharsPerColumn = 0,
                suggestedLines = (region.rect.height / lineAdvanceNormY).roundToInt().coerceIn(1, 8),
                maxCharsPerLine = floor(region.rect.width / charAdvanceNormX).toInt().coerceIn(4, 80)
            )
        }
    }
}

private fun AiTranslationRegionLayoutHints.toCompactPromptJson(
    textDirection: AiTranslationTextDirection
): JsonObject = JsonObject().apply {
    when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            addProperty("suggestedColumns", suggestedColumns)
            addProperty("maxCharsPerColumn", maxCharsPerColumn)
        }
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> {
            addProperty("suggestedLines", suggestedLines)
            addProperty("maxCharsPerLine", maxCharsPerLine)
        }
    }
}

private fun AiTranslationTextDirection.toPromptValue(): String = when (this) {
    AiTranslationTextDirection.VERTICAL -> "vertical"
    AiTranslationTextDirection.HORIZONTAL -> "horizontal"
    AiTranslationTextDirection.AUTO -> "auto"
}

private val localContextGson = GsonBuilder().disableHtmlEscaping().create()

fun appendImageUrlExtraQuery(url: String, extraQuery: String): String {
    val clean = extraQuery.trim().trimStart('?').trimStart('&')
    if (clean.isBlank()) return url
    val separator = if (url.contains("?")) "&" else "?"
    return "$url$separator$clean"
}
