package fail.tiger.komgarot.data.local

data class AiTranslatedBook(
    val schemaVersion: Int = 1,
    val bookId: String = "",
    val seriesId: String = "",
    val title: String = "",
    val seriesTitle: String = "",
    val pageCount: Int = 0,
    val fileFingerprint: AiBookFileFingerprint = AiBookFileFingerprint(),
    val translation: AiBookTranslationMetadata = AiBookTranslationMetadata(),
    val glossary: List<AiGlossaryEntry> = emptyList(),
    val pages: List<AiTranslatedPage> = emptyList()
)

data class AiBookFileFingerprint(
    val mediaType: String = "",
    val sizeBytes: Long = 0
)

data class AiBookTranslationMetadata(
    val targetLocale: String = "",
    val targetLanguageName: String = "",
    val provider: String = "openai-compatible",
    val model: String = "",
    val mode: String = AiTranslationMode.LOCAL_DETECTION.storedValue,
    val sourceTextProfile: String = AiSourceTextProfile.AUTO.storedValue,
    val sourceLanguageCode: String = "",
    val sourceLanguageOrigin: String = AiSourceLanguageOrigin.AI_PENDING.storedValue,
    val sourceKomgaLanguage: String = "",
    val sourceReadingDirection: String = AiSourceReadingDirection.UNKNOWN.storedValue,
    val modePinned: Boolean = false
)

data class AiGlossaryEntry(
    val source: String = "",
    val target: String = "",
    val note: String = ""
)

data class AiTranslatedPage(
    val pageIndex: Int = 0,
    val status: AiTranslationPageStatus = AiTranslationPageStatus.PENDING,
    val retryCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val blocks: List<AiTranslationBlock> = emptyList(),
    val errorSummary: String = "",
    val errorCategory: String = "",
    val errorHttpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    val mode: String = AiTranslationMode.LOCAL_DETECTION.storedValue
)

enum class AiTranslationPageStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}

enum class AiTranslationFailureCategory(val storedValue: String) {
    SETTINGS("settings"),
    MODEL_CONFIGURATION("model_configuration"),
    PAGE_LIST("page_list"),
    IMAGE_INPUT("image_input"),
    LOCAL_TEXT_EMPTY("local_text_empty"),
    REGION_CROP("region_crop"),
    NETWORK_OR_API("network_or_api"),
    VISION_UNSUPPORTED("vision_unsupported"),
    NON_JSON_RESPONSE("non_json_response"),
    JSON_VALIDATION_FAILED("json_validation_failed"),
    EMPTY_AI_RESULT("empty_ai_result"),
    SAVE_VERIFICATION("save_verification"),
    UNKNOWN("unknown");

    companion object {
        fun fromStoredValue(value: String): AiTranslationFailureCategory =
            entries.firstOrNull { it.storedValue == value } ?: UNKNOWN
    }
}

enum class AiTranslationBlockKind {
    DIALOGUE,
    NARRATION,
    SFX,
    SIGN,
    OTHER
}

enum class AiTranslationRegionStatus {
    PENDING,
    RUNNING,
    DONE,
    SKIPPED,
    FAILED
}

internal fun AiTranslationRegionStatus.isTerminal(): Boolean =
    this == AiTranslationRegionStatus.DONE || this == AiTranslationRegionStatus.SKIPPED

enum class AiTranslationTextDirection {
    AUTO,
    HORIZONTAL,
    VERTICAL
}

data class AiTranslationBlock(
    val localRegionId: String = "",
    val regionStatus: AiTranslationRegionStatus = AiTranslationRegionStatus.DONE,
    val kind: AiTranslationBlockKind = AiTranslationBlockKind.OTHER,
    val sourceText: String = "",
    val translatedLines: List<String> = emptyList(),
    val rect: AiTranslationRect = AiTranslationRect(),
    val translationRect: AiTranslationRect = AiTranslationRect(),
    val sourceColumns: List<AiTranslationRect> = emptyList(),
    val textColor: String = "#111111",
    val maskColor: String = "#FFFFFF",
    val maskAlpha: Float = 0.72f,
    val cornerRadius: Float = 0.04f,
    val rotationDegrees: Float = 0f,
    val fontScale: Float = 1.0f,
    val confidence: Float = 0f,
    val textDirection: AiTranslationTextDirection = AiTranslationTextDirection.AUTO
) {
    fun renderSafe(): AiTranslationBlock = copy(
        rect = rect.renderSafe(),
        translationRect = translationRect.takeIf { it != AiTranslationRect() }?.clampSafe() ?: AiTranslationRect(),
        sourceColumns = sourceColumns.mapNotNull { it.sourceColumnSafeOrNull() }.take(24),
        maskAlpha = maskAlpha.coerceIn(0.78f, 0.88f),
        cornerRadius = cornerRadius.coerceIn(0f, 0.12f),
        rotationDegrees = 0f,
        fontScale = fontScale.coerceIn(0.6f, 1.4f),
        confidence = confidence.coerceIn(0f, 1f)
    )
}

