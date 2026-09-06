package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import kotlin.math.max
import kotlin.math.min

internal fun groupLocalTextRegionsByBubbles(
    regions: List<AiTranslationLocalTextRegion>,
    bubbles: List<*>
): List<AiTranslationLocalTextRegion> {
    val bubbleRegions = bubbles.mapNotNull { bubble ->
        when (bubble) {
            is AiBubbleRegion -> bubble
            is AiTranslationRect -> AiBubbleRegion(rect = bubble, safeTextRect = bubble.insetForText())
            else -> null
        }
    }
    if (bubbleRegions.isEmpty()) return regions
    val bubbleByRegionIndex = regions.map { region ->
        bubbleRegions.indices
            .filter { index -> bubbleRegions[index].rect.containsRegionCenterOrMost(region.effectiveTextBounds()) }
            .minByOrNull { index -> bubbleRegions[index].rect.area() }
    }
    val membersByBubble = bubbleByRegionIndex
        .mapIndexedNotNull { regionIndex, bubbleIndex -> bubbleIndex?.let { it to regionIndex } }
        .groupBy({ it.first }, { it.second })
    val coveredBubbleIndexes = regions.flatMap { region ->
        bubbleRegions.indices.filter { index ->
            bubbleRegions[index].rect.containsRegionCenterOrMost(region.effectiveTextBounds())
        }
    }.toSet()
    val emittedBubbles = mutableSetOf<Int>()
    return buildList {
        regions.indices.forEach { regionIndex ->
            val bubbleIndex = bubbleByRegionIndex[regionIndex]
            if (bubbleIndex == null) {
                regions[regionIndex]
                    .takeUnless(AiTranslationLocalTextRegion::isBroadUnanchoredDetection)
                    ?.let(::add)
            } else if (emittedBubbles.add(bubbleIndex)) {
                val bubbleMembers = membersByBubble.getValue(bubbleIndex).map(regions::get)
                val sharedFontScale = bubbleMembers.map { it.estimatedFontScale }.sorted()
                    .let { it[it.size / 2] }
                val bubble = bubbleRegions[bubbleIndex]
                add(
                    mergeBubbleTextRegions(
                        bubbleIndex = bubbleIndex,
                        renderBounds = bubble.safeTextRect,
                        aiCropBounds = bubble.rect,
                        bubbleOutline = bubble.outline,
                        bubbleSolidFill = bubble.solidFill,
                        bubbleBackgroundColor = bubble.backgroundColor,
                        members = bubbleMembers,
                        sharedFontScale = sharedFontScale
                    )
                )
            }
        }
        bubbleRegions.indices
            .filterNot(coveredBubbleIndexes::contains)
            .forEach { bubbleIndex ->
                add(bubbleRegions[bubbleIndex].toFallbackTextRegion(bubbleIndex))
            }
    }
}

private fun AiBubbleRegion.toFallbackTextRegion(bubbleIndex: Int): AiTranslationLocalTextRegion {
    val fallbackBounds = safeTextRect.takeIf { it.width > 0f && it.height > 0f } ?: rect
    val direction = if (fallbackBounds.height > fallbackBounds.width * 1.15f) {
        AiTranslationTextDirection.VERTICAL
    } else {
        AiTranslationTextDirection.HORIZONTAL
    }
    return AiTranslationLocalTextRegion(
        id = "bubble-$bubbleIndex-fallback",
        rect = fallbackBounds,
        textDirection = direction,
        textColor = ensureReadableAiTextColor("#111111", backgroundColor),
        backgroundColor = backgroundColor,
        confidence = FALLBACK_BUBBLE_REGION_CONFIDENCE,
        estimatedFontScale = 1f,
        textBounds = fallbackBounds,
        renderBounds = fallbackBounds,
        aiCropBounds = rect,
        bubbleOutline = outline,
        bubbleSolidFill = solidFill
    )
}

