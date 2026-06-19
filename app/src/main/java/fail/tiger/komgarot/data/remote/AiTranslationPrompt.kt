package fail.tiger.komgarot.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
    val estimatedFontScale: Float
)

data class AiTranslationRegionLayoutHints(
    val estimatedFontPx: Int,
    val suggestedColumns: Int,
    val maxCharsPerColumn: Int,
    val suggestedLines: Int,
    val maxCharsPerLine: Int
)

fun aiTranslationSystemPrompt(): String = """
You are a manga translation engine. Use the supplied local text regions as anchors, page context images for scene understanding, and text-region crop images as the primary source for reading text. Return strict JSON only.
Translate dialogue balloons, narration boxes, sound effects, signs, and important in-image text in a natural manga style.
Keep character voice, pauses, shouting, hesitation, short punchy lines, and comic timing.
Translate sound effects as manga sound effects with impact. Preserve repeated sounds and visual rhythm when useful.
Preserve Japanese corner quotes 「」 and nested corner quotes 『』 when they express quoted speech, title text, emphasis, or source style.
Quote style is part of the translation contract: if the source crop or sourceText uses 「...」 or 『...』, translatedLines must use the same outer quote marks at the corresponding quoted spans.
For Chinese targets, Japanese manga quoted dialogue, title text, emphasis, and quoted narration use 「」 and 『』. Standard curly quotes “ ” are used only when the source crop itself uses “ ”.
Decorative text may be omitted.
If a crop is a pure number such as a page number, chapter number, score, price, or standalone numeric label, return sourceText and translatedLines as an empty array for that localRegionId so the app leaves the original image unchanged.
For each page, return pageIndex and translations.
Each translation must include localRegionId, sourceText, translatedLines, and kind.
Translate one local text region into one returned translation. Preserve the localRegionId exactly.
Each local text region is a merged text box or balloon crop. Read the whole crop as one coherent unit before translating.
Each local region has normalized rect coordinates in the page image and an imageRef matching a text-region crop attachment.
Read the matching text-region crop image and return sourceText in Japanese or the original source language.
Use the page context image to understand scene context, speaker intent, tone, sound effects, and ambiguous crop text.
Skip a local region when the matching crop image is unreadable or decorative noise.
Return translations only. The app owns placement, text direction, colors, and font size.
Handle line breaks in translatedLines for natural manga reading. Keep lines compact and avoid needless one-word lines.
Line breaks must preserve word and phrase cohesion: split at phrase, clause, or natural pause boundaries; keep names, compounds, idioms, particles, and tightly connected words together.
Punctuation in translatedLines follows visible source punctuation. Use sentence-final periods/full stops only when the source crop or sourceText visibly has a sentence-final mark. Short fragments with no visible sentence-final mark should end bare.
Punctuation attaches to the preceding word or phrase in translatedLines. A line should contain punctuation plus text, with punctuation at the end of the line or column.
For local text regions marked vertical, preserve vertical reading intent in the translated text and punctuation choice.
For Chinese targets, choose line breaks from the app-provided layoutHints. maxCharsPerColumn and maxCharsPerLine are local layout constraints computed from the detected text box size and estimated original font size.
Use suggestedColumns and suggestedLines as the preferred amount of visual wrapping. A connected phrase may exceed maxChars slightly, and a phrase that would overflow badly should move to the next translatedLines entry.
Avoid very short translated lines unless the source is a deliberate short beat, shout, name, or sound effect.
Use the supplied targetLocale and targetLanguageName. If targetLocale is a locale such as zh-CN, interpret it as the user's language and region preference.
Image ordering: each request contains page context image(s), then text-region crop image(s). Text metadata immediately before each image states imageRole, pageIndex, and localRegionId.
""".trimIndent()