internal fun List<AiTranslationBlock>.suppressDuplicateRenderedTranslations(): List<AiTranslationBlock> {
    if (size < 2) return this
    val result = mutableListOf<AiTranslationBlock>()
    forEach { block ->
        val duplicateIndex = result.indexOfFirst { existing ->
            existing.hasEquivalentTranslationNear(block)
        }
        if (duplicateIndex < 0) {
            result += block
        } else {
            result[duplicateIndex] = result[duplicateIndex].mergeDuplicateTranslationMask(block)
            result += block.copy(translatedLines = emptyList())
        }
    }
    return result
}

private fun AiTranslationBlock.hasEquivalentTranslationNear(other: AiTranslationBlock): Boolean {
    if (regionStatus != AiTranslationRegionStatus.DONE || other.regionStatus != AiTranslationRegionStatus.DONE) {
        return false
    }
    val translated = translatedLines.normalizedTranslationText()
    val otherTranslated = other.translatedLines.normalizedTranslationText()
    if (translated.isEmpty() || otherTranslated.isEmpty()) return false
    val source = sourceText.normalizedTranslationText()
    val otherSource = other.sourceText.normalizedTranslationText()
    val equivalentText = translated.isEquivalentTranslationText(otherTranslated) ||
        source.isNotEmpty() && otherSource.isNotEmpty() && source.isEquivalentTranslationText(otherSource)
    if (!equivalentText) return false
    val sourceOverlap = rect.overlapRatioAgainstSmaller(other.rect)
    val renderOverlap = effectiveTranslationRect().overlapRatioAgainstSmaller(other.effectiveTranslationRect())
    return sourceOverlap >= 0.55f || renderOverlap >= 0.65f
}

private fun AiTranslationBlock.mergeDuplicateTranslationMask(other: AiTranslationBlock): AiTranslationBlock {
    val mergedSourceColumns = (
        sourceColumns.ifEmpty { listOf(rect) } +
            other.sourceColumns.ifEmpty { listOf(other.rect) }
        ).distinct()
    val preferredLines = listOf(translatedLines, other.translatedLines)
        .maxBy { it.normalizedTranslationText().length }
    return copy(
        kind = if (kind == AiTranslationBlockKind.OTHER) other.kind else kind,
        sourceText = maxOf(sourceText, other.sourceText, compareBy(String::length)),
        translatedLines = preferredLines,
        rect = rect.union(other.rect),
        translationRect = effectiveTranslationRect().union(other.effectiveTranslationRect()),
        sourceColumns = mergedSourceColumns,
        confidence = maxOf(confidence, other.confidence)
    )
}

private fun AiTranslationBlock.effectiveTranslationRect(): AiTranslationRect =
    translationRect.takeIf { it.width > 0f && it.height > 0f } ?: rect

private fun List<String>.normalizedTranslationText(): String = joinToString("").normalizedTranslationText()

private fun String.normalizedTranslationText(): String = buildString(length) {
    this@normalizedTranslationText.forEach { character ->
        if (character.isLetterOrDigit()) append(character.lowercaseChar())
    }
}

private fun String.isEquivalentTranslationText(other: String): Boolean {
    if (this == other) return true
    val shorter = minOf(length, other.length)
    val longer = maxOf(length, other.length)
    return shorter >= 6 && shorter.toFloat() / longer >= 0.78f && (contains(other) || other.contains(this))
}

private fun AiTranslationRect.overlapRatioAgainstSmaller(other: AiTranslationRect): Float {
    if (width <= 0f || height <= 0f || other.width <= 0f || other.height <= 0f) return 0f
    val overlapWidth = (minOf(x + width, other.x + other.width) - maxOf(x, other.x)).coerceAtLeast(0f)
    val overlapHeight = (minOf(y + height, other.y + other.height) - maxOf(y, other.y)).coerceAtLeast(0f)
    val smallerArea = minOf(width * height, other.width * other.height).coerceAtLeast(0.000001f)
    return overlapWidth * overlapHeight / smallerArea
}

private fun AiTranslationRect.union(other: AiTranslationRect): AiTranslationRect {
    val left = minOf(x, other.x)
    val top = minOf(y, other.y)
    val right = maxOf(x + width, other.x + other.width)
    val bottom = maxOf(y + height, other.y + other.height)
    return AiTranslationRect(left, top, right - left, bottom - top)
}

internal fun AiTranslatedPage.pausedForRegionResume(): AiTranslatedPage {
    val pausedBlocks = blocks.map { block ->
        if (block.regionStatus == AiTranslationRegionStatus.RUNNING) {
            block.copy(regionStatus = AiTranslationRegionStatus.PENDING)
        } else {
            block
        }
    }
    val allDone = pausedBlocks.isNotEmpty() && pausedBlocks.all { it.regionStatus.isTerminal() }
    return copy(
        status = if (allDone) AiTranslationPageStatus.DONE else AiTranslationPageStatus.PENDING,
        blocks = pausedBlocks,
        errorSummary = "",
        errorCategory = "",
        errorHttpStatus = null,
        retryAfterMs = null,
        updatedAt = System.currentTimeMillis()
    )
}

