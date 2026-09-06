package fail.tiger.komgarot.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationPoint
import fail.tiger.komgarot.data.local.AiTranslationRegionStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.suppressDuplicateRenderedTranslations
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AiTranslationDisplayMode(val storedValue: String) {
    OFF("off"),
    ON("on");

    companion object {
        fun fromStoredValue(value: String): AiTranslationDisplayMode =
            entries.firstOrNull { it.storedValue == value } ?: OFF
    }
}

@Composable
fun AiTranslationOverlay(
    page: AiTranslatedPage?,
    mode: AiTranslationDisplayMode,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    verticalGlyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
) {
    if (page == null || mode == AiTranslationDisplayMode.OFF) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val localDensity = LocalDensity.current
        val layoutFontScale = localDensity.fontScale.coerceIn(0.5f, 2f)
        val textMeasurer = rememberTextMeasurer()
        val measurementBaseStyle = MaterialTheme.typography.bodySmall
        val measureText: (String, Float, Float) -> AiMeasuredGlyphSize = { text, fontSize, lineHeightMultiplier ->
            val measured = textMeasurer.measure(
                text = text,
                style = measurementBaseStyle.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Normal,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                softWrap = false
            )
            AiMeasuredGlyphSize(
                widthDp = measured.size.width / localDensity.density,
                heightDp = measured.size.height / localDensity.density
            )
        }
        val bounds = imageContentBounds(
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            imageWidth = page.imageWidth,
            imageHeight = page.imageHeight,
            fillWidth = fillWidth
        )
        val displayBlocks = remember(page.blocks) { page.blocks.suppressDuplicateRenderedTranslations() }
        displayBlocks.forEach { block ->
            val safe = block.renderSafe()
            val renderTextDirection = aiTranslationRenderTextDirection(safe)
            val hasTranslatedText = safe.translatedLines.any { it.isNotBlank() }
            val translationMode = AiTranslationMode.fromStoredValue(page.mode)
            val showProcessingPlaceholder = shouldShowAiTranslationProcessingPlaceholder(
                pageStatus = page.status,
                regionStatus = safe.regionStatus,
                translationMode = translationMode
            )
            if (!hasTranslatedText && !showProcessingPlaceholder) return@forEach
            val sourceMaskPlacements = aiTranslationSourceMaskRects(
                block = safe,
                pageWidthDp = bounds.width.value,
                pageHeightDp = bounds.height.value,
                translationMode = translationMode
            )
            val textPlacement = aiTranslationTextPlacement(
                block = safe,
                sourceMaskPlacements = sourceMaskPlacements,
                translationMode = translationMode,
                pageWidthDp = bounds.width.value,
                pageHeightDp = bounds.height.value
            )
            val blockWidth = bounds.width * textPlacement.width.coerceIn(0f, 1f)
            val blockHeight = bounds.height * textPlacement.height.coerceIn(0f, 1f)
            val sourceColumnMetrics = aiTranslationSourceColumnMetrics(
                columns = sourceMaskPlacements,
                pageWidthDp = bounds.width.value,
                pageHeightDp = bounds.height.value
            )
            val sourceGapMetrics = aiTranslationSourceColumnMetrics(
                columns = safe.sourceColumns,
                pageWidthDp = bounds.width.value,
                pageHeightDp = bounds.height.value
            )
            val fittedFontSizeSp = aiTranslationFontSizeSp(
                baseScale = safe.fontScale,
                rectWidthDp = blockWidth.value,
                rectHeightDp = blockHeight.value,
                textDirection = renderTextDirection,
                lineCount = safe.translatedLines.size.coerceAtLeast(1),
                textLength = safe.translatedLines.sumOf { it.length }.coerceAtLeast(1),
                kind = safe.kind,
                sourceColumnWidthDp = sourceColumnMetrics.fontWidthDp,
                sourceColumnHeightDp = sourceColumnMetrics.maxHeightDp,
                sourceColumnCount = sourceColumnMetrics.columnCount,
                sourceLineHeightDp = sourceGapMetrics.medianHeightDp
            )
            if (!hasTranslatedText) {
                sourceMaskPlacements.forEach { sourceMaskPlacement ->
                    val sourceMaskWidth = bounds.width * sourceMaskPlacement.width.coerceIn(0f, 1f)
                    val sourceMaskHeight = bounds.height * sourceMaskPlacement.height.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .offset(
                                x = bounds.x + bounds.width * sourceMaskPlacement.x.coerceIn(0f, 1f),
                                y = bounds.y + bounds.height * sourceMaskPlacement.y.coerceIn(0f, 1f)
                            )
                            .width(sourceMaskWidth)
                            .heightIn(min = sourceMaskHeight),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AiTranslationRegionPlaceholder(
                            placeholderColor = parseAiColor(safe.textColor),
                            cornerRadius = safe.cornerRadius,
                            placeholderAlpha = AI_TRANSLATION_PLACEHOLDER_ALPHA,
                            modifier = Modifier
                                .width(sourceMaskWidth)
                                .height(sourceMaskHeight)
                        )
                    }
                }
            } else {
                val usesSolidTextBoxMask = safe.kind.usesSolidAiTranslationMask()
                val maskPlan = aiTranslationMaskPlan(
                    translationMode = translationMode,
                    hasBubbleOutline = safe.bubbleOutline.size >= 4,
                    bubbleSolidFill = safe.bubbleSolidFill,
                    bubbleBoundsArea = aiTranslationBubbleBoundsArea(safe.bubbleOutline)
                )
                val translatedTextBackgroundColor = if (usesSolidTextBoxMask) {
                    Color.Transparent
                } else {
                    parseAiColor(safe.maskColor).copy(alpha = safe.maskAlpha)
                }
                val highAccuracySafetyPadding = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                    AI_TRANSLATION_HIGH_ACCURACY_TEXT_SAFETY_PADDING_DP.dp
                } else {
                    0.dp
                }
                val inlineTextPadding = when {
                    translationMode == AiTranslationMode.HIGH_ACCURACY -> 0.dp
                    usesSolidTextBoxMask -> 0.dp
                    else -> 0.5.dp
                }
                val horizontalLinePadding = when {
                    translationMode == AiTranslationMode.HIGH_ACCURACY -> 0.dp
                    usesSolidTextBoxMask -> 0.dp
                    else -> 1.dp
                }
                val verticalColumnHorizontalPadding = if (
                    renderTextDirection == AiTranslationTextDirection.VERTICAL && usesSolidTextBoxMask
                ) {
                    if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                        0.dp
                    } else {
                        AI_TRANSLATION_VERTICAL_TEXT_COLUMN_HORIZONTAL_PADDING_DP.dp
                    }
                } else {
                    inlineTextPadding
                }
                val verticalColumnVerticalPadding = if (
                    renderTextDirection == AiTranslationTextDirection.VERTICAL && usesSolidTextBoxMask
                ) {
                    0.dp
                } else {
                    inlineTextPadding
                }
                val textGroupGap = if (renderTextDirection == AiTranslationTextDirection.VERTICAL) {
                    aiTranslationVerticalColumnGapDp(sourceGapMetrics.medianGapDp, safe.kind).dp
                } else if (usesSolidTextBoxMask) {
                    0.dp
                } else {
                    1.dp
                }
                if (usesSolidTextBoxMask && translationMode == AiTranslationMode.HIGH_ACCURACY) {
                    AiHighAccuracyBubbleMask(
                        block = safe,
                        maskPlan = maskPlan,
                        sourceMaskPlacements = sourceMaskPlacements,
                        bounds = bounds,
                        maskColor = parseAiColor(safe.maskColor)
                    )
                }
                if (usesSolidTextBoxMask && maskPlan.drawRectangularSourceMasks) {
                    sourceMaskPlacements.forEach { sourceMaskPlacement ->
                        val sourceMaskWidth = bounds.width * sourceMaskPlacement.width.coerceIn(0f, 1f)
                        val sourceMaskHeight = bounds.height * sourceMaskPlacement.height.coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .offset(
                                    x = bounds.x + bounds.width * sourceMaskPlacement.x.coerceIn(0f, 1f),
                                    y = bounds.y + bounds.height * sourceMaskPlacement.y.coerceIn(0f, 1f)
                                )
                                .width(sourceMaskWidth)
                                .heightIn(min = sourceMaskHeight),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AiTranslationSourceTextMask(
                                maskColor = parseAiColor(safe.maskColor),
                                cornerRadius = safe.cornerRadius,
                                maskAlpha = normalAiTranslationMaskAlpha(safe.maskAlpha),
                                modifier = Modifier
                                    .width(sourceMaskWidth)
                                    .height(sourceMaskHeight)
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .offset(
                            x = bounds.x + bounds.width * textPlacement.x.coerceIn(0f, 1f),
                            y = bounds.y + bounds.height * textPlacement.y.coerceIn(0f, 1f)
                        )
                        .width(blockWidth)
                        .height(blockHeight)
                        .clipToBounds(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (renderTextDirection == AiTranslationTextDirection.VERTICAL) {
                        val verticalLayout = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                            fitMeasuredVerticalAiTranslationText(
                                lines = safe.translatedLines,
                                rectWidthDp = (blockWidth.value - highAccuracySafetyPadding.value * 2f).coerceAtLeast(0.5f),
                                rectHeightDp = (blockHeight.value - highAccuracySafetyPadding.value * 2f).coerceAtLeast(0.5f),
                                baseFontSizeSp = fittedFontSizeSp,
                                kind = safe.kind,
                                glyphSpacingMultiplier = verticalGlyphSpacingMultiplier,
                                columnGapDp = textGroupGap.value,
                                measureGlyph = { glyph, fontSize ->
                                    measureText(glyph.toString(), fontSize, AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER)
                                }
                            )
                        } else fitVerticalAiTranslationText(
                            lines = safe.translatedLines,
                            rectWidthDp = aiTranslationFitExtentDp(
                                totalDp = blockWidth.value,
                                paddingDp = verticalColumnHorizontalPadding.value,
                                fontScale = layoutFontScale
                            ),
                            rectHeightDp = aiTranslationFitExtentDp(
                                totalDp = blockHeight.value,
                                paddingDp = verticalColumnVerticalPadding.value,
                                fontScale = layoutFontScale
                            ),
                            baseFontSizeSp = fittedFontSizeSp,
                            kind = safe.kind,
                            glyphSpacingMultiplier = verticalGlyphSpacingMultiplier,
                            columnGapDp = textGroupGap.value / layoutFontScale,
                            sourceColumnWidthDp = aiTranslationLayoutSourceColumnWidthDp(
                                translationMode = translationMode,
                                sourceColumnWidthDp = sourceColumnMetrics.fontWidthDp
                            ) / layoutFontScale,
                            sourceColumnHeightDp = aiTranslationLayoutSourceColumnWidthDp(
                                translationMode = translationMode,
                                sourceColumnWidthDp = sourceColumnMetrics.maxHeightDp
                            ) / layoutFontScale,
                            sourceColumnCount = aiTranslationLayoutSourceColumnCount(
                                translationMode = translationMode,
                                sourceColumnCount = sourceColumnMetrics.columnCount
                            )
                        )
                        val columnWidth = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                            Dp(verticalLayout.columnWidthDp)
                        } else {
                            Dp(verticalLayout.columnWidthDp * layoutFontScale)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(textGroupGap),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .offset(y = verticalTextTopOffsetDp(verticalLayout.fontSizeSp))
                                .padding(if (translationMode == AiTranslationMode.HIGH_ACCURACY) highAccuracySafetyPadding else 0.dp)
                                .wrapContentSize(unbounded = true)
                                .normalTextBoxMask(
                                    enabled = usesSolidTextBoxMask && maskPlan.drawTextBackground,
                                    orientation = TextBackgroundOrientation.VERTICAL,
                                    color = parseAiColor(safe.maskColor).copy(alpha = normalAiTranslationMaskAlpha(safe.maskAlpha)),
                                    cornerRadius = safe.cornerRadius
                                )
                        ) {
                            verticalLayout.columns.forEach { column ->
                                VerticalTextColumnBackground(
                                    text = column,
                                    textColor = parseAiColor(safe.textColor),
                                    backgroundColor = translatedTextBackgroundColor,
                                    cornerRadius = safe.cornerRadius,
                                    fontSizeSp = verticalLayout.fontSizeSp,
                                    lineHeightMultiplier = AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER,
                                    glyphSpacingMultiplier = verticalGlyphSpacingMultiplier,
                                    columnWidth = columnWidth,
                                    horizontalPadding = verticalColumnHorizontalPadding,
                                    verticalPadding = verticalColumnVerticalPadding
                                )
                            }
                        }
                    } else {
                        val horizontalLayout = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                            fitMeasuredHorizontalAiTranslationText(
                                lines = safe.translatedLines,
                                rectWidthDp = (blockWidth.value - highAccuracySafetyPadding.value * 2f).coerceAtLeast(0.5f),
                                rectHeightDp = (blockHeight.value - highAccuracySafetyPadding.value * 2f).coerceAtLeast(0.5f),
                                baseFontSizeSp = fittedFontSizeSp,
                                kind = safe.kind,
                                lineGapDp = textGroupGap.value,
                                measureText = { text, fontSize ->
                                    measureText(text, fontSize, AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER)
                                }
                            )
                        } else fitHorizontalAiTranslationText(
                            lines = safe.translatedLines,
                            rectWidthDp = aiTranslationFitExtentDp(
                                totalDp = blockWidth.value,
                                paddingDp = horizontalLinePadding.value,
                                fontScale = layoutFontScale
                            ),
                            rectHeightDp = aiTranslationFitExtentDp(
                                totalDp = blockHeight.value,
                                paddingDp = inlineTextPadding.value,
                                fontScale = layoutFontScale
                            ),
                            baseFontSizeSp = fittedFontSizeSp,
                            kind = safe.kind,
                            lineGapDp = textGroupGap.value / layoutFontScale
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(textGroupGap),
                            modifier = Modifier
                                .padding(if (translationMode == AiTranslationMode.HIGH_ACCURACY) highAccuracySafetyPadding else 0.dp)
                                .wrapContentHeight(unbounded = true)
                                .normalTextBoxMask(
                                    enabled = usesSolidTextBoxMask && maskPlan.drawTextBackground,
                                    orientation = TextBackgroundOrientation.HORIZONTAL,
                                    color = parseAiColor(safe.maskColor).copy(alpha = normalAiTranslationMaskAlpha(safe.maskAlpha)),
                                    cornerRadius = safe.cornerRadius
                                )
                        ) {
                            horizontalLayout.lines.forEach { line ->
                                HorizontalTextLineBackground(
                                    text = line,
                                    textColor = parseAiColor(safe.textColor),
                                    backgroundColor = translatedTextBackgroundColor,
                                    cornerRadius = safe.cornerRadius,
                                    fontSizeSp = horizontalLayout.fontSizeSp,
                                    lineHeightMultiplier = AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER,
                                    maxWidth = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                                        horizontalLayout.maxLineWidth
                                    } else {
                                        horizontalLayout.maxLineWidth * layoutFontScale
                                    },
                                    horizontalPadding = horizontalLinePadding,
                                    verticalPadding = inlineTextPadding
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiHighAccuracyBubbleMask(
    block: AiTranslationBlock,
    maskPlan: AiTranslationMaskPlan,
    sourceMaskPlacements: List<AiTranslationRect>,
    bounds: AiImageContentBounds,
    maskColor: Color
) {
    Canvas(Modifier.fillMaxSize()) {
        val imageLeft = bounds.x.toPx()
        val imageTop = bounds.y.toPx()
        val imageWidth = bounds.width.toPx()
        val imageHeight = bounds.height.toPx()
        val bubblePath = Path().apply {
            val outline = expandAiTranslationBubbleMaskOutline(
                points = block.bubbleOutline,
                expansionRatio = AI_TRANSLATION_BUBBLE_MASK_OUTLINE_EXPANSION_RATIO
            )
            if (outline.size >= 4) {
                moveTo(imageLeft + outline.first().x * imageWidth, imageTop + outline.first().y * imageHeight)
                outline.drop(1).forEach { point ->
                    lineTo(imageLeft + point.x * imageWidth, imageTop + point.y * imageHeight)
                }
                close()
            } else {
                val fallback = block.translationRect.takeIf { it.width > 0f && it.height > 0f } ?: block.rect
                val rect = Rect(
                    left = imageLeft + fallback.x * imageWidth,
                    top = imageTop + fallback.y * imageHeight,
                    right = imageLeft + (fallback.x + fallback.width) * imageWidth,
                    bottom = imageTop + (fallback.y + fallback.height) * imageHeight
                )
                if (block.kind == AiTranslationBlockKind.SIGN || block.kind == AiTranslationBlockKind.NARRATION) {
                    val radius = min(rect.width, rect.height) * 0.10f
                    addRoundRect(RoundRect(rect, radius, radius))
                } else {
                    addOval(rect)
                }
            }
        }
        if (maskPlan == AiTranslationMaskPlan.BUBBLE_FILL) {
            drawPath(bubblePath, maskColor)
        } else {
            clipPath(bubblePath) {
                sourceMaskPlacements.forEach { sourceRect ->
                    drawRect(
                        color = maskColor,
                        topLeft = Offset(
                            x = imageLeft + sourceRect.x * imageWidth,
                            y = imageTop + sourceRect.y * imageHeight
                        ),
                        size = Size(
                            width = sourceRect.width * imageWidth,
                            height = sourceRect.height * imageHeight
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTranslationSourceTextMask(
    maskColor: Color,
    cornerRadius: Float,
    maskAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                orientation = TextBackgroundOrientation.HORIZONTAL,
                color = maskColor.copy(alpha = maskAlpha),
                cornerRadius = cornerRadius
            )
    )
}

internal fun AiTranslationBlockKind.usesSolidAiTranslationMask(): Boolean = when (this) {
    AiTranslationBlockKind.DIALOGUE,
    AiTranslationBlockKind.NARRATION,
    AiTranslationBlockKind.SIGN -> true
    AiTranslationBlockKind.SFX,
    AiTranslationBlockKind.OTHER -> false
}

@Suppress("UNUSED_PARAMETER")
internal fun normalAiTranslationMaskAlpha(savedAlpha: Float): Float = 1f

internal fun List<AiTranslationBlock>.withNonOverlappingTranslationRects(
    gap: Float = AI_TRANSLATION_MIN_OVERLAP_GAP,
    maxShift: Float = AI_TRANSLATION_MAX_OVERLAP_SHIFT
): List<AiTranslationBlock> = this

internal fun AiTranslationRect.overlapsAiTranslationRect(
    other: AiTranslationRect,
    gap: Float = 0f
): Boolean {
    val safeGap = gap.coerceAtLeast(0f)
    return x < other.right + safeGap &&
        right + safeGap > other.x &&
        y < other.bottom + safeGap &&
        bottom + safeGap > other.y
}

private fun AiTranslationRect.shiftAwayFromPlacedRects(
    placed: List<AiTranslationRect>,
    gap: Float,
    maxShift: Float
): AiTranslationRect {
    var candidate = this
    repeat(AI_TRANSLATION_OVERLAP_SHIFT_ATTEMPTS) {
        if (placed.none { candidate.overlapsAiTranslationRect(it, gap) }) return candidate
        val shifted = candidate.bestShiftAwayFromPlacedRects(
            original = this,
            placed = placed,
            gap = gap,
            maxShift = maxShift
        )
        if (shifted == candidate) return candidate
        candidate = shifted
    }
    return candidate
}

private fun AiTranslationRect.bestShiftAwayFromPlacedRects(
    original: AiTranslationRect,
    placed: List<AiTranslationRect>,
    gap: Float,
    maxShift: Float
): AiTranslationRect {
    val overlapping = placed.filter { overlapsAiTranslationRect(it, gap) }
    if (overlapping.isEmpty()) return this
    val candidates = overlapping.flatMap { rect ->
        listOf(
            copy(y = rect.bottom + gap),
            copy(y = rect.y - height - gap),
            copy(x = rect.right + gap),
            copy(x = rect.x - width - gap)
        )
    }.map { it.coerceForOverlayPlacement() }
        .filter { it.withinShiftLimit(original, maxShift) }
    if (candidates.isEmpty()) return this
    val clearCandidates = candidates.filter { candidate ->
        placed.none { candidate.overlapsAiTranslationRect(it, gap) }
    }
    val pool = clearCandidates.ifEmpty { candidates }
    return pool.minBy { candidate ->
        val overlapPenalty = placed.count { candidate.overlapsAiTranslationRect(it, gap) } * 10f
        overlapPenalty + abs(candidate.x - x) + abs(candidate.y - y)
    }
}

private fun AiTranslationRect.withinShiftLimit(
    original: AiTranslationRect,
    maxShift: Float
): Boolean =
    abs(x - original.x) <= maxShift && abs(y - original.y) <= maxShift

private fun AiTranslationRect.coerceForOverlayPlacement(): AiTranslationRect {
    val safeX = x.coerceIn(0f, 0.999f)
    val safeY = y.coerceIn(0f, 0.999f)
    val safeWidth = width.coerceAtLeast(0f).coerceAtMost(1f - safeX)
    val safeHeight = height.coerceAtLeast(0f).coerceAtMost(1f - safeY)
    return copy(
        x = safeX,
        y = safeY,
        width = safeWidth,
        height = safeHeight
    )
}

private fun Modifier.normalTextBoxMask(
    enabled: Boolean,
    orientation: TextBackgroundOrientation,
    color: Color,
    cornerRadius: Float
): Modifier = if (enabled) {
    background(
        orientation = orientation,
        color = color,
        cornerRadius = cornerRadius
    )
} else {
    this
}

@Composable
private fun AiTranslationRegionPlaceholder(
    placeholderColor: Color,
    cornerRadius: Float,
    placeholderAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                orientation = TextBackgroundOrientation.HORIZONTAL,
                color = placeholderColor.copy(alpha = placeholderAlpha),
                cornerRadius = cornerRadius
            )
    )
}

private fun toVerticalText(value: String): String =
    value.map { char ->
        when (char) {
            '…' -> '︙'
            '—' -> '︱'
            'ー' -> '｜'
            '–' -> '︱'
            '-' -> '︱'
            '（' -> '︵'
            '）' -> '︶'
            '(' -> '︵'
            ')' -> '︶'
            '「' -> '﹁'
            '」' -> '﹂'
            '『' -> '﹃'
            '』' -> '﹄'
            '，' -> '︐'
            ',' -> '︐'
            '。' -> '︒'
            '、' -> '︑'
            '：' -> '︓'
            ':' -> '︓'
            '；' -> '︔'
            ';' -> '︔'
            else -> char
        }
    }.joinToString("")

@Composable
private fun HorizontalTextLineBackground(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    cornerRadius: Float,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    maxWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp
) {
    Text(
        text = text,
        color = textColor,
        softWrap = true,
        style = aiTranslationTextStyle(fontSizeSp, lineHeightMultiplier),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .background(
                orientation = TextBackgroundOrientation.HORIZONTAL,
                color = backgroundColor,
                cornerRadius = cornerRadius
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    )
}

@Composable
private fun VerticalTextColumnBackground(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    cornerRadius: Float,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    glyphSpacingMultiplier: Float,
    columnWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp
) {
    CompactVerticalTextColumn(
        text = text,
        textColor = textColor,
        fontSizeSp = fontSizeSp,
        lineHeightMultiplier = lineHeightMultiplier,
        glyphSpacingMultiplier = glyphSpacingMultiplier,
        modifier = Modifier
            .width(columnWidth)
            .background(
                orientation = TextBackgroundOrientation.VERTICAL,
                color = backgroundColor,
                cornerRadius = cornerRadius
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    )
}

@Composable
private fun CompactVerticalTextColumn(
    text: String,
    textColor: Color,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    glyphSpacingMultiplier: Float,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
        text.forEach { char ->
            Text(
                text = char.toString(),
                color = textColor,
                softWrap = false,
                style = aiTranslationTextStyle(fontSizeSp, lineHeightMultiplier)
            )
        }
        }
    ) { measurables, constraints ->
        val requestedGlyphAdvancePx = verticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).roundToPx().coerceAtLeast(1)
        val placeables = measurables.map { measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0,
                    maxWidth = Constraints.Infinity
                )
            )
        }
        val tallestGlyph = placeables.maxOfOrNull { it.height } ?: 0
        val widestGlyph = placeables.maxOfOrNull { it.width } ?: 0
        val placementAdvancePx = verticalGlyphPlacementAdvancePx(requestedGlyphAdvancePx, tallestGlyph)
        val naturalHeight = if (placeables.isEmpty()) 0 else placementAdvancePx * (placeables.size - 1) + tallestGlyph
        val width = widestGlyph.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = naturalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val x = (width - placeable.width) / 2
                placeable.placeRelative(x = x, y = index * placementAdvancePx)
            }
        }
    }
}

@Composable
private fun aiTranslationTextStyle(
    fontSizeSp: Float,
    lineHeightMultiplier: Float
): TextStyle {
    val base = MaterialTheme.typography.bodySmall
    return base.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}

private enum class TextBackgroundOrientation {
    HORIZONTAL,
    VERTICAL
}

private fun Modifier.background(
    orientation: TextBackgroundOrientation,
    color: Color,
    cornerRadius: Float
): Modifier {
    val multiplier = when (orientation) {
        TextBackgroundOrientation.HORIZONTAL -> 32
        TextBackgroundOrientation.VERTICAL -> 24
    }
    return background(color, RoundedCornerShape((cornerRadius * multiplier).dp))
}

internal fun aiTranslationFontSizeSp(
    baseScale: Float,
    rectWidthDp: Float,
    rectHeightDp: Float,
    textDirection: AiTranslationTextDirection,
    lineCount: Int,
    textLength: Int,
    kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE,
    sourceColumnWidthDp: Float = 0f,
    sourceColumnHeightDp: Float = 0f,
    sourceColumnCount: Int = 0,
    sourceLineHeightDp: Float = 0f
): Float {
    val safeTextLength = textLength.coerceAtLeast(1)
    val safeLineCount = lineCount.coerceAtLeast(1)
    val sizeFromBox = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            val sourceWidthSize = if (sourceColumnWidthDp > 0f) {
                sourceColumnWidthToFontSizeSp(sourceColumnWidthDp)
            } else {
                0f
            }
            val widthSize = sourceWidthSize.takeIf { it > 0f } ?: (rectWidthDp * 0.74f)
            if (kind == AiTranslationBlockKind.DIALOGUE || kind == AiTranslationBlockKind.NARRATION) {
                if (sourceWidthSize > 0f) {
                    widthSize
                } else {
                    max(widthSize, min(rectHeightDp * 0.065f, 12.8f))
                }
            } else {
                widthSize
            }
        }
        AiTranslationTextDirection.HORIZONTAL -> (rectHeightDp / safeLineCount) * 0.86f
        AiTranslationTextDirection.AUTO -> minOf(rectWidthDp, rectHeightDp) * 0.62f
    }
    val sizeFromTextHeight = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            val layoutColumnCount = if (sourceColumnCount > 0) sourceColumnCount + 1 else 1
            val charsPerColumn = ((safeTextLength + layoutColumnCount - 1) / layoutColumnCount).coerceAtLeast(1)
            val sourceHeightSize = if (sourceColumnHeightDp > 0f) {
                sourceColumnHeightDp / maxOf(1.4f, charsPerColumn * 0.92f)
            } else {
                0f
            }
            if (sourceHeightSize > 0f) {
                max(sourceHeightSize, rectHeightDp / maxOf(1.4f, safeTextLength * 0.50f))
            } else {
                rectHeightDp / maxOf(1.4f, safeTextLength * 0.50f)
            }
        }
        AiTranslationTextDirection.HORIZONTAL -> rectHeightDp / (safeLineCount * 0.92f)
        AiTranslationTextDirection.AUTO -> rectHeightDp / maxOf(1.6f, safeLineCount * 1.1f)
    }
    val sizeFromTextArea = sqrt((rectWidthDp * rectHeightDp) / maxOf(1f, safeTextLength * 0.72f))
    val rawSize = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> {
            if (sourceColumnWidthDp > 0f && kind != AiTranslationBlockKind.SFX) {
                sizeFromBox
            } else {
                minOf(sizeFromBox, sizeFromTextHeight, sizeFromTextArea * 1.22f)
            }
        }
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> minOf(sizeFromBox, sizeFromTextHeight, sizeFromTextArea * 1.18f)
    }
    val minimumReadableSize = if (textDirection == AiTranslationTextDirection.VERTICAL) 9.2f else 8.8f
    val scaledSize = (rawSize * baseScale).let { size ->
        if (textDirection == AiTranslationTextDirection.HORIZONTAL && sourceLineHeightDp > 0f) {
            min(size, sourceLineHeightDp * 0.90f)
        } else {
            size
        }
    }
    val normalSize = if (textDirection == AiTranslationTextDirection.VERTICAL) {
        scaledSize.coerceIn(minimumReadableSize, AI_TRANSLATION_MAX_VERTICAL_FONT_SP)
    } else {
        scaledSize.coerceIn(minimumReadableSize, 28f)
    }
    return displayAiTranslationFontSizeSp(kind, normalSize, textDirection)
}

