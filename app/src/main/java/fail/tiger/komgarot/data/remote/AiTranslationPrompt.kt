package fail.tiger.komgarot.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
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

fun aiTranslationSystemPrompt(): String = """
You are a manga translation engine.
Each request translates exactly one current text-region crop image.
Page context images provide scene, speaker, tone, setting, and action context. Use page context for meaning.
The page context images are supporting context for the current crop.
The crop image is the only readable source text. Read the full crop as one coherent unit before translating.
Return only:
{"pageIndex":number,"translations":[{"sourceText":string|string[],"translatedLines":string[],"kind":"dialogue"|"narration"|"sign"|"SFX"}]}
Return no localRegionId, id, rect, coordinates, placement, color, or font data.
Classify the crop as dialogue, narration, sign, or SFX.
If the crop is dominated by a sound effect, return kind: "SFX".
SFX translatedLines must stay very short and contain only the sound-effect translation.
If the crop contains dialogue or sign text plus nearby sound effects, translate the main crop text and omit surrounding sound effects.
If the crop is decorative noise, return sourceText and translatedLines as an empty array.
If the crop is a pure number such as a page number, chapter number, score, price, or standalone numeric label, return sourceText and translatedLines as an empty array.
Translate dialogue balloons, narration boxes, signs, and important in-image text in a natural manga style.
Keep character voice, pauses, shouting, hesitation, short punchy lines, and comic timing.
Use the supplied targetLocale and targetLanguageName as the translation language.
translatedLines must be written in targetLanguageName.
Translate Japanese kana and source-language grammar into targetLanguageName.
Before returning JSON, verify every translatedLines entry uses targetLanguageName.
Preserve Japanese corner quotes 「」 and nested corner quotes 『』 when they express quoted speech, title text, emphasis, or source style.
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
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO
): String = buildString {
    appendLine("bookId: $bookId")
    appendLine("targetLocale: $targetLocale")
    appendLine("targetLanguageName: $targetLanguageName")
    appendLine("sourceMode: ${translationMode.storedValue}")
    appendSourceTextProfileIfNeeded(sourceTextProfile)
    if (localPageContexts.isNotEmpty()) {
        appendLine("currentRegion:")
        appendLine(localContextGson.toJson(localPageContexts.toPromptCurrentRegionJson(sourceTextProfile)))
        appendLine("Translate the current crop.")
        appendLine("Image 1: context")
        appendLine("Image 2: crop")
        appendLine("Use page context for scene, speaker, tone, and action.")
        appendLine("Read sourceText from the crop image.")
        if (sourceTextProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON) {
            appendLine("Korean horizontal webtoon source profile: read Korean text left-to-right within each line and top-to-bottom across lines.")
            appendLine("Preserve Korean spaces in sourceText, including spacing around names, particles, and short spoken fragments.")
        }
    }
    if (customInstructions.isNotBlank()) {
        appendLine("Additional user instructions:")
        appendLine(customInstructions)
    }
}

private fun StringBuilder.appendSourceTextProfileIfNeeded(sourceTextProfile: AiSourceTextProfile) {
    if (sourceTextProfile != AiSourceTextProfile.AUTO) {
        appendLine("sourceTextProfile: ${sourceTextProfile.storedValue}")
    }
}

private fun List<AiTranslationLocalPageContext>.toPromptCurrentRegionJson(
    sourceTextProfile: AiSourceTextProfile
): JsonObject = JsonObject().apply {
    val page = firstOrNull()
    val region = page?.regions?.firstOrNull()
    if (page != null) {
        addProperty("pageIndex", page.pageIndex)
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