internal fun AiTranslatedPage.runningForRegionResume(mode: AiTranslationMode): AiTranslatedPage =
    copy(
        status = AiTranslationPageStatus.RUNNING,
        blocks = blocks.map { block ->
            if (block.regionStatus.isTerminal()) {
                block
            } else {
                block.copy(regionStatus = AiTranslationRegionStatus.PENDING)
            }
        },
        errorSummary = "",
        errorCategory = "",
        errorHttpStatus = null,
        retryAfterMs = null,
        mode = mode.storedValue,
        updatedAt = System.currentTimeMillis()
    )

data class AiTranslationRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

private const val MIN_RENDER_RECT_SIZE = 0.004f
private const val MAX_RENDER_RECT_SIZE = 0.9f

private fun AiTranslationRect.renderSafe(): AiTranslationRect {
    val safeX = x.coerceIn(0f, 0.97f)
    val safeY = y.coerceIn(0f, 0.97f)
    val safeWidth = width.coerceIn(MIN_RENDER_RECT_SIZE, MAX_RENDER_RECT_SIZE).coerceAtMost(1f - safeX)
    val safeHeight = height.coerceIn(MIN_RENDER_RECT_SIZE, MAX_RENDER_RECT_SIZE).coerceAtMost(1f - safeY)
    return copy(
        x = safeX,
        y = safeY,
        width = safeWidth,
        height = safeHeight
    )
}

private fun AiTranslationRect.clampSafe(): AiTranslationRect {
    val safeX = x.coerceIn(0f, 0.97f)
    val safeY = y.coerceIn(0f, 0.97f)
    return copy(
        x = safeX,
        y = safeY,
        width = width.coerceIn(MIN_RENDER_RECT_SIZE, MAX_RENDER_RECT_SIZE).coerceAtMost(1f - safeX),
        height = height.coerceIn(MIN_RENDER_RECT_SIZE, MAX_RENDER_RECT_SIZE).coerceAtMost(1f - safeY)
    )
}

private fun AiTranslationRect.sourceColumnSafeOrNull(): AiTranslationRect? {
    if (width <= 0f || height <= 0f) return null
    val safeX = x.coerceIn(0f, 0.99f)
    val safeY = y.coerceIn(0f, 0.99f)
    val safeWidth = width.coerceAtMost(1f - safeX)
    val safeHeight = height.coerceAtMost(1f - safeY)
    if (safeWidth <= 0f || safeHeight <= 0f) return null
    return copy(
        x = safeX,
        y = safeY,
        width = safeWidth,
        height = safeHeight
    )
}

data class AiTranslationValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

fun validateAiTranslatedBook(book: AiTranslatedBook): AiTranslationValidationResult {
    val errors = mutableListOf<String>()
    if (book.schemaVersion != 1) errors += "schemaVersion must be 1"
    if (book.bookId.isBlank()) errors += "bookId is required"
    if (book.pageCount < 0) errors += "pageCount must be non-negative"
    book.pages.forEach { page ->
        if (page.pageIndex !in 0 until book.pageCount.coerceAtLeast(1)) {
            errors += "page ${page.pageIndex} is outside pageCount"
        }
        if (page.imageWidth < 0 || page.imageHeight < 0) {
            errors += "page ${page.pageIndex} image size must be non-negative"
        }
        page.blocks.forEachIndexed { index, block ->
            validateBlock(page.pageIndex, index, block, errors)
        }
    }
    return AiTranslationValidationResult(errors.isEmpty(), errors)
}

private fun validateBlock(
    pageIndex: Int,
    blockIndex: Int,
    block: AiTranslationBlock,
    errors: MutableList<String>
) {
    val rect = block.rect
    val rectValid = rect.x in 0f..1f &&
        rect.y in 0f..1f &&
        rect.width in 0f..1f &&
        rect.height in 0f..1f &&
        rect.x + rect.width <= 1.0001f &&
        rect.y + rect.height <= 1.0001f
    if (!rectValid) errors += "page $pageIndex block $blockIndex rect is invalid"
    val translationRect = block.translationRect
    val translationRectValid = translationRect == AiTranslationRect() || (
        translationRect.x in 0f..1f &&
            translationRect.y in 0f..1f &&
            translationRect.width in 0f..1f &&
            translationRect.height in 0f..1f &&
            translationRect.x + translationRect.width <= 1.0001f &&
            translationRect.y + translationRect.height <= 1.0001f
        )
    if (!translationRectValid) errors += "page $pageIndex block $blockIndex translationRect is invalid"
    if (!isHexColor(block.textColor)) errors += "page $pageIndex block $blockIndex textColor is invalid"
    if (!isHexColor(block.maskColor)) errors += "page $pageIndex block $blockIndex maskColor is invalid"
    if (block.maskAlpha !in 0.55f..0.88f) errors += "page $pageIndex block $blockIndex maskAlpha is invalid"
    if (block.confidence !in 0f..1f) errors += "page $pageIndex block $blockIndex confidence is invalid"
}

private fun isHexColor(value: String): Boolean =
    Regex("^#[0-9A-Fa-f]{6}$").matches(value)