internal fun shouldShowAiTranslationProcessingPlaceholder(
    pageStatus: AiTranslationPageStatus,
    regionStatus: AiTranslationRegionStatus,
    translationMode: AiTranslationMode
): Boolean =
    translationMode != AiTranslationMode.HIGH_ACCURACY &&
        pageStatus == AiTranslationPageStatus.RUNNING &&
        (regionStatus == AiTranslationRegionStatus.PENDING ||
            regionStatus == AiTranslationRegionStatus.RUNNING)

internal data class AiVerticalTextLayout(
    val fontSizeSp: Float,
    val charsPerColumn: Int,
    val columns: List<String>,
    val columnWidthDp: Float
)

internal data class AiMeasuredGlyphSize(
    val widthDp: Float,
    val heightDp: Float
)

internal data class AiMeasuredLayoutBounds(
    val widthDp: Float,
    val heightDp: Float
)

internal enum class AiTranslationMaskPlan(
    val drawRectangularSourceMasks: Boolean,
    val drawTextBackground: Boolean
) {
    RECTANGULAR_SOURCE(true, true),
    BUBBLE_FILL(false, false),
    BUBBLE_CLIPPED_SOURCE(false, false)
}

internal fun expandAiTranslationBubbleMaskOutline(
    points: List<AiTranslationPoint>,
    expansionRatio: Float
): List<AiTranslationPoint> {
    if (points.size < 4 || expansionRatio <= 0f) return points
    val left = points.minOf(AiTranslationPoint::x)
    val right = points.maxOf(AiTranslationPoint::x)
    val top = points.minOf(AiTranslationPoint::y)
    val bottom = points.maxOf(AiTranslationPoint::y)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val expansionScale = 1f + expansionRatio * 2f
    return points.map { point ->
        AiTranslationPoint(
            x = (centerX + (point.x - centerX) * expansionScale).coerceIn(0f, 1f),
            y = (centerY + (point.y - centerY) * expansionScale).coerceIn(0f, 1f)
        )
    }
}

