package fail.tiger.komgarot.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
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
        val bounds = imageContentBounds(
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            imageWidth = page.imageWidth,
            imageHeight = page.imageHeight,
            fillWidth = fillWidth
        )
        page.blocks.forEach { block ->
            val safe = block.renderSafe()
            val hasTranslatedText = safe.translatedLines.any { it.isNotBlank() }
            val placement = safe.translationRect.effectiveOrNull() ?: safe.rect
            val blockWidth = bounds.width * placement.width.coerceIn(0f, 1f)
            val blockHeight = bounds.height * placement.height.coerceIn(0f, 1f)
            val fittedFontSizeSp = aiTranslationFontSizeSp(
                baseScale = safe.fontScale,
                rectWidthDp = blockWidth.value,
                rectHeightDp = blockHeight.value,
                textDirection = safe.textDirection,
                lineCount = safe.translatedLines.size.coerceAtLeast(1),
                textLength = safe.translatedLines.sumOf { it.length }.coerceAtLeast(1)
            )
            Box(
                Modifier
                    .offset(
                        x = bounds.x + bounds.width * placement.x.coerceIn(0f, 1f),
                        y = bounds.y + bounds.height * placement.y.coerceIn(0f, 1f)
                    )
                    .width(blockWidth)
                    .heightIn(min = blockHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                if (!hasTranslatedText) {
                    AiTranslationRegionPlaceholder(
                        placeholderColor = parseAiColor(safe.textColor),
                        cornerRadius = safe.cornerRadius,
                        placeholderAlpha = AI_TRANSLATION_PLACEHOLDER_ALPHA,
                        modifier = Modifier
                            .width(blockWidth)
                            .height(blockHeight)
                    )
                } else {
                    val usesSolidTextBoxMask = safe.kind.usesSolidAiTranslationMask()
                    val translatedTextBackgroundColor = if (usesSolidTextBoxMask) {
                        Color.Transparent
                    } else {
                        parseAiColor(safe.maskColor).copy(alpha = safe.maskAlpha)
                    }
                    val inlineTextPadding = if (usesSolidTextBoxMask) 0.dp else 0.5.dp
                    val horizontalLinePadding = if (usesSolidTextBoxMask) 0.dp else 1.dp
                    val textGroupGap = if (usesSolidTextBoxMask) 0.dp else 1.dp
                    if (usesSolidTextBoxMask) {
                        AiTranslationSourceTextMask(
                            maskColor = parseAiColor(safe.maskColor),
                            cornerRadius = safe.cornerRadius,
                            maskAlpha = normalAiTranslationMaskAlpha(safe.maskAlpha),
                            modifier = Modifier
                                .width(blockWidth)
                                .height(blockHeight)
                        )
                    }
                    if (safe.textDirection == AiTranslationTextDirection.VERTICAL) {
                        val charsPerColumn = verticalCharsPerColumn(blockHeight.value, fittedFontSizeSp, verticalGlyphSpacingMultiplier)
                        val columnWidth = verticalColumnWidthDp(fittedFontSizeSp)
                        val verticalColumns = verticalTextColumnsForDisplay(safe.translatedLines, charsPerColumn)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(textGroupGap),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .offset(y = verticalTextTopOffsetDp(fittedFontSizeSp))
                                .wrapContentSize(unbounded = true)
                                .normalTextBoxMask(
                                    enabled = usesSolidTextBoxMask,
                                    orientation = TextBackgroundOrientation.VERTICAL,
                                    color = parseAiColor(safe.maskColor).copy(alpha = normalAiTranslationMaskAlpha(safe.maskAlpha)),
                                    cornerRadius = safe.cornerRadius
                                )
                        ) {
                            verticalColumns.forEach { column ->
                                VerticalTextColumnBackground(
                                    text = column,
                                    textColor = parseAiColor(safe.textColor),
                                    backgroundColor = translatedTextBackgroundColor,
                                    cornerRadius = safe.cornerRadius,
                                    fontSizeSp = fittedFontSizeSp,
                                    lineHeightMultiplier = AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER,
                                    glyphSpacingMultiplier = verticalGlyphSpacingMultiplier,
                                    columnWidth = columnWidth,
                                    horizontalPadding = inlineTextPadding,
                                    verticalPadding = inlineTextPadding
                                )
                            }
                        }
                    } else {
                        val maxLineWidth = preferredHorizontalLineWidthDp(blockWidth, fittedFontSizeSp)
                        val displayLines = balancedHorizontalLines(
                            lines = safe.translatedLines,
                            widthDp = maxLineWidth.value,
                            fontSizeSp = fittedFontSizeSp
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(textGroupGap),
                            modifier = Modifier
                                .wrapContentHeight(unbounded = true)
                                .normalTextBoxMask(
                                    enabled = usesSolidTextBoxMask,
                                    orientation = TextBackgroundOrientation.HORIZONTAL,
                                    color = parseAiColor(safe.maskColor).copy(alpha = normalAiTranslationMaskAlpha(safe.maskAlpha)),
                                    cornerRadius = safe.cornerRadius
                                )
                        ) {
                            displayLines.forEach { line ->
                                HorizontalTextLineBackground(
                                    text = line,
                                    textColor = parseAiColor(safe.textColor),
                                    backgroundColor = translatedTextBackgroundColor,
                                    cornerRadius = safe.cornerRadius,
                                    fontSizeSp = fittedFontSizeSp,
                                    lineHeightMultiplier = AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER,
                                    maxWidth = maxLineWidth,
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

internal fun normalAiTranslationMaskAlpha(savedAlpha: Float): Float =
    maxOf(savedAlpha, AI_TRANSLATION_NORMAL_TEXT_MASK_ALPHA).coerceIn(0.82f, 0.90f)

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
        val glyphAdvancePx = verticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).roundToPx().coerceAtLeast(1)
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val tallestGlyph = placeables.maxOfOrNull { it.height } ?: 0
        val widestGlyph = placeables.maxOfOrNull { it.width } ?: 0
        val naturalHeight = if (placeables.isEmpty()) 0 else glyphAdvancePx * (placeables.size - 1) + tallestGlyph
        val width = widestGlyph.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = naturalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val x = (width - placeable.width) / 2
                placeable.placeRelative(x = x, y = index * glyphAdvancePx)
            }
        }
    }
}

@Composable
private fun aiTranslationTextStyle(fontSizeSp: Float, lineHeightMultiplier: Float): TextStyle {
    val base = MaterialTheme.typography.bodySmall
    return base.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.SemiBold,
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
    textLength: Int
): Float {
    val safeTextLength = textLength.coerceAtLeast(1)
    val safeLineCount = lineCount.coerceAtLeast(1)
    val sizeFromBox = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> rectWidthDp * 0.74f
        AiTranslationTextDirection.HORIZONTAL -> (rectHeightDp / safeLineCount) * 0.86f
        AiTranslationTextDirection.AUTO -> minOf(rectWidthDp, rectHeightDp) * 0.62f
    }
    val sizeFromTextHeight = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> rectHeightDp / maxOf(1.4f, safeTextLength * 0.76f)
        AiTranslationTextDirection.HORIZONTAL -> rectHeightDp / (safeLineCount * 0.92f)
        AiTranslationTextDirection.AUTO -> rectHeightDp / maxOf(1.6f, safeLineCount * 1.1f)
    }
    val sizeFromTextArea = sqrt((rectWidthDp * rectHeightDp) / maxOf(1f, safeTextLength * 0.72f))
    val rawSize = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> minOf(sizeFromBox, sizeFromTextHeight, sizeFromTextArea * 1.22f)
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> minOf(sizeFromBox, sizeFromTextHeight, sizeFromTextArea * 1.18f)
    }
    val minimumReadableSize = if (textDirection == AiTranslationTextDirection.VERTICAL) 8.6f else 8.2f
    val maximumReadableSize = if (textDirection == AiTranslationTextDirection.VERTICAL) 28f else 28f
    return (rawSize * baseScale).coerceIn(minimumReadableSize, maximumReadableSize)
}