private fun mergeBubbleTextRegions(
    bubbleIndex: Int,
    renderBounds: AiTranslationRect,
    aiCropBounds: AiTranslationRect,
    bubbleOutline: List<fail.tiger.komgarot.data.local.AiTranslationPoint>,
    bubbleSolidFill: Boolean,
    bubbleBackgroundColor: String,
    members: List<AiTranslationLocalTextRegion>,
    sharedFontScale: Float
): AiTranslationLocalTextRegion {
    val sourceColumns = members.flatMap(AiTranslationLocalTextRegion::effectiveSourceColumns)
    val textBounds = sourceColumns.boundingRect()
    val styleSource = members.maxByOrNull { it.confidence } ?: members.first()
    val textDirection = mergedBubbleTextDirection(members, renderBounds, styleSource.textDirection)
    return styleSource.copy(
        id = "bubble-$bubbleIndex-${members.joinToString("+") { it.id }}",
        rect = textBounds,
        confidence = members.map { it.confidence }.average().toFloat().coerceIn(0f, 1f),
        estimatedFontScale = sharedFontScale,
        textDirection = textDirection,
        rotationDegrees = if (members.all { it.rotationDegrees == styleSource.rotationDegrees }) {
            styleSource.rotationDegrees
        } else {
            0f
        },
        textBounds = textBounds,
        renderBounds = renderBounds,
        aiCropBounds = aiCropBounds,
        sourceColumns = sourceColumns,
        bubbleOutline = bubbleOutline,
        bubbleSolidFill = bubbleSolidFill,
        backgroundColor = bubbleBackgroundColor
    )
}

private fun mergedBubbleTextDirection(
    members: List<AiTranslationLocalTextRegion>,
    renderBounds: AiTranslationRect,
    fallback: AiTranslationTextDirection
): AiTranslationTextDirection {
    val verticalCount = members.count { it.textDirection == AiTranslationTextDirection.VERTICAL }
    val horizontalCount = members.count { it.textDirection == AiTranslationTextDirection.HORIZONTAL }
    return when {
        verticalCount > horizontalCount -> AiTranslationTextDirection.VERTICAL
        horizontalCount > verticalCount -> AiTranslationTextDirection.HORIZONTAL
        verticalCount > 0 && renderBounds.height > renderBounds.width * 1.15f ->
            AiTranslationTextDirection.VERTICAL
        horizontalCount > 0 -> AiTranslationTextDirection.HORIZONTAL
        else -> fallback
    }
}

private fun AiTranslationRect.insetForText(): AiTranslationRect {
    val horizontalInset = width * BUBBLE_TEXT_HORIZONTAL_INSET_RATIO
    val verticalInset = height * BUBBLE_TEXT_VERTICAL_INSET_RATIO
    return AiTranslationRect(
        x = x + horizontalInset,
        y = y + verticalInset,
        width = (width - horizontalInset * 2f).coerceAtLeast(0f),
        height = (height - verticalInset * 2f).coerceAtLeast(0f)
    )
}

private fun AiTranslationRect.containsRegionCenterOrMost(region: AiTranslationRect): Boolean {
    val centerX = region.centerX()
    val centerY = region.centerY()
    if (centerX in x..(x + width) && centerY in y..(y + height)) return true
    val overlapWidth = (min(x + width, region.x + region.width) - max(x, region.x)).coerceAtLeast(0f)
    val overlapHeight = (min(y + height, region.y + region.height) - max(y, region.y)).coerceAtLeast(0f)
    return overlapWidth * overlapHeight / region.area().coerceAtLeast(MIN_GEOMETRY_EXTENT) >= MIN_BUBBLE_OVERLAP_RATIO
}

private fun List<AiTranslationRect>.boundingRect(): AiTranslationRect {
    if (size == 1) return single()
    val left = minOf { it.x }
    val top = minOf { it.y }
    val right = maxOf { it.x + it.width }
    val bottom = maxOf { it.y + it.height }
    return AiTranslationRect(left, top, right - left, bottom - top)
}

private fun AiTranslationRect.centerX(): Float = x + width / 2f
private fun AiTranslationRect.centerY(): Float = y + height / 2f
private fun AiTranslationRect.area(): Float = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)

private fun AiTranslationLocalTextRegion.isBroadUnanchoredDetection(): Boolean {
    val bounds = effectiveTextBounds()
    return bounds.width >= BROAD_UNANCHORED_MIN_SPAN &&
        bounds.height >= BROAD_UNANCHORED_MIN_SPAN &&
        bounds.area() >= BROAD_UNANCHORED_MIN_AREA
}

private const val BUBBLE_TEXT_HORIZONTAL_INSET_RATIO = 0.12f
private const val BUBBLE_TEXT_VERTICAL_INSET_RATIO = 0.09f
private const val MIN_BUBBLE_OVERLAP_RATIO = 0.55f
private const val MIN_GEOMETRY_EXTENT = 0.000001f
private const val FALLBACK_BUBBLE_REGION_CONFIDENCE = 0.45f
private const val BROAD_UNANCHORED_MIN_SPAN = 0.18f
private const val BROAD_UNANCHORED_MIN_AREA = 0.045f