internal fun aiTranslationMaskPlan(
    translationMode: AiTranslationMode,
    hasBubbleOutline: Boolean,
    bubbleSolidFill: Boolean,
    bubbleBoundsArea: Float = 0f
): AiTranslationMaskPlan = when {
    translationMode != AiTranslationMode.HIGH_ACCURACY -> AiTranslationMaskPlan.RECTANGULAR_SOURCE
    hasBubbleOutline && bubbleSolidFill && bubbleBoundsArea <= AI_TRANSLATION_MAX_SOLID_BUBBLE_FILL_AREA ->
        AiTranslationMaskPlan.BUBBLE_FILL
    else -> AiTranslationMaskPlan.BUBBLE_CLIPPED_SOURCE
}

internal fun aiTranslationBubbleBoundsArea(points: List<AiTranslationPoint>): Float {
    if (points.size < 4) return 0f
    val width = points.maxOf(AiTranslationPoint::x) - points.minOf(AiTranslationPoint::x)
    val height = points.maxOf(AiTranslationPoint::y) - points.minOf(AiTranslationPoint::y)
    return width.coerceAtLeast(0f) * height.coerceAtLeast(0f)
}

internal fun fitMeasuredVerticalAiTranslationText(
    lines: List<String>,
    rectWidthDp: Float,
    rectHeightDp: Float,
    baseFontSizeSp: Float,
    kind: AiTranslationBlockKind,
    glyphSpacingMultiplier: Float,
    columnGapDp: Float,
    measureGlyph: (Char, Float) -> AiMeasuredGlyphSize
): AiVerticalTextLayout {
    val safeWidth = rectWidthDp.coerceAtLeast(0.5f)
    val safeHeight = rectHeightDp.coerceAtLeast(0.5f)
    val fullText = lines.joinToString("").ifBlank { " " }
    fun layout(fontSizeSp: Float, textValue: String = fullText): AiVerticalTextLayout {
        val text = textValue.map { toVerticalText(it.toString()).first() }
        val metrics = text.map { measureGlyph(it, fontSizeSp) }
        val widest = metrics.maxOfOrNull { it.widthDp } ?: fontSizeSp
        val tallest = metrics.maxOfOrNull { it.heightDp } ?: fontSizeSp
        val advance = max(
            fontSizeSp * glyphSpacingMultiplier,
            tallest * AI_TRANSLATION_MIN_VERTICAL_GLYPH_SPACING_MULTIPLIER
        ).coerceAtLeast(0.25f)
        val charsPerColumn = (((safeHeight - tallest).coerceAtLeast(0f) / advance).toInt() + 1).coerceAtLeast(1)
        return AiVerticalTextLayout(
            fontSizeSp = fontSizeSp,
            charsPerColumn = charsPerColumn,
            columns = verticalTextColumnsForDisplay(listOf(text.joinToString("")), charsPerColumn, kind),
            columnWidthDp = max(widest, fontSizeSp * AI_TRANSLATION_VERTICAL_COLUMN_WIDTH_MULTIPLIER)
        )
    }
    val completeTextMinimum = absoluteFitMinimumFontSize(kind)
    var low = completeTextMinimum
    var high = baseFontSizeSp.coerceAtLeast(low)
    var best = layout(low)
    repeat(AI_TRANSLATION_FIT_ITERATIONS * 2) {
        val candidate = layout((low + high) / 2f)
        val bounds = measuredVerticalLayoutBounds(candidate, glyphSpacingMultiplier, columnGapDp, measureGlyph)
        if (bounds.widthDp <= safeWidth && bounds.heightDp <= safeHeight) {
            best = candidate
            low = candidate.fontSizeSp
        } else {
            high = candidate.fontSizeSp
        }
    }
    return best
}