internal fun verticalCharsPerColumn(
    heightDp: Float,
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Int {
    return ((heightDp - 2f) / verticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).value).toInt().coerceAtLeast(1)
}

internal fun verticalGlyphAdvanceDp(
    fontSizeSp: Float,
    glyphSpacingMultiplier: Float = AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER
): Dp = Dp(fontSizeSp * glyphSpacingMultiplier)

internal fun aiVerticalGlyphSpacingMultiplier(percent: Int): Float = percent.coerceIn(70, 130) / 100f

internal fun verticalTextTopOffsetDp(fontSizeSp: Float): Dp =
    Dp((-fontSizeSp * 0.16f).coerceAtMost(-1.2f))

private fun preferredHorizontalLineWidthDp(width: Dp, fontSizeSp: Float): Dp =
    Dp((width.value * 0.98f).coerceAtLeast(fontSizeSp * 3.2f))

internal fun verticalColumnWidthDp(fontSizeSp: Float): Dp =
    Dp((fontSizeSp * 1.18f).coerceIn(11f, 44f))

internal fun balancedHorizontalLines(
    lines: List<String>,
    widthDp: Float,
    fontSizeSp: Float
): List<String> {
    val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
    val charsPerLine = (widthDp / (fontSizeSp * 0.58f)).toInt().coerceAtLeast(5)
    if (clean.size <= 1) return clean.flatMap { wrapTranslatedLine(it, charsPerLine) }
    val shortLineThreshold = (charsPerLine * 0.45f).toInt().coerceAtLeast(4)
    val merged = mutableListOf<String>()
    var buffer = ""
    clean.forEach { line ->
        val candidate = if (buffer.isBlank()) line else joinTranslatedLine(buffer, line)
        if (buffer.isBlank()) {
            buffer = line
        } else if (buffer.length < shortLineThreshold || line.length <= 3 || candidate.length <= charsPerLine) {
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
    if (line.length <= charsPerLine) return listOf(line)
    val chunks = line.chunked(charsPerLine).attachDanglingPunctuationToPrevious()
    return if (mergeShortTail) chunks.mergeShortTrailingChunk(charsPerLine) else chunks
}

internal fun verticalTextColumnsForDisplay(lines: List<String>, charsPerColumn: Int): List<String> =
    lines
        .map { toVerticalText(it) }
        .flatMap { line ->
            wrapTranslatedLine(line, charsPerColumn, mergeShortTail = true)
        }
        .asReversed()

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiTranslationFloatingButton(
    mode: AiTranslationDisplayMode,
    pageStatus: AiTranslationPageStatus?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = mode == AiTranslationDisplayMode.ON
    val running = pageStatus == AiTranslationPageStatus.RUNNING
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = if (active || running) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (active || running) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = stringResource(R.string.reader_ai_translation),
                modifier = Modifier.alpha(if (active || running) 1f else 0.72f)
            )
            Text(
                text = stringResource(R.string.reader_ai_translation_icon_text),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.alpha(0f)
            )
        }
    }
}

fun readerAiStatusStringRes(status: AiTranslationPageStatus?): Int =
    when (status) {
        AiTranslationPageStatus.RUNNING -> R.string.reader_ai_status_running
        AiTranslationPageStatus.DONE -> R.string.reader_ai_status_done
        AiTranslationPageStatus.FAILED -> R.string.reader_ai_status_failed
        else -> R.string.reader_ai_status_pending
    }

fun readerAiModeShortStringRes(mode: String?): Int =
    R.string.ai_translation_mode_local_detection_short

private fun parseAiColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.White)

private const val AI_TRANSLATION_PLACEHOLDER_ALPHA = 0.22f
private const val AI_TRANSLATION_NORMAL_TEXT_MASK_ALPHA = 0.86f
private const val AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER = 0.96f
private const val AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER = 0.92f
private const val AI_TRANSLATION_DEFAULT_VERTICAL_GLYPH_SPACING_MULTIPLIER = 0.92f
private const val AI_TRANSLATION_TRAILING_PUNCTUATION = "。！？!?，,、；;：:…︙︱｜︐︒︑︓︔﹂﹄」』)）"