fun aiTranslationUserPrompt(
    bookId: String,
    targetLocale: String,
    targetLanguageName: String,
    translationMode: AiTranslationMode,
    localPageContexts: List<AiTranslationLocalPageContext>,
    customInstructions: String
): String = buildString {
    appendLine("bookId: $bookId")
    appendLine("targetLocale: $targetLocale")
    appendLine("targetLanguageName: $targetLanguageName")
    appendLine("sourceMode: ${translationMode.storedValue}")
    if (localPageContexts.isNotEmpty()) {
        appendLine("localTextRegions:")
        appendLine(localContextGson.toJson(localPageContexts.toPromptPagesJson()))
        appendLine("Translate the supplied local text regions. Return localRegionId with corrected sourceText and translatedLines for each translated region.")
        appendLine("Each region is one merged text box or balloon crop. Read the full crop before translating so multiline Japanese stays coherent.")
        appendLine("Fields use stable names: pageIndex, imageWidth, imageHeight, regions, id, textDirection, rect, imageRef.")
        appendLine("Each region includes layoutHints computed by the app from the detected box and estimated original font size. Use maxCharsPerColumn for vertical regions and maxCharsPerLine for horizontal regions when choosing translatedLines breaks.")
        appendLine("Rect values are normalized page coordinates. imageRef values match text-region crop image metadata.")
        appendLine("The attached images are ordered: page context image first for each page, then text-region crop images in the same order as the region list.")
        appendLine("Read text from each text-region crop image. Return sourceText from the crop image.")
        appendLine("For a pure number region, return sourceText and translatedLines as an empty array; do not translate or rewrite digits.")
        appendLine("Choose translatedLines breaks at phrase or clause boundaries, keeping connected words together.")
        appendLine("Before returning JSON, check punctuation: source with no visible sentence-final mark returns no added period/full stop.")
        appendLine("Before returning JSON, check line breaks: no translatedLines entry should contain only punctuation.")
        appendLine("Before returning JSON, check translatedLines for quote style: source 「」/『』 stays as translated 「」/『』.")
        appendLine("Return translations only. Do not return placement, styling, or image-analysis data.")
    }
    appendLine("Return JSON matching the schema described in the system message.")
    if (customInstructions.isNotBlank()) {
        appendLine("Additional user instructions:")
        appendLine(customInstructions)
    }
}

private fun List<AiTranslationLocalPageContext>.toPromptPagesJson(): JsonArray = JsonArray().apply {
    this@toPromptPagesJson.forEach { page -> add(page.toPromptPageJson()) }
}

private fun AiTranslationLocalPageContext.toPromptPageJson(): JsonObject = JsonObject().apply {
    addProperty("pageIndex", pageIndex)
    addProperty("imageWidth", imageWidth)
    addProperty("imageHeight", imageHeight)
    add("regions", JsonArray().apply {
        regions
            .forEach { region -> add(region.toPromptRegionJson(imageWidth, imageHeight)) }
    })
}

private fun AiTranslationLocalTextRegion.toPromptRegionJson(
    pageImageWidth: Int,
    pageImageHeight: Int
): JsonObject = JsonObject().apply {
    addProperty("id", id)
    addProperty("textDirection", textDirection.toPromptValue())
    add("rect", rect.toPromptRectJson())
    addProperty("imageRef", "text-region:$id")
    add("layoutHints", aiTranslationRegionLayoutHints(pageImageWidth, pageImageHeight, this@toPromptRegionJson).toPromptJson())
}

fun aiTranslationRegionLayoutHints(
    pageImageWidth: Int,
    pageImageHeight: Int,
    region: AiTranslationLocalTextRegion
): AiTranslationRegionLayoutHints {
    val safeImageWidth = pageImageWidth.coerceAtLeast(1)
    val safeImageHeight = pageImageHeight.coerceAtLeast(1)
    val scale = region.estimatedFontScale.coerceIn(0.55f, 1.60f)
    return when (region.textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            val glyphWidthNormX = (0.045f * scale).coerceAtLeast(0.006f)
            val estimatedFontPx = (glyphWidthNormX * safeImageWidth).roundToInt().coerceAtLeast(8)
            val columnAdvanceNormX = glyphWidthNormX * 1.45f
            val glyphAdvanceNormY = glyphWidthNormX * safeImageWidth / safeImageHeight * 1.05f
            AiTranslationRegionLayoutHints(
                estimatedFontPx = estimatedFontPx,
                suggestedColumns = (region.rect.width / columnAdvanceNormX).roundToInt().coerceIn(1, 8),
                maxCharsPerColumn = floor(region.rect.height / glyphAdvanceNormY).toInt().coerceIn(2, 40),
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

private fun AiTranslationRegionLayoutHints.toPromptJson(): JsonObject = JsonObject().apply {
    addProperty("estimatedFontPx", estimatedFontPx)
    addProperty("suggestedColumns", suggestedColumns)
    addProperty("maxCharsPerColumn", maxCharsPerColumn)
    addProperty("suggestedLines", suggestedLines)
    addProperty("maxCharsPerLine", maxCharsPerLine)
}

private fun AiTranslationTextDirection.toPromptValue(): String = when (this) {
    AiTranslationTextDirection.VERTICAL -> "vertical"
    AiTranslationTextDirection.HORIZONTAL -> "horizontal"
    AiTranslationTextDirection.AUTO -> "auto"
}

private fun AiTranslationRect.toPromptRectJson(): JsonObject = JsonObject().apply {
    addProperty("x", x)
    addProperty("y", y)
    addProperty("width", width)
    addProperty("height", height)
}

private val localContextGson = GsonBuilder().disableHtmlEscaping().create()

fun appendImageUrlExtraQuery(url: String, extraQuery: String): String {
    val clean = extraQuery.trim().trimStart('?').trimStart('&')
    if (clean.isBlank()) return url
    val separator = if (url.contains("?")) "&" else "?"
    return "$url$separator$clean"
}