internal fun measuredVerticalLayoutBounds(
    layout: AiVerticalTextLayout,
    glyphSpacingMultiplier: Float,
    columnGapDp: Float,
    measureGlyph: (Char, Float) -> AiMeasuredGlyphSize
): AiMeasuredLayoutBounds {
    val glyphs = layout.columns.flatMap(String::toList)
    val metrics = glyphs.map { glyph -> measureGlyph(glyph, layout.fontSizeSp) }
    val widest = metrics.maxOfOrNull { it.widthDp } ?: 0f
    val tallest = metrics.maxOfOrNull { it.heightDp } ?: 0f
    val advance = max(
        layout.fontSizeSp * glyphSpacingMultiplier,
        tallest * AI_TRANSLATION_MIN_VERTICAL_GLYPH_SPACING_MULTIPLIER
    )
    val maxLength = layout.columns.maxOfOrNull(String::length) ?: 0
    val height = if (maxLength <= 0) 0f else tallest + advance * (maxLength - 1)
    return AiMeasuredLayoutBounds(
        widthDp = verticalTextLayoutWidthDp(max(layout.columnWidthDp, widest), layout.columns.size, columnGapDp),
        heightDp = height
    )
}

internal fun fitMeasuredHorizontalAiTranslationText(
    lines: List<String>,
    rectWidthDp: Float,
    rectHeightDp: Float,
    baseFontSizeSp: Float,
    kind: AiTranslationBlockKind,
    lineGapDp: Float,
    measureText: (String, Float) -> AiMeasuredGlyphSize
): AiHorizontalTextLayout {
    val safeWidth = rectWidthDp.coerceAtLeast(0.5f)
    val safeHeight = rectHeightDp.coerceAtLeast(0.5f)
    val fullText = lines.map(String::trim).filter(String::isNotBlank)
        .reduceOrNull(::joinTranslatedLine)
        .orEmpty()
        .ifBlank { " " }
    fun layout(fontSizeSp: Float, textValue: String = fullText): AiHorizontalTextLayout = AiHorizontalTextLayout(
        fontSizeSp = fontSizeSp,
        lines = balancedHorizontalLines(listOf(textValue), safeWidth, fontSizeSp),
        maxLineWidth = Dp(safeWidth)
    )
    val completeTextMinimum = absoluteFitMinimumFontSize(kind)
    var low = completeTextMinimum
    var high = baseFontSizeSp.coerceAtLeast(low)
    var best = layout(low)
    repeat(AI_TRANSLATION_FIT_ITERATIONS * 2) {
        val candidate = layout((low + high) / 2f)
        val bounds = measuredHorizontalLayoutBounds(candidate, lineGapDp, measureText)
        if (bounds.widthDp <= safeWidth && bounds.heightDp <= safeHeight) {
            best = candidate
            low = candidate.fontSizeSp
        } else {
            high = candidate.fontSizeSp
        }
    }
    return best
}

