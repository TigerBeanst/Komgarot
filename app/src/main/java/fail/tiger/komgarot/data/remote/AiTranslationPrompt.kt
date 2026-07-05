package fail.tiger.komgarot.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
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
You are a manga translation engine. Use the supplied local text regions as anchors, page context images for scene understanding, and text-region crop images as the primary source for reading text. Return strict JSON only.
Translate dialogue balloons, narration boxes, sound effects, signs, and important in-image text in a natural manga style.
Keep character voice, pauses, shouting, hesitation, short punchy lines, and comic timing.
Translate sound effects as manga sound effects with impact. Preserve repeated sounds and visual rhythm when useful.
Sound effects must return kind: "SFX" and use short translatedLines suitable for small overlay text.
Classify the current crop itself as dialogue, narration, sign, or SFX before translating.
If the current crop itself is a sound effect, return kind: "SFX" even when page context contains dialogue nearby.
SFX translatedLines must stay very short and contain only the sound-effect translation.
If a crop contains dialogue plus surrounding sound effects, translate the anchored main dialogue or sign text and omit surrounding sound effects from translatedLines unless the crop itself is dominated by sound-effect text.
Preserve Japanese corner quotes 「」 and nested corner quotes 『』 when they express quoted speech, title text, emphasis, or source style.
Quote style is part of the translation contract: if the source crop or sourceText uses 「...」 or 『...』, translatedLines must use the same outer quote marks at the corresponding quoted spans.
For Chinese targets, Japanese manga quoted dialogue, title text, emphasis, and quoted narration use 「」 and 『』. Standard curly quotes “ ” are used only when the source crop itself uses “ ”.
Decorative text may be omitted.
If the crop is a pure number such as a page number, chapter number, score, price, or standalone numeric label, return sourceText and translatedLines as an empty array so the app leaves the original image unchanged.
For each page, return pageIndex and translations.
Each translation must include sourceText, translatedLines, and kind.
Translate the current local text region into one returned translation.
Each request contains one local text region crop: a text box, balloon crop, sign, or sound effect crop. Read the whole crop as one coherent unit before translating.
The app binds this response to the requested local region.
The translations array must contain exactly one object for the current crop.
Each local region has normalized rect coordinates in the page image.
Read the current text-region crop image and return sourceText in Japanese or the original source language.
Use the page context image to understand scene context, speaker intent, tone, sound effects, and ambiguous crop text.
Page context images are scene context only. Do not read, translate, or infer sourceText from page context images.
The readable text source is the current text-region crop image.
Skip a local region when the matching crop image is unreadable or decorative noise.
Return translations only. The app owns placement, text direction, colors, and font size.
Handle line breaks in translatedLines for natural manga reading. Keep lines compact and avoid needless one-word lines.
Line breaks must preserve word and phrase cohesion: split at phrase, clause, or natural pause boundaries; keep names, compounds, idioms, particles, and tightly connected words together.
Punctuation in translatedLines follows visible source punctuation. Use sentence-final periods/full stops only when the source crop or sourceText visibly has a sentence-final mark. Short fragments with no visible sentence-final mark should end bare.
A single visible source ellipsis character … maps to one translated ellipsis …; repeated visible source ellipses keep the same count.
Punctuation attaches to the preceding word or phrase in translatedLines. A line should contain punctuation plus text, with punctuation at the end of the line or column.
For local text regions marked vertical, preserve vertical reading intent in the translated text and punctuation choice.
For Chinese targets, choose line breaks from the app-provided layoutHints. maxCharsPerColumn and maxCharsPerLine are local layout constraints computed from the detected text box size and estimated original font size.
Use suggestedColumns and suggestedLines as the preferred amount of visual wrapping. A connected phrase may exceed maxChars slightly, and a phrase that would overflow badly should move to the next translatedLines entry.
Avoid very short translated lines unless the source is a deliberate short beat, shout, name, or sound effect.
Use the supplied targetLocale and targetLanguageName. If targetLocale is a locale such as zh-CN, interpret it as the user's language and region preference.
Image ordering: each request contains page context image(s), then the current text-region crop image. Text metadata immediately before each image states imageRole and pageIndex.
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
    appendLine("sourceTextProfile: ${sourceTextProfile.storedValue}")
    if (localPageContexts.isNotEmpty()) {
        appendLine("localTextRegions:")
        appendLine(localContextGson.toJson(localPageContexts.toPromptPagesJson()))
        appendLine("Translate the current local text region. Return sourceText and translatedLines for this region.")
        appendLine("The app binds this response to the requested local region.")
        appendLine("The translations array must contain exactly one object for the current crop.")
        appendLine("The current region is one independent text box, balloon crop, sign, or sound effect. Read the full crop before translating so multiline text stays coherent.")
        if (sourceTextProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON) {
            appendLine("Korean horizontal webtoon source profile: read Korean text left-to-right within each line and top-to-bottom across lines.")
            appendLine("Preserve Korean spaces in sourceText, including spacing around names, particles, and short spoken fragments.")
        }
        appendLine("Fields use stable names: pageIndex, imageWidth, imageHeight, regions, textDirection, rect.")
        appendLine("Each region includes layoutHints computed by the app from the detected box and estimated original font size. Use maxCharsPerColumn for vertical regions and maxCharsPerLine for horizontal regions when choosing translatedLines breaks.")
        appendLine("Rect values are normalized page coordinates.")
        appendLine("The attached images are ordered: page context image first for each page, then the current text-region crop image.")
        appendLine("Page context images are for scene context only. Use them for speaker, tone, and scene understanding.")
        appendLine("Read sourceText from the current text-region crop image. Return sourceText from the crop image.")
        appendLine("For a pure number region, return sourceText and translatedLines as an empty array.")
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
    addProperty("textDirection", textDirection.toPromptValue())
    add("rect", rect.toPromptRectJson())
    val columns = sourceColumns.filter { it.width > 0f && it.height > 0f }
    if (columns.isNotEmpty()) {
        add("sourceColumns", JsonArray().apply {
            columns.forEach { column -> add(column.toPromptRectJson()) }
        })
    }
    if (rotationDegrees != 0f) {
        addProperty("rotationDegrees", rotationDegrees)
    }
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
