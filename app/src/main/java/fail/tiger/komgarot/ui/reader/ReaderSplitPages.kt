package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint

internal fun AiTranslatedPage.forReaderPageSegment(segment: ReaderPageSegment): AiTranslatedPage {
    if (segment == ReaderPageSegment.FULL) return this
    return copy(
        imageWidth = (imageWidth / 2).coerceAtLeast(1),
        blocks = blocks.mapNotNull { block -> block.forReaderPageSegment(segment) }
    )
}

private fun AiTranslationBlock.forReaderPageSegment(segment: ReaderPageSegment): AiTranslationBlock? {
    val anchor = translationRect.takeIf { it.width > 0f && it.height > 0f } ?: rect
    val anchorCenter = anchor.x + anchor.width / 2f
    val belongsToSegment = when (segment) {
        ReaderPageSegment.LEFT_HALF -> anchorCenter < 0.5f
        ReaderPageSegment.RIGHT_HALF -> anchorCenter >= 0.5f
        ReaderPageSegment.FULL -> true
    }
    if (!belongsToSegment) return null
    return copy(
        rect = rect.forReaderPageSegment(segment) ?: return null,
        translationRect = translationRect
            .takeIf { it.width > 0f && it.height > 0f }
            ?.forReaderPageSegment(segment)
            ?: AiTranslationRect(),
        sourceColumns = sourceColumns.mapNotNull { column -> column.forReaderPageSegment(segment) },
        bubbleOutline = bubbleOutline.forReaderPageSegment(segment)
    )
}

private fun List<AiTranslationPoint>.forReaderPageSegment(segment: ReaderPageSegment): List<AiTranslationPoint> {
    if (segment == ReaderPageSegment.FULL || size < 3) return this
    val segmentStart = if (segment == ReaderPageSegment.RIGHT_HALF) 0.5f else 0f
    val segmentEnd = segmentStart + 0.5f
    val boundary = if (segment == ReaderPageSegment.RIGHT_HALF) segmentStart else segmentEnd
    val keepPoint: (AiTranslationPoint) -> Boolean = if (segment == ReaderPageSegment.RIGHT_HALF) {
        { point -> point.x >= boundary }
    } else {
        { point -> point.x <= boundary }
    }
    val clipped = buildList {
        var previous = this@forReaderPageSegment.last()
        var previousInside = keepPoint(previous)
        this@forReaderPageSegment.forEach { current ->
            val currentInside = keepPoint(current)
            if (currentInside != previousInside) {
                val deltaX = current.x - previous.x
                val ratio = if (deltaX == 0f) 0f else (boundary - previous.x) / deltaX
                add(
                    AiTranslationPoint(
                        x = boundary,
                        y = previous.y + (current.y - previous.y) * ratio.coerceIn(0f, 1f)
                    )
                )
            }
            if (currentInside) add(current)
            previous = current
            previousInside = currentInside
        }
    }
    return clipped
        .takeIf { it.size >= 3 }
        ?.map { point -> point.copy(x = ((point.x - segmentStart) * 2f).coerceIn(0f, 1f)) }
        .orEmpty()
}

internal fun AiTranslationRect.forReaderPageSegment(segment: ReaderPageSegment): AiTranslationRect? {
    if (segment == ReaderPageSegment.FULL) return this
    if (width <= 0f || height <= 0f) return null
    val segmentStart = if (segment == ReaderPageSegment.RIGHT_HALF) 0.5f else 0f
    val segmentEnd = segmentStart + 0.5f
    val clippedLeft = x.coerceAtLeast(segmentStart)
    val clippedRight = (x + width).coerceAtMost(segmentEnd)
    if (clippedRight <= clippedLeft) return null
    return copy(
        x = (clippedLeft - segmentStart) * 2f,
        width = (clippedRight - clippedLeft) * 2f
    )
}