internal fun measuredHorizontalLayoutBounds(
    layout: AiHorizontalTextLayout,
    lineGapDp: Float,
    measureText: (String, Float) -> AiMeasuredGlyphSize
): AiMeasuredLayoutBounds {
    val measurements = layout.lines.map { line -> measureText(line, layout.fontSizeSp) }
    return AiMeasuredLayoutBounds(
        widthDp = measurements.maxOfOrNull { it.widthDp } ?: 0f,
        heightDp = measurements.sumOf { it.heightDp.toDouble() }.toFloat() +
            (measurements.size - 1).coerceAtLeast(0) * lineGapDp
    )
}

internal data class AiHorizontalTextLayout(
    val fontSizeSp: Float,
    val lines: List<String>,
    val maxLineWidth: Dp
)

internal data class AiVerticalSourceColumnMetrics(
    val columnCount: Int = 0,
    val medianWidthDp: Float = 0f,
    val fontWidthDp: Float = 0f,
    val maxHeightDp: Float = 0f,
    val medianHeightDp: Float = 0f,
    val medianGapDp: Float = 0f
)

internal fun aiTranslationRenderTextDirection(block: AiTranslationBlock): AiTranslationTextDirection {
    if (block.textDirection != AiTranslationTextDirection.AUTO) {
        return block.textDirection
    }
    val columns = block.sourceColumns.filter { it.width > 0f && it.height > 0f }
    if (columns.isEmpty()) return block.textDirection
    val horizontalLines = columns.count { it.width >= it.height * 1.15f }
    val verticalColumns = columns.count { it.height > it.width * 1.35f }
    return when {
        horizontalLines > verticalColumns -> AiTranslationTextDirection.HORIZONTAL
        block.textDirection == AiTranslationTextDirection.VERTICAL -> AiTranslationTextDirection.VERTICAL
        verticalColumns > horizontalLines -> AiTranslationTextDirection.VERTICAL
        block.textDirection == AiTranslationTextDirection.AUTO && block.rect.width >= block.rect.height ->
            AiTranslationTextDirection.HORIZONTAL
        block.textDirection == AiTranslationTextDirection.AUTO -> AiTranslationTextDirection.VERTICAL
        else -> block.textDirection
    }
}

internal fun aiTranslationSourceMaskRects(
    block: AiTranslationBlock,
    pageWidthDp: Float = 0f,
    pageHeightDp: Float = 0f,
    translationMode: AiTranslationMode = AiTranslationMode.LOCAL_DETECTION
): List<AiTranslationRect> {
    val sourceColumns = block.sourceColumns.filter { it.width > 0f && it.height > 0f }
    val rects = sourceColumns.ifEmpty { listOf(block.rect) }
    val paddedRects = if (sourceColumns.isEmpty() || pageWidthDp <= 0f || pageHeightDp <= 0f) {
        rects
    } else {
        val minimumPaddingDp = if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
            AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_PADDING_DP
        } else {
            AI_TRANSLATION_SOURCE_TEXT_MASK_PADDING_DP
        }
        rects.map { rect ->
            val horizontalPadding = max(
                minimumPaddingDp / pageWidthDp,
                if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                    rect.width * AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_HORIZONTAL_EXPANSION
                } else {
                    0f
                }
            )
            val verticalPadding = max(
                minimumPaddingDp / pageHeightDp,
                if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
                    rect.height * AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_VERTICAL_EXPANSION
                } else {
                    0f
                }
            )
            rect.expandNormalized(horizontalPadding, verticalPadding)
        }
    }
    val horizontalBlock = aiTranslationRenderTextDirection(block) == AiTranslationTextDirection.HORIZONTAL
    return if (horizontalBlock && paddedRects.size > 1) {
        if (translationMode == AiTranslationMode.HIGH_ACCURACY) {
            mergeNearbyHorizontalMaskRects(paddedRects)
        } else {
            listOfNotNull(paddedRects.boundingRectOrNull())
        }
    } else {
        paddedRects
    }
}

private fun mergeNearbyHorizontalMaskRects(rects: List<AiTranslationRect>): List<AiTranslationRect> {
    val sorted = rects.filter { it.width > 0f && it.height > 0f }.sortedBy { it.y }
    if (sorted.size < 2) return sorted
    val groups = mutableListOf<MutableList<AiTranslationRect>>()
    sorted.forEach { rect ->
        val current = groups.lastOrNull()
        val currentBounds = current?.boundingRectOrNull()
        val gap = currentBounds?.let { (rect.y - it.bottom).coerceAtLeast(0f) } ?: Float.POSITIVE_INFINITY
        val gapLimit = currentBounds?.let { max(it.height, rect.height) * 0.90f } ?: 0f
        if (current != null && gap <= gapLimit) {
            current += rect
        } else {
            groups += mutableListOf(rect)
        }
    }
    return groups.mapNotNull { it.boundingRectOrNull() }
}

internal fun aiTranslationTextPlacement(
    block: AiTranslationBlock,
    sourceMaskPlacements: List<AiTranslationRect>,
    translationMode: AiTranslationMode = AiTranslationMode.LOCAL_DETECTION,
    pageWidthDp: Float = 0f,
    pageHeightDp: Float = 0f
): AiTranslationRect {
    val requestedPlacement = block.translationRect.effectiveOrNull()
    if (translationMode == AiTranslationMode.HIGH_ACCURACY && requestedPlacement != null) {
        return requestedPlacement
            .expandForVerticalBubbleInterior(block)
            .expandForCompleteHorizontalText(block, pageWidthDp, pageHeightDp)
    }
    val ocrPlacement = sourceMaskPlacements
        .takeIf { block.kind.usesSolidAiTranslationMask() }
        ?.boundingRectOrNull()
    return ocrPlacement ?: requestedPlacement ?: block.rect
}

private fun AiTranslationRect.expandForVerticalBubbleInterior(
    block: AiTranslationBlock
): AiTranslationRect {
    if (
        block.bubbleOutline.size < 4 ||
        aiTranslationRenderTextDirection(block) != AiTranslationTextDirection.VERTICAL
    ) {
        return this
    }
    val outlineLeft = block.bubbleOutline.minOf(AiTranslationPoint::x)
    val outlineRight = block.bubbleOutline.maxOf(AiTranslationPoint::x)
    val outlineTop = block.bubbleOutline.minOf(AiTranslationPoint::y)
    val outlineBottom = block.bubbleOutline.maxOf(AiTranslationPoint::y)
    val outlineWidth = outlineRight - outlineLeft
    val outlineHeight = outlineBottom - outlineTop
    if (outlineWidth <= 0f || outlineHeight <= 0f) return this
    val horizontalInset = outlineWidth * AI_TRANSLATION_VERTICAL_BUBBLE_INTERIOR_HORIZONTAL_INSET_RATIO
    val verticalInset = outlineHeight * AI_TRANSLATION_VERTICAL_BUBBLE_INTERIOR_VERTICAL_INSET_RATIO
    val interior = AiTranslationRect(
        x = outlineLeft + horizontalInset,
        y = outlineTop + verticalInset,
        width = outlineWidth - horizontalInset * 2f,
        height = outlineHeight - verticalInset * 2f
    ).coerceForOverlayPlacement()
    return if (interior.width > width || interior.height > height) interior else this
}

