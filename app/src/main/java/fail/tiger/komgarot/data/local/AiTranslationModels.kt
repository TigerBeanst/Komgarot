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

enum class AiTranslationTextDirection {
    AUTO,
    HORIZONTAL,
    VERTICAL
}

data class AiTranslationBlock(
    val localRegionId: String = "",
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
        rotationDegrees = rotationDegrees.coerceIn(-12f, 12f),
        fontScale = fontScale.coerceIn(0.6f, 1.4f),
        confidence = confidence.coerceIn(0f, 1f)
    )
}

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
