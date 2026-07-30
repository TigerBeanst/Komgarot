package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationRect

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
        sourceColumns = sourceColumns.mapNotNull { column -> column.forReaderPageSegment(segment) }
    )
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