private fun AiTranslationRect.expandForCompleteHorizontalText(
    block: AiTranslationBlock,
    pageWidthDp: Float,
    pageHeightDp: Float
): AiTranslationRect {
    val expandableKind = block.kind == AiTranslationBlockKind.SIGN || block.kind == AiTranslationBlockKind.NARRATION
    if (
        !expandableKind ||
        block.bubbleOutline.size >= 4 ||
        aiTranslationRenderTextDirection(block) != AiTranslationTextDirection.HORIZONTAL ||
        pageWidthDp <= 0f ||
        pageHeightDp <= 0f
    ) {
        return this
    }
    val longestLineUnits = block.translatedLines
        .filter(String::isNotBlank)
        .maxOfOrNull(::visualTextUnits)
        ?: return this
    val desiredWidthDp = longestLineUnits * AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_TARGET_CHAR_DP +
        AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_PADDING_DP * 2f
    val targetWidth = max(
        width * AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_MIN_EXPANSION,
        desiredWidthDp / pageWidthDp
    ).coerceIn(width, AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_MAX_WIDTH)
    val centerX = x + width / 2f
    val expandedX = (centerX - targetWidth / 2f).coerceIn(0f, 1f - targetWidth)
    return copy(x = expandedX, width = targetWidth)
}

internal fun aiTranslationLayoutSourceColumnWidthDp(
    translationMode: AiTranslationMode,
    sourceColumnWidthDp: Float
): Float = if (translationMode == AiTranslationMode.HIGH_ACCURACY) 0f else sourceColumnWidthDp

internal fun aiTranslationLayoutSourceColumnCount(
    translationMode: AiTranslationMode,
    sourceColumnCount: Int
): Int = if (translationMode == AiTranslationMode.HIGH_ACCURACY) 0 else sourceColumnCount

internal fun aiTranslationFitExtentDp(
    totalDp: Float,
    paddingDp: Float,
    fontScale: Float = 1f
): Float =
    ((totalDp - paddingDp.coerceAtLeast(0f) * 2f) / fontScale.coerceAtLeast(0.5f)).coerceAtLeast(1f)

internal fun aiTranslationSourceColumnMetrics(
    columns: List<AiTranslationRect>,
    pageWidthDp: Float,
    pageHeightDp: Float
): AiVerticalSourceColumnMetrics {
    val validColumns = columns.filter { it.width > 0f && it.height > 0f }
    if (validColumns.isEmpty() || pageWidthDp <= 0f || pageHeightDp <= 0f) return AiVerticalSourceColumnMetrics()
    val widths = validColumns.map { it.width * pageWidthDp }.sorted()
    val heights = validColumns.map { it.height * pageHeightDp }.sorted()
    val gaps = validColumns
        .sortedBy { it.x }
        .zipWithNext { left, right -> (right.x - left.right) * pageWidthDp }
        .filter { it > 0f }
        .sorted()
    return AiVerticalSourceColumnMetrics(
        columnCount = validColumns.size,
        medianWidthDp = widths[widths.size / 2],
        fontWidthDp = widths.lastOrNull() ?: 0f,
        maxHeightDp = heights.maxOrNull() ?: 0f,
        medianHeightDp = heights[heights.size / 2],
        medianGapDp = gaps.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] } ?: 0f
    )
}

internal fun aiTranslationVerticalColumnGapDp(
    sourceColumnGapDp: Float,
    kind: AiTranslationBlockKind
): Float = when (kind) {
    AiTranslationBlockKind.DIALOGUE,
    AiTranslationBlockKind.NARRATION,
    AiTranslationBlockKind.SIGN -> sourceColumnGapDp.takeIf { it > 0f }?.coerceIn(1f, 3f) ?: 1f
    AiTranslationBlockKind.SFX,
    AiTranslationBlockKind.OTHER -> 1f
}

internal fun fitVerticalAiTranslationText(
    lines: List<String>,
    rectWidthDp: Float,
    rectHeightDp: Float,
    baseFontSizeSp: Float,
    kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER,
    columnGapDp: Float = 0f,
    sourceColumnWidthDp: Float = 0f,
    sourceColumnHeightDp: Float = 0f,
    sourceColumnCount: Int = 0
): AiVerticalTextLayout {
    val minimumFontSize = absoluteFitMinimumFontSize(kind)
    val sourceWidthFontSize = if (sourceColumnWidthDp > 0f && kind != AiTranslationBlockKind.SFX) {
        displayAiTranslationFontSizeSp(
            kind = kind,
            normalSizeSp = sourceColumnWidthToFontSizeSp(sourceColumnWidthDp),
            textDirection = AiTranslationTextDirection.VERTICAL
        )
    } else {
        0f
    }
    val fixedColumnWidthDp = sourceColumnWidthDp
        .takeIf { it > 0f && kind != AiTranslationBlockKind.SFX }
        ?: 0f
    val fitWidthDp = if (fixedColumnWidthDp > 0f) {
        Float.POSITIVE_INFINITY
    } else {
        verticalFitWidthDp(rectWidthDp, kind)
    }
    val naturalFitHeightDp = verticalFitHeightDp(rectHeightDp, kind)
    val fitHeightDp = if (sourceColumnHeightDp > 0f && kind != AiTranslationBlockKind.SFX) {
        sourceColumnHeightDp
    } else {
        naturalFitHeightDp
    }
    val targetBaseFontSizeSp = if (sourceWidthFontSize > 0f) {
        sourceWidthFontSize
    } else {
        baseFontSizeSp
    }
    val maximumFontSize = displayAiTranslationFontSizeSp(
        kind = kind,
        normalSizeSp = targetBaseFontSizeSp,
        textDirection = AiTranslationTextDirection.VERTICAL
    )
    var low = minimumFontSize
    var high = maximumFontSize.coerceAtLeast(minimumFontSize)
    var best = verticalTextLayout(lines, fitHeightDp, low, glyphSpacingMultiplier, kind, fixedColumnWidthDp)
    repeat(AI_TRANSLATION_FIT_ITERATIONS * 2) {
        val fontSize = (low + high) / 2f
        val layout = verticalTextLayout(lines, fitHeightDp, fontSize, glyphSpacingMultiplier, kind, fixedColumnWidthDp)
        if (verticalTextLayoutFits(layout, fitWidthDp, fitHeightDp, glyphSpacingMultiplier, columnGapDp)) {
            best = layout
            low = fontSize
        } else {
            high = fontSize
        }
    }
    val extraColumns = best.columns.size - sourceColumnCount
    if (sourceColumnCount <= 0 || extraColumns <= 0) return best
    val shrink = when (extraColumns) {
        1 -> 0.98f
        2 -> 0.96f
        else -> max(0.94f, 0.96f - (extraColumns - 2) * 0.01f)
    }
    val adjustedFontSize = (best.fontSizeSp * shrink).coerceAtLeast(minimumFontSize)
    val adjusted = verticalTextLayout(lines, fitHeightDp, adjustedFontSize, glyphSpacingMultiplier, kind, fixedColumnWidthDp)
    return if (verticalTextLayoutFits(adjusted, fitWidthDp, fitHeightDp, glyphSpacingMultiplier, columnGapDp)) {
        adjusted
    } else {
        best
    }
}

private fun verticalFitWidthDp(rectWidthDp: Float, kind: AiTranslationBlockKind): Float =
    rectWidthDp * when (kind) {
        AiTranslationBlockKind.SFX -> 0.98f
        AiTranslationBlockKind.SIGN,
        AiTranslationBlockKind.OTHER -> 0.96f
        AiTranslationBlockKind.DIALOGUE,
        AiTranslationBlockKind.NARRATION -> 0.94f
    }

private fun verticalFitHeightDp(rectHeightDp: Float, kind: AiTranslationBlockKind): Float =
    rectHeightDp * when (kind) {
        AiTranslationBlockKind.SFX -> 0.98f
        AiTranslationBlockKind.SIGN,
        AiTranslationBlockKind.OTHER -> 0.96f
        AiTranslationBlockKind.DIALOGUE,
        AiTranslationBlockKind.NARRATION -> 0.96f
    }

private fun verticalTextLayout(
    lines: List<String>,
    rectHeightDp: Float,
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float,
    kind: AiTranslationBlockKind,
    fixedColumnWidthDp: Float = 0f
): AiVerticalTextLayout {
    val charsPerColumn = verticalCharsPerColumn(rectHeightDp, fontSizeSp, glyphSpacingMultiplier)
    return AiVerticalTextLayout(
        fontSizeSp = fontSizeSp,
        charsPerColumn = charsPerColumn,
        columns = verticalTextColumnsForDisplay(lines, charsPerColumn, kind),
        columnWidthDp = fixedColumnWidthDp.takeIf { it > 0f } ?: verticalColumnWidthDp(fontSizeSp).value
    )
}

private fun verticalTextLayoutFits(
    layout: AiVerticalTextLayout,
    rectWidthDp: Float,
    rectHeightDp: Float,
    glyphSpacingMultiplier: Float,
    columnGapDp: Float
): Boolean {
    val width = verticalTextLayoutWidthDp(layout.columnWidthDp, layout.columns.size, columnGapDp)
    val height = verticalTextLayoutHeightDp(
        maxColumnLength = layout.columns.maxOfOrNull { it.length } ?: 0,
        fontSizeSp = layout.fontSizeSp,
        glyphSpacingMultiplier = glyphSpacingMultiplier
    )
    return width <= rectWidthDp && height <= rectHeightDp
}

