package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiHorizontalTranslationPolicy
import fail.tiger.komgarot.data.local.AiTranslationPoint
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import kotlin.math.max
import kotlin.math.min

internal data class AiHorizontalDetectionTile(
    val index: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

internal fun shouldUseAiHorizontalDetectionTiles(
    policy: AiHorizontalTranslationPolicy,
    sourceWidth: Int,
    sourceHeight: Int,
    maxEdge: Int
): Boolean = policy == AiHorizontalTranslationPolicy.KOREAN_V1 &&
    sourceWidth > 0 &&
    sourceHeight > 0 &&
    maxEdge > 0 &&
    sourceHeight > maxEdge * HORIZONTAL_TILE_TRIGGER_EDGE_MULTIPLIER &&
    sourceHeight.toFloat() / sourceWidth.toFloat() >= HORIZONTAL_TILE_TRIGGER_ASPECT_RATIO

internal fun planAiHorizontalDetectionTiles(
    sourceWidth: Int,
    sourceHeight: Int,
    maxEdge: Int,
    overlapRatio: Float = HORIZONTAL_TILE_OVERLAP_RATIO
): List<AiHorizontalDetectionTile> {
    if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()
    val safeMaxEdge = maxEdge.coerceAtLeast(1)
    if (!shouldUseAiHorizontalDetectionTiles(
            policy = AiHorizontalTranslationPolicy.KOREAN_V1,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            maxEdge = safeMaxEdge
        )
    ) {
        return listOf(AiHorizontalDetectionTile(0, 0, 0, sourceWidth, sourceHeight))
    }
    val tileHeight = min(sourceHeight, safeMaxEdge)
    val overlap = max(
        HORIZONTAL_TILE_MIN_OVERLAP_PX,
        (tileHeight * overlapRatio.coerceIn(0.08f, 0.30f)).toInt()
    ).coerceAtMost((tileHeight - 1).coerceAtLeast(0))
    val step = (tileHeight - overlap).coerceAtLeast(1)
    val result = mutableListOf<AiHorizontalDetectionTile>()
    var top = 0
    while (top < sourceHeight) {
        val bottom = min(sourceHeight, top + tileHeight)
        result += AiHorizontalDetectionTile(
            index = result.size,
            left = 0,
            top = top,
            right = sourceWidth,
            bottom = bottom
        )
        if (bottom == sourceHeight) break
        top = min(top + step, sourceHeight - tileHeight)
    }
    return result
}

internal fun mapAiHorizontalDetectionTileRegion(
    region: AiTranslationLocalTextRegion,
    tile: AiHorizontalDetectionTile,
    pageWidth: Int,
    pageHeight: Int,
    id: String
): AiTranslationLocalTextRegion {
    if (pageWidth <= 0 || pageHeight <= 0 || tile.width <= 0 || tile.height <= 0) {
        return region.copy(id = id)
    }
    return region.copy(
        id = id,
        rect = mapAiHorizontalDetectionTileRect(region.rect, tile, pageWidth, pageHeight),
        textBounds = mapAiHorizontalDetectionTileRectOrEmpty(region.textBounds, tile, pageWidth, pageHeight),
        renderBounds = mapAiHorizontalDetectionTileRectOrEmpty(region.renderBounds, tile, pageWidth, pageHeight),
        aiCropBounds = mapAiHorizontalDetectionTileRectOrEmpty(region.aiCropBounds, tile, pageWidth, pageHeight),
        sourceColumns = region.sourceColumns.map {
            mapAiHorizontalDetectionTileRect(it, tile, pageWidth, pageHeight)
        },
        sourceLines = region.sourceLines.map {
            mapAiHorizontalDetectionTileRect(it, tile, pageWidth, pageHeight)
        },
        bubbleOutline = region.bubbleOutline.map { point ->
            mapAiHorizontalDetectionTilePoint(point, tile, pageWidth, pageHeight)
        }
    )
}

internal fun mapAiHorizontalDetectionTileBubble(
    bubble: AiBubbleRegion,
    tile: AiHorizontalDetectionTile,
    pageWidth: Int,
    pageHeight: Int
): AiBubbleRegion = bubble.copy(
    rect = mapAiHorizontalDetectionTileRect(bubble.rect, tile, pageWidth, pageHeight),
    safeTextRect = mapAiHorizontalDetectionTileRect(bubble.safeTextRect, tile, pageWidth, pageHeight),
    outline = bubble.outline.map { point ->
        mapAiHorizontalDetectionTilePoint(point, tile, pageWidth, pageHeight)
    }
)

internal fun dedupeAiHorizontalDetectionBubbles(
    bubbles: List<AiBubbleRegion>
): List<AiBubbleRegion> {
    if (bubbles.size < 2) return bubbles
    val retained = mutableListOf<AiBubbleRegion>()
    bubbles.sortedWith(
        compareByDescending<AiBubbleRegion> { it.rect.area() }
            .thenBy { it.rect.y }
            .thenBy { it.rect.x }
    ).forEach { candidate ->
        val duplicateIndex = retained.indexOfFirst { existing ->
            bubbleOverlapRatio(existing.rect, candidate.rect) >= HORIZONTAL_BUBBLE_DEDUP_OVERLAP_RATIO &&
                horizontalBubbleOutlinesOverlap(existing.outline, candidate.outline)
        }
        if (duplicateIndex < 0) {
            retained += candidate
        }
    }
    return retained.sortedWith(compareBy<AiBubbleRegion> { it.rect.y }.thenBy { it.rect.x })
}

private fun horizontalBubbleOutlinesOverlap(
    left: List<AiTranslationPoint>,
    right: List<AiTranslationPoint>
): Boolean {
    if (left.size < 3 || right.size < 3) return true
    val points = left + right
    if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
    val x = points.minOf { it.x }
    val y = points.minOf { it.y }
    val width = points.maxOf { it.x } - x
    val height = points.maxOf { it.y } - y
    if (width <= 0f || height <= 0f) return false
    var leftCount = 0
    var rightCount = 0
    var sharedCount = 0
    // Bounded interior sampling distinguishes shapes with overlapping bounding boxes.
    for (row in 0 until HORIZONTAL_OUTLINE_SAMPLE_GRID) {
        for (column in 0 until HORIZONTAL_OUTLINE_SAMPLE_GRID) {
            val sampleX = x + (column + 0.5f) * width / HORIZONTAL_OUTLINE_SAMPLE_GRID
            val sampleY = y + (row + 0.5f) * height / HORIZONTAL_OUTLINE_SAMPLE_GRID
            val inLeft = left.containsHorizontalBubblePoint(sampleX, sampleY)
            val inRight = right.containsHorizontalBubblePoint(sampleX, sampleY)
            if (inLeft) leftCount++
            if (inRight) rightCount++
            if (inLeft && inRight) sharedCount++
        }
    }
    val smallerCount = min(leftCount, rightCount)
    return smallerCount > 0 &&
        sharedCount.toFloat() / smallerCount >= HORIZONTAL_BUBBLE_DEDUP_OVERLAP_RATIO
}

internal fun List<AiTranslationPoint>.containsHorizontalBubblePoint(x: Float, y: Float): Boolean {
    var inside = false
    var previous = last()
    for (point in this) {
        if ((point.y > y) != (previous.y > y) &&
            x < (previous.x - point.x) * (y - point.y) / (previous.y - point.y) + point.x
        ) {
            inside = !inside
        }
        previous = point
    }
    return inside
}

private fun mapAiHorizontalDetectionTileRectOrEmpty(
    rect: AiTranslationRect,
    tile: AiHorizontalDetectionTile,
    pageWidth: Int,
    pageHeight: Int
): AiTranslationRect = if (rect.width > 0f && rect.height > 0f) {
    mapAiHorizontalDetectionTileRect(rect, tile, pageWidth, pageHeight)
} else {
    AiTranslationRect()
}

private fun mapAiHorizontalDetectionTileRect(
    rect: AiTranslationRect,
    tile: AiHorizontalDetectionTile,
    pageWidth: Int,
    pageHeight: Int
): AiTranslationRect {
    if (rect.width <= 0f || rect.height <= 0f || tile.width <= 0 || tile.height <= 0 || pageWidth <= 0 || pageHeight <= 0) {
        return AiTranslationRect()
    }
    val pageX = tile.left.toFloat() / pageWidth + rect.x * tile.width / pageWidth
    val pageY = tile.top.toFloat() / pageHeight + rect.y * tile.height / pageHeight
    val pageWidthNorm = rect.width * tile.width / pageWidth
    val pageHeightNorm = rect.height * tile.height / pageHeight
    val safeX = pageX.coerceIn(0f, 1f)
    val safeY = pageY.coerceIn(0f, 1f)
    return AiTranslationRect(
        x = safeX,
        y = safeY,
        width = pageWidthNorm.coerceAtMost(1f - safeX).coerceAtLeast(0f),
        height = pageHeightNorm.coerceAtMost(1f - safeY).coerceAtLeast(0f)
    )
}

private fun mapAiHorizontalDetectionTilePoint(
    point: AiTranslationPoint,
    tile: AiHorizontalDetectionTile,
    pageWidth: Int,
    pageHeight: Int
): AiTranslationPoint = AiTranslationPoint(
    x = (tile.left.toFloat() / pageWidth + point.x * tile.width / pageWidth).coerceIn(0f, 1f),
    y = (tile.top.toFloat() / pageHeight + point.y * tile.height / pageHeight).coerceIn(0f, 1f)
)

private fun bubbleOverlapRatio(left: AiTranslationRect, right: AiTranslationRect): Float {
    val overlapWidth = (min(left.x + left.width, right.x + right.width) - max(left.x, right.x)).coerceAtLeast(0f)
    val overlapHeight = (min(left.y + left.height, right.y + right.height) - max(left.y, right.y)).coerceAtLeast(0f)
    val overlap = overlapWidth * overlapHeight
    val smaller = min(left.area(), right.area())
    return if (smaller <= 0f) 0f else overlap / smaller
}

private fun AiTranslationRect.area(): Float = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)

private const val HORIZONTAL_TILE_TRIGGER_EDGE_MULTIPLIER = 2
private const val HORIZONTAL_TILE_TRIGGER_ASPECT_RATIO = 1.80f
private const val HORIZONTAL_TILE_OVERLAP_RATIO = 0.16f
private const val HORIZONTAL_TILE_MIN_OVERLAP_PX = 128
private const val HORIZONTAL_BUBBLE_DEDUP_OVERLAP_RATIO = 0.55f
private const val HORIZONTAL_OUTLINE_SAMPLE_GRID = 24