internal fun verticalTextLayoutWidthDp(
    columnWidthDp: Float,
    columnCount: Int,
    columnGapDp: Float = 0f
): Float {
    if (columnCount <= 0) return 0f
    return columnCount * columnWidthDp + (columnCount - 1) * columnGapDp
}

internal fun verticalTextLayoutHeightDp(
    maxColumnLength: Int,
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Float {
    if (maxColumnLength <= 0) return 0f
    val glyphAdvance = safeVerticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).value
    val glyphHeight = fontSizeSp
    return glyphAdvance * (maxColumnLength - 1) + glyphHeight
}

internal fun fitHorizontalAiTranslationText(
    lines: List<String>,
    rectWidthDp: Float,
    rectHeightDp: Float,
    baseFontSizeSp: Float,
    kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE,
    lineGapDp: Float = 0f
): AiHorizontalTextLayout {
    var fontSize = baseFontSizeSp.coerceAtLeast(absoluteFitMinimumFontSize(kind))
    var layout = horizontalTextLayout(lines, rectWidthDp, fontSize)
    repeat(AI_TRANSLATION_FIT_ITERATIONS) {
        val width = horizontalTextLayoutWidthDp(layout.lines, layout.fontSizeSp)
        val height = horizontalTextLayoutHeightDp(layout.lines.size, layout.fontSizeSp, lineGapDp)
        val widthScale = if (width > rectWidthDp && width > 0f) rectWidthDp / width else 1f
        val heightScale = if (height > rectHeightDp && height > 0f) rectHeightDp / height else 1f
        val fitScale = min(widthScale, heightScale)
        if (fitScale >= 0.995f) return layout
        fontSize = (layout.fontSizeSp * fitScale * 0.98f)
            .coerceAtLeast(absoluteFitMinimumFontSize(kind))
        layout = horizontalTextLayout(lines, rectWidthDp, fontSize)
    }
    return layout
}

private fun horizontalTextLayout(
    lines: List<String>,
    rectWidthDp: Float,
    fontSizeSp: Float
): AiHorizontalTextLayout {
    val maxLineWidth = preferredHorizontalLineWidthDp(Dp(rectWidthDp), fontSizeSp)
    return AiHorizontalTextLayout(
        fontSizeSp = fontSizeSp,
        lines = balancedHorizontalLines(lines, maxLineWidth.value, fontSizeSp),
        maxLineWidth = maxLineWidth
    )
}

internal fun horizontalTextLayoutWidthDp(lines: List<String>, fontSizeSp: Float): Float =
    lines.maxOfOrNull { visualTextUnits(it) * fontSizeSp * AI_TRANSLATION_HORIZONTAL_CHAR_WIDTH_MULTIPLIER } ?: 0f

internal fun horizontalTextLayoutHeightDp(lineCount: Int, fontSizeSp: Float, lineGapDp: Float = 0f): Float {
    if (lineCount <= 0) return 0f
    return lineCount * fontSizeSp * max(1f, AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER) + (lineCount - 1) * lineGapDp
}

private fun absoluteFitMinimumFontSize(kind: AiTranslationBlockKind): Float =
    if (kind == AiTranslationBlockKind.SFX) AI_TRANSLATION_SFX_ABSOLUTE_FIT_MIN_FONT_SP else AI_TRANSLATION_ABSOLUTE_FIT_MIN_FONT_SP

internal fun displayAiTranslationFontSizeSp(
    kind: AiTranslationBlockKind,
    normalSizeSp: Float,
    textDirection: AiTranslationTextDirection
): Float {
    return when (kind) {
        AiTranslationBlockKind.SFX -> {
            val maximumSfxSize = if (textDirection == AiTranslationTextDirection.VERTICAL) 12f else 11f
            minOf(normalSizeSp * 0.55f, maximumSfxSize).coerceAtLeast(6.0f)
        }
        AiTranslationBlockKind.SIGN -> {
            if (textDirection == AiTranslationTextDirection.VERTICAL) {
                normalSizeSp.coerceAtLeast(7.2f)
            } else {
                minOf(normalSizeSp, 22f).coerceAtLeast(7.2f)
            }
        }
        AiTranslationBlockKind.DIALOGUE,
        AiTranslationBlockKind.NARRATION -> {
            if (textDirection == AiTranslationTextDirection.VERTICAL) {
                normalSizeSp
            } else {
                minOf(normalSizeSp, 28f)
            }
        }
        AiTranslationBlockKind.OTHER -> normalSizeSp
    }
}

internal fun verticalCharsPerColumn(
    heightDp: Float,
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Int {
    return ((heightDp - 2f) / safeVerticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).value).toInt().coerceAtLeast(1)
}

internal fun verticalGlyphAdvanceDp(
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Dp = Dp(fontSizeSp * glyphSpacingMultiplier)

internal fun safeVerticalGlyphAdvanceDp(
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Dp = Dp(fontSizeSp * glyphSpacingMultiplier.coerceIn(AI_TRANSLATION_MIN_VERTICAL_GLYPH_SPACING_MULTIPLIER, 1.30f))

internal fun aiVerticalGlyphSpacingMultiplier(percent: Int): Float = percent.coerceIn(70, 130) / 100f

internal fun verticalGlyphPlacementAdvancePx(requestedAdvancePx: Int, tallestGlyphPx: Int): Int {
    val minimumAdvancePx = (tallestGlyphPx * AI_TRANSLATION_MIN_VERTICAL_GLYPH_SPACING_MULTIPLIER).roundToInt()
    return max(requestedAdvancePx, minimumAdvancePx).coerceAtLeast(1)
}

@Suppress("UNUSED_PARAMETER")
internal fun verticalTextTopOffsetDp(fontSizeSp: Float): Dp = Dp(0f)

private fun preferredHorizontalLineWidthDp(width: Dp, fontSizeSp: Float): Dp =
    Dp((width.value * 0.98f).coerceAtLeast(fontSizeSp * 0.8f))

internal fun verticalColumnWidthDp(fontSizeSp: Float): Dp =
    Dp(fontSizeSp * AI_TRANSLATION_VERTICAL_COLUMN_WIDTH_MULTIPLIER)

private fun sourceColumnWidthToFontSizeSp(sourceColumnWidthDp: Float): Float =
    (sourceColumnWidthDp - AI_TRANSLATION_VERTICAL_TEXT_COLUMN_HORIZONTAL_PADDING_DP * 2f)
        .coerceAtLeast(AI_TRANSLATION_ABSOLUTE_FIT_MIN_FONT_SP)

internal fun balancedHorizontalLines(
    lines: List<String>,
    widthDp: Float,
    fontSizeSp: Float
): List<String> {
    val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
    val charsPerLine = (widthDp / (fontSizeSp * AI_TRANSLATION_HORIZONTAL_CHAR_WIDTH_MULTIPLIER)).toInt().coerceAtLeast(1)
    if (clean.size <= 1) return clean.flatMap { wrapTranslatedLine(it, charsPerLine) }
    val shortLineThreshold = (charsPerLine * 0.45f).toInt().coerceAtLeast(4)
    val merged = mutableListOf<String>()
    var buffer = ""
    clean.forEach { line ->
        val candidate = if (buffer.isBlank()) line else joinTranslatedLine(buffer, line)
        if (buffer.isBlank()) {
            buffer = line
        } else if (
            visualTextUnits(buffer) < shortLineThreshold ||
            visualTextUnits(line) <= 3f ||
            visualTextUnits(candidate) <= charsPerLine
        ) {
            buffer = candidate
        } else {
            merged += buffer
            buffer = line
        }
    }
    if (buffer.isNotBlank()) merged += buffer
    return merged.flatMap { wrapTranslatedLine(it, charsPerLine) }
}

private fun joinTranslatedLine(left: String, right: String): String =
    if (left.lastOrNull()?.isLetterOrDigit() == true && right.firstOrNull()?.isLetterOrDigit() == true) {
        "$left $right"
    } else {
        left + right
    }

private fun wrapTranslatedLine(
    line: String,
    charsPerLine: Int,
    mergeShortTail: Boolean = false
): List<String> {
    val clean = line.trim()
    if (clean.length <= charsPerLine) return listOf(clean)
    val chunks = if (clean.any { it.isWhitespace() }) {
        wrapTranslatedWords(clean, charsPerLine)
    } else {
        clean.chunked(charsPerLine)
    }.attachDanglingPunctuationToPrevious()
    return if (mergeShortTail) chunks.mergeShortTrailingChunk(charsPerLine) else chunks
}

private fun wrapTranslatedWords(line: String, charsPerLine: Int): List<String> {
    val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (current.isBlank() || visualTextUnits(candidate) <= charsPerLine) {
            current = candidate
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    return lines.flatMap { value ->
        if (visualTextUnits(value) <= charsPerLine || value.contains(" ")) {
            listOf(value)
        } else {
            value.chunked(charsPerLine)
        }
    }
}

private fun visualTextUnits(value: String): Float =
    value.sumOf { char ->
        when {
            char.isWhitespace() -> 1.0
            char.isHangulSyllable() -> 1.0
            char.code in 0x3040..0x30FF -> 1.0
            char.code in 0x4E00..0x9FFF -> 1.0
            else -> 0.58
        }
    }.toFloat()

private fun Char.isHangulSyllable(): Boolean = code in 0xAC00..0xD7AF

internal fun verticalTextColumnsForDisplay(
    lines: List<String>,
    charsPerColumn: Int,
    kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE
): List<String> {
    val cleanLines = lines.map { it.trim() }.filter { it.isNotBlank() }
    val sourceLines = if (kind.preservesAiVerticalLineBreaks()) {
        cleanLines
    } else {
        listOf(cleanLines.joinToString(separator = ""))
    }
    return sourceLines
        .map { toVerticalText(it) }
        .flatMap { line ->
            balancedVerticalChunks(line, charsPerColumn)
        }
        .asReversed()
}

private fun AiTranslationBlockKind.preservesAiVerticalLineBreaks(): Boolean =
    this == AiTranslationBlockKind.SIGN || this == AiTranslationBlockKind.OTHER

private fun balancedVerticalChunks(line: String, charsPerColumn: Int): List<String> {
    val clean = line.trim()
    if (clean.isBlank()) return emptyList()
    if (clean.length <= charsPerColumn) return listOf(clean)
    val columnCount = ((clean.length + charsPerColumn - 1) / charsPerColumn).coerceAtLeast(1)
    val targetChars = ((clean.length + columnCount - 1) / columnCount).coerceAtLeast(1)
    return wrapTranslatedLine(clean, targetChars, mergeShortTail = true)
}

private fun List<String>.attachDanglingPunctuationToPrevious(): List<String> {
    val merged = mutableListOf<String>()
    forEach { chunk ->
        var rest = chunk
        while (merged.isNotEmpty() && rest.isNotEmpty() && rest.first().attachesToPreviousText()) {
            merged[merged.lastIndex] = merged.last() + rest.first()
            rest = rest.drop(1)
        }
        if (rest.isNotEmpty()) merged += rest
    }
    return merged
}

private fun List<String>.mergeShortTrailingChunk(charsPerLine: Int): List<String> {
    if (size < 2) return this
    val last = last()
    val previous = this[size - 2]
    val allowedOverflow = 2
    if (last.length > 2 || previous.length + last.length > charsPerLine + allowedOverflow) return this
    return dropLast(2) + (previous + last)
}

private fun Char.attachesToPreviousText(): Boolean = this in AI_TRANSLATION_TRAILING_PUNCTUATION

private fun AiTranslationRect.effectiveOrNull(): AiTranslationRect? =
    takeIf { width > 0f && height > 0f }

private fun List<AiTranslationRect>.boundingRectOrNull(): AiTranslationRect? {
    val valid = filter { it.width > 0f && it.height > 0f }
    if (valid.isEmpty()) return null
    val left = valid.minOf { it.x }
    val top = valid.minOf { it.y }
    val right = valid.maxOf { it.right }
    val bottom = valid.maxOf { it.bottom }
    return AiTranslationRect(
        x = left,
        y = top,
        width = (right - left).coerceAtLeast(0f),
        height = (bottom - top).coerceAtLeast(0f)
    )
}

private fun AiTranslationRect.expandNormalized(horizontalPadding: Float, verticalPadding: Float): AiTranslationRect {
    val left = (x - horizontalPadding).coerceAtLeast(0f)
    val top = (y - verticalPadding).coerceAtLeast(0f)
    val expandedRight = (this.right + horizontalPadding).coerceAtMost(1f)
    val expandedBottom = (this.bottom + verticalPadding).coerceAtMost(1f)
    return AiTranslationRect(
        x = left,
        y = top,
        width = (expandedRight - left).coerceIn(0f, 1f - left),
        height = (expandedBottom - top).coerceIn(0f, 1f - top)
    )
}

private val AiTranslationRect.right: Float
    get() = x + width

private val AiTranslationRect.bottom: Float
    get() = y + height

private data class AiImageContentBounds(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp
)

private fun imageContentBounds(
    containerWidth: Dp,
    containerHeight: Dp,
    imageWidth: Int,
    imageHeight: Int,
    fillWidth: Boolean
): AiImageContentBounds {
    if (imageWidth <= 0 || imageHeight <= 0) {
        return AiImageContentBounds(0.dp, 0.dp, containerWidth, containerHeight)
    }

    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    if (fillWidth) {
        val drawnHeight = Dp(containerWidth.value / imageAspect)
        return AiImageContentBounds(
            x = 0.dp,
            y = Dp((containerHeight.value - drawnHeight.value) / 2f),
            width = containerWidth,
            height = drawnHeight
        )
    }

    val containerAspect = containerWidth.value / containerHeight.value
    return if (imageAspect > containerAspect) {
        val drawnHeight = Dp(containerWidth.value / imageAspect)
        AiImageContentBounds(
            x = 0.dp,
            y = Dp((containerHeight.value - drawnHeight.value) / 2f),
            width = containerWidth,
            height = drawnHeight
        )
    } else {
        val drawnWidth = Dp(containerHeight.value * imageAspect)
        AiImageContentBounds(
            x = Dp((containerWidth.value - drawnWidth.value) / 2f),
            y = 0.dp,
            width = drawnWidth,
            height = containerHeight
        )
    }
}

fun readerAiStatusStringRes(status: AiTranslationPageStatus?): Int =
    when (status) {
        AiTranslationPageStatus.RUNNING -> R.string.reader_ai_status_running
        AiTranslationPageStatus.DONE -> R.string.reader_ai_status_done
        AiTranslationPageStatus.FAILED -> R.string.reader_ai_status_failed
        else -> R.string.reader_ai_status_pending
    }

internal fun readerAiTranslationProgressText(page: AiTranslatedPage?): String? {
    if (page?.status != AiTranslationPageStatus.RUNNING) return null
    val total = page.blocks.size
    if (total <= 0) return null
    val translated = page.blocks.count { block -> block.regionStatus == AiTranslationRegionStatus.DONE }
    return "$translated/$total"
}

private fun parseAiColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.White)

private const val AI_TRANSLATION_PLACEHOLDER_ALPHA = 0.22f
private const val AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER = 0.96f
private const val AI_TRANSLATION_HORIZONTAL_CHAR_WIDTH_MULTIPLIER = 1.0f
private const val AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER = 0.92f
private const val AI_TRANSLATION_MAX_VERTICAL_FONT_SP = 24f
private const val AI_TRANSLATION_VERTICAL_COLUMN_WIDTH_MULTIPLIER = 1.18f
private const val AI_TRANSLATION_VERTICAL_TEXT_COLUMN_HORIZONTAL_PADDING_DP = 0.5f
private const val AI_TRANSLATION_SOURCE_TEXT_MASK_PADDING_DP = 2f
private const val AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_PADDING_DP = 6f
private const val AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_HORIZONTAL_EXPANSION = 0.12f
private const val AI_TRANSLATION_HIGH_ACCURACY_SOURCE_MASK_VERTICAL_EXPANSION = 0.18f
private const val AI_TRANSLATION_BUBBLE_MASK_OUTLINE_EXPANSION_RATIO = 0.03f
private const val AI_TRANSLATION_MAX_SOLID_BUBBLE_FILL_AREA = 0.08f
private const val AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_TARGET_CHAR_DP = 8f
private const val AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_PADDING_DP = 4f
private const val AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_MIN_EXPANSION = 1.35f
private const val AI_TRANSLATION_NON_BUBBLE_HORIZONTAL_MAX_WIDTH = 0.94f
private const val AI_TRANSLATION_VERTICAL_BUBBLE_INTERIOR_HORIZONTAL_INSET_RATIO = 0.08f
private const val AI_TRANSLATION_VERTICAL_BUBBLE_INTERIOR_VERTICAL_INSET_RATIO = 0.08f
private const val AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER = 0.86f
private const val AI_TRANSLATION_MIN_VERTICAL_GLYPH_SPACING_MULTIPLIER = 0.78f
private const val AI_TRANSLATION_ABSOLUTE_FIT_MIN_FONT_SP = 5.2f
private const val AI_TRANSLATION_HIGH_ACCURACY_TEXT_SAFETY_PADDING_DP = 2f
private const val AI_TRANSLATION_SFX_ABSOLUTE_FIT_MIN_FONT_SP = 4.8f
private const val AI_TRANSLATION_FIT_ITERATIONS = 8
private const val AI_TRANSLATION_MIN_OVERLAP_GAP = 0.0005f
private const val AI_TRANSLATION_MAX_OVERLAP_SHIFT = 0.06f
private const val AI_TRANSLATION_OVERLAP_SHIFT_ATTEMPTS = 12
private const val AI_TRANSLATION_TRAILING_PUNCTUATION = "。！？!?，,、；;：:…︙︱｜︐︒︑︓︔﹂﹄」』)）"
