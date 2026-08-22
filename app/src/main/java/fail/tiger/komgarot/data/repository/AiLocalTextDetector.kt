package fail.tiger.komgarot.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.usesHorizontalComicRules
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class AiLocalTextDetector(
    private val paddleTextDetector: AiPaddleTextDetector? = null,
    private val maxEdge: Int = 2048,
    private val maxRegions: Int = 64
) : AutoCloseable {
    fun detect(
        file: File,
        pageIndex: Int,
        settings: AiSettings? = null,
        sourceLanguageTag: String = "",
        onTimingStep: (String, Long) -> Unit = { _, _ -> },
        onDetectionStats: (AiLocalDetectionStats) -> Unit = {}
    ): AiTranslationLocalPageContext {
        val decoded = decodeForDetection(file)
        val bitmap = decoded.bitmap
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val sourceTextProfile = settings?.sourceTextProfile ?: AiSourceTextProfile.AUTO
            val paddleOutput = if (settings != null) {
                timedLocalDetectionStep(AI_TIMING_PADDLE_OCR, onTimingStep) {
                    paddleTextDetector?.detect(
                        bitmap = bitmap,
                        pixels = pixels,
                        pageIndex = pageIndex,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        settings = settings,
                        sourceLanguageTag = sourceLanguageTag,
                        maxRegions = maxRegions
                    ) ?: AiPaddleDetectionOutput.EMPTY
                }
            } else {
                AiPaddleDetectionOutput.EMPTY
            }
            val regions = selectLocalTextDetectionRegions(
                paddleRegions = paddleOutput.regions,
                heuristicRegions = {
                    timedLocalDetectionStep(AI_TIMING_HEURISTIC_FALLBACK, onTimingStep) {
                        val components = findInkComponents(
                            pixels = pixels,
                            width = bitmap.width,
                            height = bitmap.height
                        )
                        detectWithHeuristic(
                            clusters = mergeTextComponents(components, bitmap.width, bitmap.height),
                            pixels = pixels,
                            pageIndex = pageIndex,
                            detectionImageWidth = bitmap.width,
                            detectionImageHeight = bitmap.height,
                            sourceImageWidth = decoded.sourceWidth,
                            sourceImageHeight = decoded.sourceHeight,
                            sourceTextProfile = sourceTextProfile
                        )
                    }
                },
                sourceTextProfile = sourceTextProfile,
                maxRegions = maxRegions
            )
            paddleOutput.stats?.let { stats ->
                onTimingStep(
                    if (stats.sessionWasReused) AI_TIMING_PADDLE_SESSION_HOT else AI_TIMING_PADDLE_SESSION_COLD,
                    stats.sessionAcquireMs
                )
                onTimingStep(AI_TIMING_PADDLE_INFERENCE, stats.inferenceMs)
                onTimingStep(AI_TIMING_PADDLE_POST_PROCESS, stats.postProcessMs)
                onDetectionStats(stats.copy(regionCount = regions.size))
            }
            AiTranslationLocalPageContext(
                pageIndex = pageIndex,
                imageWidth = decoded.sourceWidth,
                imageHeight = decoded.sourceHeight,
                regions = regions
            )
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        paddleTextDetector?.close()
    }

    private fun detectWithHeuristic(
        clusters: List<AiTextCluster>,
        pixels: IntArray,
        pageIndex: Int,
        detectionImageWidth: Int,
        detectionImageHeight: Int,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
        sourceTextProfile: AiSourceTextProfile
    ): List<AiTranslationLocalTextRegion> = clusters
        .asSequence()
        .mapIndexed { index, cluster ->
            cluster.toLocalTextRegion(
                id = "p$pageIndex-r${index + 1}",
                pixels = pixels,
                detectionImageWidth = detectionImageWidth,
                detectionImageHeight = detectionImageHeight,
                sourceImageWidth = sourceImageWidth,
                sourceImageHeight = sourceImageHeight,
                sourceTextProfile = sourceTextProfile
            )
        }
        .filter { it.rect.width >= 0.01f && it.rect.height >= 0.01f }
        .sortedWith(compareBy<AiTranslationLocalTextRegion> { it.rect.y }.thenBy { it.rect.x })
        .take(maxRegions)
        .toList()

    private fun decodeForDetection(file: File): AiDetectionBitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = aiImageSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("failed to decode page image for local text detection")
        return AiDetectionBitmap(
            bitmap = bitmap,
            sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: bitmap.width,
            sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: bitmap.height
        )
    }
}

data class AiLocalDetectionStats(
    val sessionWasReused: Boolean,
    val sessionAcquireMs: Long,
    val preprocessMs: Long,
    val inferenceMs: Long,
    val postProcessMs: Long,
    val estimatedPeakWorkingSetBytes: Long,
    val regionCount: Int,
    val executionProvider: String
)

internal data class AiPaddleDetectionOutput(
    val regions: List<AiTranslationLocalTextRegion>,
    val stats: AiLocalDetectionStats?
) {
    companion object {
        val EMPTY = AiPaddleDetectionOutput(emptyList(), null)
    }
}

internal fun <T> timedLocalDetectionStep(
    label: String,
    onTimingStep: (String, Long) -> Unit,
    block: () -> T
): T {
    val startedAt = System.currentTimeMillis()
    return try {
        block()
    } finally {
        onTimingStep(label, (System.currentTimeMillis() - startedAt).coerceAtLeast(0L))
    }
}

internal fun selectLocalTextDetectionRegions(
    paddleRegions: List<AiTranslationLocalTextRegion>,
    heuristicRegions: () -> List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile,
    maxRegions: Int
): List<AiTranslationLocalTextRegion> {
    if (paddleRegions.isNotEmpty()) {
        return mergeLocalTextRegionsIntoTextBoxes(
            normalizeLocalTextDirectionsForProfile(paddleRegions, sourceTextProfile),
            sourceTextProfile
        )
            .take(maxRegions)
    }
    return mergeLocalTextRegionsIntoTextBoxes(
        normalizeLocalTextDirectionsForProfile(heuristicRegions(), sourceTextProfile),
        sourceTextProfile
    )
        .take(maxRegions)
}

internal fun normalizeLocalTextDirectionsForProfile(
    regions: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile
): List<AiTranslationLocalTextRegion> = if (sourceTextProfile.usesHorizontalComicRules()) {
    regions.map { region ->
        if (region.textDirection == AiTranslationTextDirection.HORIZONTAL && region.rotationDegrees == 0f) {
            region
        } else {
            region.copy(
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                rotationDegrees = 0f,
                sourceColumns = emptyList()
            )
        }
    }
} else {
    regions
}

private data class AiDetectionBitmap(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int
)

internal data class AiDetectedTextLine(
    val region: AiTranslationLocalTextRegion
) {
    val id: String get() = region.id
    val rect: AiTranslationRect get() = region.rect
    val textDirection: AiTranslationTextDirection get() = region.textDirection
    val textColor: String get() = region.textColor
    val backgroundColor: String get() = region.backgroundColor
    val confidence: Float get() = region.confidence
    val estimatedFontScale: Float get() = region.estimatedFontScale
    val rotationDegrees: Float get() = region.rotationDegrees
}

internal data class AiMaskRegion(
    val rect: AiTranslationRect,
    val rotationDegrees: Float = 0f
)

internal data class AiTextBlockCandidate(
    val lines: List<AiDetectedTextLine>,
    val textDirection: AiTranslationTextDirection,
    val textBounds: AiTranslationRect,
    val maskRegions: List<AiMaskRegion>
)

private const val AI_TIMING_PADDLE_OCR = "paddle_ocr"
private const val AI_TIMING_PADDLE_SESSION_COLD = "paddle_session_cold"
private const val AI_TIMING_PADDLE_SESSION_HOT = "paddle_session_hot"
private const val AI_TIMING_PADDLE_INFERENCE = "paddle_inference"
private const val AI_TIMING_PADDLE_POST_PROCESS = "paddle_post_process"
private const val AI_TIMING_HEURISTIC_FALLBACK = "heuristic_fallback"

internal fun mergeLocalTextRegionsIntoTextBoxes(
    regions: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO
): List<AiTranslationLocalTextRegion> = mergeCollapsedLocalTextRegionsIntoTextBoxes(
    regions = collapseHighlyOverlappingLocalTextRegions(regions),
    sourceTextProfile = sourceTextProfile
)

private fun mergeCollapsedLocalTextRegionsIntoTextBoxes(
    regions: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile
): List<AiTranslationLocalTextRegion> {
    if (regions.size < 2) return regions.sortedWith(aiLocalRegionReadingOrder())
    if (usesTextLinePipelineForLocalRegions(regions, sourceTextProfile)) {
        return buildMangaTextBlocks(
            lines = detectedTextLinesFromLocalRegions(regions),
            sourceTextProfile = sourceTextProfile
        ).toLocalTextBoxRegions()
    }
    val clusters = regions
        .filter { it.rect.width > 0f && it.rect.height > 0f }
        .map { mutableListOf(it) }
        .toMutableList()
    if (clusters.size < 2) return regions.sortedWith(aiLocalRegionReadingOrder())

    var changed: Boolean
    do {
        changed = false
        var leftIndex = 0
        while (leftIndex < clusters.size && !changed) {
            var rightIndex = leftIndex + 1
            while (rightIndex < clusters.size && !changed) {
                if (localTextBoxClustersCanMerge(clusters[leftIndex], clusters[rightIndex], sourceTextProfile)) {
                    clusters[leftIndex].addAll(clusters[rightIndex])
                    clusters.removeAt(rightIndex)
                    changed = true
                } else {
                    rightIndex += 1
                }
            }
            leftIndex += 1
        }
    } while (changed)

    return clusters
        .flatMap { splitMergedLocalTextBoxCluster(it, sourceTextProfile) }
        .map { it.toMergedLocalTextBoxRegion() }
        .sortedWith(aiLocalRegionReadingOrder())
}

internal fun collapseHighlyOverlappingLocalTextRegions(
    regions: List<AiTranslationLocalTextRegion>
): List<AiTranslationLocalTextRegion> {
    if (regions.size < 2) return regions
    val collapsed = mutableListOf<AiTranslationLocalTextRegion>()
    regions.forEach { region ->
        val duplicateIndex = collapsed.indexOfFirst { existing ->
            existing.textDirection.isCompatibleWith(region.textDirection) &&
                existing.rect.overlapRatioAgainstSmallerRegion(region.rect) >= 0.82f
        }
        if (duplicateIndex < 0) {
            collapsed += region
        } else {
            collapsed[duplicateIndex] = listOf(collapsed[duplicateIndex], region).toMergedLocalTextBoxRegion()
        }
    }
    return collapsed.sortedWith(aiLocalRegionReadingOrder())
}

private fun AiTranslationTextDirection.isCompatibleWith(other: AiTranslationTextDirection): Boolean =
    this == other || this == AiTranslationTextDirection.AUTO || other == AiTranslationTextDirection.AUTO

private fun AiTranslationRect.overlapRatioAgainstSmallerRegion(other: AiTranslationRect): Float {
    if (width <= 0f || height <= 0f || other.width <= 0f || other.height <= 0f) return 0f
    val overlapWidth = (min(x + width, other.x + other.width) - max(x, other.x)).coerceAtLeast(0f)
    val overlapHeight = (min(y + height, other.y + other.height) - max(y, other.y)).coerceAtLeast(0f)
    val smallerArea = min(width * height, other.width * other.height).coerceAtLeast(0.000001f)
    return overlapWidth * overlapHeight / smallerArea
}

private fun usesTextLinePipelineForLocalRegions(
    regions: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile
): Boolean =
    !sourceTextProfile.usesHorizontalComicRules() &&
        regions.any { it.textDirection == AiTranslationTextDirection.VERTICAL }

internal fun detectedTextLinesFromLocalRegions(
    regions: List<AiTranslationLocalTextRegion>
): List<AiDetectedTextLine> =
    regions
        .filter { it.rect.width > 0f && it.rect.height > 0f }
        .map(::AiDetectedTextLine)

internal fun buildMangaTextBlocks(
    lines: List<AiDetectedTextLine>,
    sourceTextProfile: AiSourceTextProfile
): List<AiTextBlockCandidate> {
    if (lines.isEmpty()) return emptyList()
    if (lines.size == 1) return listOf(lines.single().toTextBlockCandidate())
    val clusters = lines.map { mutableListOf(it) }.toMutableList()
    var changed: Boolean
    do {
        changed = false
        var leftIndex = 0
        while (leftIndex < clusters.size && !changed) {
            var rightIndex = leftIndex + 1
            while (rightIndex < clusters.size && !changed) {
                if (mangaTextLineClustersCanMerge(clusters[leftIndex], clusters[rightIndex], sourceTextProfile)) {
                    clusters[leftIndex].addAll(clusters[rightIndex])
                    clusters.removeAt(rightIndex)
                    changed = true
                } else {
                    rightIndex += 1
                }
            }
            leftIndex += 1
        }
    } while (changed)

    return clusters
        .flatMap { splitMangaTextLineCluster(it, sourceTextProfile) }
        .map { it.toTextBlockCandidate() }
        .sortedWith(aiTextBlockReadingOrder())
}

private fun mangaTextLineClustersCanMerge(
    left: List<AiDetectedTextLine>,
    right: List<AiDetectedTextLine>,
    sourceTextProfile: AiSourceTextProfile
): Boolean {
    if (sourceTextProfile.usesHorizontalComicRules()) return false
    return left.any { leftLine ->
        right.any { rightLine ->
            mangaTextLinesCanMerge(leftLine, rightLine)
        }
    }
}

private fun splitMangaTextLineCluster(
    lines: List<AiDetectedTextLine>,
    sourceTextProfile: AiSourceTextProfile
): List<List<AiDetectedTextLine>> {
    if (sourceTextProfile.usesHorizontalComicRules() || lines.size < 3) return listOf(lines)
    val direction = lines.detectedLineClusterDirection()
    if (direction != AiTranslationTextDirection.VERTICAL) return listOf(lines)
    val ordered = lines.sortedWith(aiDetectedTextLineReadingOrder(direction))
    val gaps = ordered.zipWithNext { left, right -> horizontalGap(left.rect, right.rect) }
    if (gaps.isEmpty()) return listOf(lines)
    val medianGap = gaps.sorted()[gaps.size / 2]
    val columnWidth = medianRegionThickness(lines.map { it.region }, AiTranslationTextDirection.VERTICAL)
    val splitGap = max(columnWidth * 1.20f, medianGap * 1.80f)
    val groups = mutableListOf<MutableList<AiDetectedTextLine>>(mutableListOf(ordered.first()))
    gaps.forEachIndexed { index, gap ->
        val left = ordered[index]
        val right = ordered[index + 1]
        val shouldSplit = gap > splitGap || !mangaTextLinesCanMerge(left, right)
        if (shouldSplit) {
            groups += mutableListOf(right)
        } else {
            groups.last() += right
        }
    }
    return groups
}

private fun mangaTextLinesCanMerge(
    left: AiDetectedTextLine,
    right: AiDetectedTextLine
): Boolean =
    if (japaneseMangaVerticalTextFragmentsCanMerge(left.rect, right.rect)) {
        japaneseMangaVerticalTextFontScalesCanMerge(left.region, right.region) &&
            japaneseMangaVerticalTextBackgroundsCanMerge(left.region, right.region)
    } else {
        japaneseMangaVerticalTextRegionsCanMerge(left.region, right.region)
    }

private fun AiDetectedTextLine.toTextBlockCandidate(): AiTextBlockCandidate =
    listOf(this).toTextBlockCandidate()

private fun List<AiDetectedTextLine>.toTextBlockCandidate(): AiTextBlockCandidate {
    val direction = detectedLineClusterDirection()
    val ordered = sortedWith(aiDetectedTextLineReadingOrder(direction))
    val textBounds = ordered.map { it.region }.boundingRect()
    return AiTextBlockCandidate(
        lines = ordered,
        textDirection = direction,
        textBounds = textBounds,
        maskRegions = ordered.map { AiMaskRegion(rect = it.rect, rotationDegrees = it.rotationDegrees) }
    )
}

internal fun List<AiTextBlockCandidate>.toLocalTextBoxRegions(): List<AiTranslationLocalTextRegion> =
    map { it.toLocalTextBoxRegion() }
        .sortedWith(aiLocalRegionReadingOrder())

private fun AiTextBlockCandidate.toLocalTextBoxRegion(): AiTranslationLocalTextRegion {
    val regions = lines.map { it.region }
    val sourceMaskColumns = maskRegions
        .map { it.rect }
        .mergeVerticalTextFragmentsIntoSourceColumns(textDirection)
    val merged = if (regions.size == 1) {
        regions.single().copy(
            estimatedFontScale = estimateMergedTextBoxFontScale(textDirection, regions)
        )
    } else {
        regions.toMergedLocalTextBoxRegion()
    }
    return merged.copy(
        textDirection = textDirection,
        rect = textBounds,
        textBounds = textBounds,
        renderBounds = textBounds,
        aiCropBounds = textBounds,
        sourceColumns = sourceMaskColumns
    )
}

private fun List<AiTranslationRect>.mergeVerticalTextFragmentsIntoSourceColumns(
    textDirection: AiTranslationTextDirection
): List<AiTranslationRect> {
    if (textDirection != AiTranslationTextDirection.VERTICAL || size < 2) return this
    val clusters = map { mutableListOf(it) }.toMutableList()
    var changed: Boolean
    do {
        changed = false
        var leftIndex = 0
        while (leftIndex < clusters.size && !changed) {
            var rightIndex = leftIndex + 1
            while (rightIndex < clusters.size && !changed) {
                if (clusters[leftIndex].any { leftRect ->
                        clusters[rightIndex].any { rightRect ->
                            japaneseMangaVerticalTextFragmentsCanMerge(leftRect, rightRect)
                        }
                    }
                ) {
                    clusters[leftIndex].addAll(clusters[rightIndex])
                    clusters.removeAt(rightIndex)
                    changed = true
                } else {
                    rightIndex += 1
                }
            }
            leftIndex += 1
        }
    } while (changed)
    return clusters
        .map { cluster ->
            if (cluster.size == 1) {
                cluster.single()
            } else {
                cluster.map { it.toRegion() }.boundingRect()
            }
        }
        .sortedWith(compareByDescending<AiTranslationRect> { it.x }.thenBy { it.y })
}

private fun localTextBoxClustersCanMerge(
    left: List<AiTranslationLocalTextRegion>,
    right: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile
): Boolean {
    val direction = sharedTextDirection(left.clusterDirection(), right.clusterDirection()) ?: return false
    val leftRect = left.boundingRect()
    val rightRect = right.boundingRect()
    val combined = unionRects(leftRect, rightRect)
    if (!combined.fitsMergedTextBoxLimits(direction)) return false
    val overlapEnough = normalizedIntersectionOverUnion(leftRect, rightRect) >= 0.18f
    if (overlapEnough && !usesJapaneseVerticalTextBoxRules(sourceTextProfile, direction)) return true
    return when (direction) {
        AiTranslationTextDirection.VERTICAL -> verticalTextBoxClustersCanMerge(left, right, leftRect, rightRect, sourceTextProfile)
        AiTranslationTextDirection.HORIZONTAL -> horizontalTextBoxClustersCanMerge(left, right, leftRect, rightRect)
        AiTranslationTextDirection.AUTO -> horizontalTextBoxClustersCanMerge(left, right, leftRect, rightRect)
    }
}

private fun horizontalTextBoxClustersCanMerge(
    left: List<AiTranslationLocalTextRegion>,
    right: List<AiTranslationLocalTextRegion>,
    leftRect: AiTranslationRect,
    rightRect: AiTranslationRect
): Boolean {
    val lineHeight = medianRegionThickness(left + right, AiTranslationTextDirection.HORIZONTAL)
    val gap = verticalGap(leftRect, rightRect)
    val alignedX = axisOverlapRatio(leftRect.x, leftRect.right, rightRect.x, rightRect.right) >= 0.55f ||
        abs(leftRect.centerX - rightRect.centerX) <= max(max(leftRect.width, rightRect.width) * 0.22f, 0.028f)
    val adjacentSameRow = axisOverlapRatio(leftRect.y, leftRect.bottom, rightRect.y, rightRect.bottom) >= 0.42f &&
        horizontalGap(leftRect, rightRect) <= max(lineHeight * 1.6f, 0.016f)
    val adjacentRows = alignedX && gap <= max(lineHeight * 0.95f, 0.014f)
    return adjacentSameRow || adjacentRows
}

private fun verticalTextBoxClustersCanMerge(
    left: List<AiTranslationLocalTextRegion>,
    right: List<AiTranslationLocalTextRegion>,
    leftRect: AiTranslationRect,
    rightRect: AiTranslationRect,
    sourceTextProfile: AiSourceTextProfile
): Boolean {
    if (usesJapaneseVerticalTextBoxRules(sourceTextProfile, AiTranslationTextDirection.VERTICAL)) {
        return japaneseMangaVerticalTextColumnClustersCanMerge(left, right)
    }
    val columnWidth = medianRegionThickness(left + right, AiTranslationTextDirection.VERTICAL)
    val gap = horizontalGap(leftRect, rightRect)
    val alignedY = axisOverlapRatio(leftRect.y, leftRect.bottom, rightRect.y, rightRect.bottom) >= 0.22f ||
        abs(leftRect.centerY - rightRect.centerY) <= max(max(leftRect.height, rightRect.height) * 0.34f, 0.055f)
    val sameColumnGapLimit = max(columnWidth * 2.4f, 0.020f)
    val adjacentSameColumn = axisOverlapRatio(leftRect.x, leftRect.right, rightRect.x, rightRect.right) >= 0.42f &&
        verticalGap(leftRect, rightRect) <= sameColumnGapLimit
    val topAligned = abs(leftRect.y - rightRect.y) <= max(columnWidth * 1.25f, 0.035f)
    val columnGapLimit = max(columnWidth * 1.9f, 0.028f)
    val adjacentColumns = alignedY && topAligned && gap <= columnGapLimit
    return adjacentSameColumn || adjacentColumns
}

private fun usesJapaneseVerticalTextBoxRules(
    sourceTextProfile: AiSourceTextProfile,
    direction: AiTranslationTextDirection
): Boolean = !sourceTextProfile.usesHorizontalComicRules() &&
    direction == AiTranslationTextDirection.VERTICAL

private fun japaneseMangaVerticalTextColumnClustersCanMerge(
    left: List<AiTranslationLocalTextRegion>,
    right: List<AiTranslationLocalTextRegion>
): Boolean = left.any { leftRegion ->
    right.any { rightRegion ->
        japaneseMangaVerticalTextRegionsCanMerge(leftRegion, rightRegion)
    }
}

private fun japaneseMangaVerticalTextRegionsCanMerge(
    leftRegion: AiTranslationLocalTextRegion,
    rightRegion: AiTranslationLocalTextRegion
): Boolean {
    if (japaneseMangaVerticalTextCloseNarrowOcrColumnsCanMerge(leftRegion, rightRegion)) return true
    return japaneseMangaVerticalTextColumnsCanMerge(leftRegion.rect, rightRegion.rect) &&
        japaneseMangaVerticalTextFontScalesCanMerge(leftRegion, rightRegion) &&
        japaneseMangaVerticalTextBackgroundsCanMerge(leftRegion, rightRegion)
}

private fun japaneseMangaVerticalTextColumnsCanMerge(
    leftRect: AiTranslationRect,
    rightRect: AiTranslationRect
): Boolean {
    val minWidth = min(leftRect.width, rightRect.width)
    val maxWidth = max(leftRect.width, rightRect.width)
    val averageWidth = (leftRect.width + rightRect.width) / 2f
    val xOverlap = axisOverlapRatio(leftRect.x, leftRect.right, rightRect.x, rightRect.right)
    val centerXDistance = abs(leftRect.centerX - rightRect.centerX)
    val sameColumnLike = xOverlap >= 0.62f && centerXDistance <= max(averageWidth * 0.35f, 0.006f)
    if (sameColumnLike) return false
    val widthRatio = minWidth / max(maxWidth, 0.001f)
    val sameVisualWidth = widthRatio >= 0.94f
    val gap = horizontalGap(leftRect, rightRect)
    val tightGap = gap <= max(averageWidth * 1.05f, 0.018f)
    val naturalBalloonGap = gap <= max(averageWidth * 1.85f, 0.032f)
    val topOffset = abs(leftRect.y - rightRect.y)
    val topTolerance = max(averageWidth * 1.05f, 0.024f)
    val topAligned = topOffset <= topTolerance
    val verticalOverlap = axisOverlapRatio(leftRect.y, leftRect.bottom, rightRect.y, rightRect.bottom)
    val heightRatio = min(leftRect.height, rightRect.height) / max(max(leftRect.height, rightRect.height), 0.001f)
    val relaxedTopTolerance = max(averageWidth * 1.20f, 0.026f)
    val nearSameTop = topOffset <= relaxedTopTolerance
    val highOverlapSameBubble = nearSameTop && verticalOverlap >= 0.72f
    val shortContainedColumn = nearSameTop && heightRatio <= 0.72f && verticalOverlap >= 0.90f
    val narrowShortContainedColumn = widthRatio >= 0.64f && shortContainedColumn
    val topAlignedSameWidthNaturalGap = sameVisualWidth && naturalBalloonGap && topAligned && verticalOverlap >= 0.35f
    return topAlignedSameWidthNaturalGap ||
        tightGap &&
        (
            sameVisualWidth && (topAligned || highOverlapSameBubble || shortContainedColumn) ||
                narrowShortContainedColumn
            )
}

private fun japaneseMangaVerticalTextCloseNarrowOcrColumnsCanMerge(
    leftRegion: AiTranslationLocalTextRegion,
    rightRegion: AiTranslationLocalTextRegion
): Boolean {
    val leftRect = leftRegion.rect
    val rightRect = rightRegion.rect
    val minWidth = min(leftRect.width, rightRect.width)
    val maxWidth = max(leftRect.width, rightRect.width).coerceAtLeast(0.001f)
    val averageWidth = (leftRect.width + rightRect.width) / 2f
    val xOverlap = axisOverlapRatio(leftRect.x, leftRect.right, rightRect.x, rightRect.right)
    val centerXDistance = abs(leftRect.centerX - rightRect.centerX)
    val sameColumnLike = xOverlap >= 0.62f && centerXDistance <= max(averageWidth * 0.35f, 0.006f)
    if (sameColumnLike) return false
    val widthRatio = minWidth / maxWidth
    val gap = horizontalGap(leftRect, rightRect)
    val topOffset = abs(leftRect.y - rightRect.y)
    val verticalOverlap = axisOverlapRatio(leftRect.y, leftRect.bottom, rightRect.y, rightRect.bottom)
    val minScale = min(leftRegion.estimatedFontScale, rightRegion.estimatedFontScale).coerceAtLeast(0.001f)
    val maxScale = max(leftRegion.estimatedFontScale, rightRegion.estimatedFontScale).coerceAtLeast(0.001f)
    return widthRatio >= 0.68f &&
        maxWidth <= 0.039f &&
        gap <= max(averageWidth * 0.85f, 0.018f) &&
        topOffset <= max(averageWidth * 0.95f, 0.020f) &&
        verticalOverlap >= 0.55f &&
        maxScale <= 0.96f &&
        minScale / maxScale >= 0.72f &&
        japaneseMangaVerticalTextBackgroundsCanMerge(leftRegion, rightRegion, maxDiff = 0.18f)
}

private fun japaneseMangaVerticalTextFragmentsCanMerge(
    leftRect: AiTranslationRect,
    rightRect: AiTranslationRect
): Boolean {
    val xOverlap = axisOverlapRatio(leftRect.x, leftRect.right, rightRect.x, rightRect.right)
    if (xOverlap < 0.62f) return false
    val minWidth = min(leftRect.width, rightRect.width)
    val maxWidth = max(leftRect.width, rightRect.width).coerceAtLeast(0.001f)
    val widthRatio = minWidth / maxWidth
    if (widthRatio < 0.82f) return false
    val averageWidth = (leftRect.width + rightRect.width) / 2f
    val centerAligned = abs(leftRect.centerX - rightRect.centerX) <= max(averageWidth * 0.35f, 0.006f)
    if (!centerAligned) return false
    val verticalGap = verticalGap(leftRect, rightRect)
    val gapLimit = max(averageWidth * 0.72f, 0.014f)
    return verticalGap <= gapLimit
}

private fun japaneseMangaVerticalTextFontScalesCanMerge(
    left: AiTranslationLocalTextRegion,
    right: AiTranslationLocalTextRegion
): Boolean {
    val minScale = min(left.estimatedFontScale, right.estimatedFontScale).coerceAtLeast(0.001f)
    val maxScale = max(left.estimatedFontScale, right.estimatedFontScale).coerceAtLeast(0.001f)
    return minScale / maxScale >= 0.90f
}

private fun japaneseMangaVerticalTextBackgroundsCanMerge(
    left: AiTranslationLocalTextRegion,
    right: AiTranslationLocalTextRegion,
    maxDiff: Float = 0.12f
): Boolean {
    val leftColor = parseHexRgb(left.backgroundColor) ?: return true
    val rightColor = parseHexRgb(right.backgroundColor) ?: return true
    val diff = (
        abs(leftColor.red - rightColor.red) +
            abs(leftColor.green - rightColor.green) +
            abs(leftColor.blue - rightColor.blue)
        ) / 765f
    return diff <= maxDiff
}

private fun splitMergedLocalTextBoxCluster(
    regions: List<AiTranslationLocalTextRegion>,
    sourceTextProfile: AiSourceTextProfile
): List<List<AiTranslationLocalTextRegion>> {
    if (sourceTextProfile != AiSourceTextProfile.JAPANESE_MANGA || regions.size < 3) return listOf(regions)
    val direction = regions.clusterDirection()
    if (direction != AiTranslationTextDirection.VERTICAL) return listOf(regions)
    val ordered = regions.sortedWith(aiLocalRegionReadingOrder(direction))
    val gaps = ordered.zipWithNext { left, right -> horizontalGap(left.rect, right.rect) }
    if (gaps.isEmpty()) return listOf(regions)
    val medianGap = gaps.sorted()[gaps.size / 2]
    val columnWidth = medianRegionThickness(regions, AiTranslationTextDirection.VERTICAL)
    val splitGap = max(columnWidth * 1.05f, medianGap * 1.45f)
    val groups = mutableListOf<MutableList<AiTranslationLocalTextRegion>>(mutableListOf(ordered.first()))
    gaps.forEachIndexed { index, gap ->
        val left = ordered[index]
        val right = ordered[index + 1]
        val shouldSplit = gap > splitGap ||
            !japaneseMangaVerticalTextRegionsCanMerge(left, right)
        if (shouldSplit) {
            groups += mutableListOf(ordered[index + 1])
        } else {
            groups.last() += ordered[index + 1]
        }
    }
    return groups
}

private fun AiTranslationRect.fitsMergedTextBoxLimits(direction: AiTranslationTextDirection): Boolean {
    val area = width * height
    return when (direction) {
        AiTranslationTextDirection.VERTICAL -> width <= 0.34f && height <= 0.62f && area <= 0.16f
        AiTranslationTextDirection.HORIZONTAL -> width <= 0.62f && height <= 0.36f && area <= 0.14f
        AiTranslationTextDirection.AUTO -> width <= 0.58f && height <= 0.38f && area <= 0.14f
    }
}

private fun List<AiTranslationLocalTextRegion>.toMergedLocalTextBoxRegion(): AiTranslationLocalTextRegion {
    if (size == 1) return first().copy(
        estimatedFontScale = estimateMergedTextBoxFontScale(first().textDirection, this),
        sourceColumns = first().effectiveSourceColumns()
    )
    val direction = clusterDirection()
    val ordered = sortedWith(aiLocalRegionReadingOrder(direction))
    val representative = ordered.maxByOrNull { it.confidence } ?: ordered.first()
    return representative.copy(
        id = ordered.first().id,
        rect = boundingRect(),
        textDirection = direction,
        textColor = dominantColor(ordered.map { it.textColor }),
        backgroundColor = dominantColor(ordered.map { it.backgroundColor }),
        confidence = ordered.map { it.confidence }.average().toFloat().coerceIn(0f, 1f),
        estimatedFontScale = estimateMergedTextBoxFontScale(direction, ordered),
        sourceColumns = ordered.flatMap { it.effectiveSourceColumns() }
    )
}

private fun estimateMergedTextBoxFontScale(
    direction: AiTranslationTextDirection,
    regions: List<AiTranslationLocalTextRegion>
): Float {
    val thickness = medianRegionThickness(regions, direction).coerceAtLeast(0.001f)
    val scaleFromLocalTextWidth = when (direction) {
        AiTranslationTextDirection.VERTICAL -> thickness / 0.045f
        AiTranslationTextDirection.HORIZONTAL -> thickness / 0.055f
        AiTranslationTextDirection.AUTO -> thickness / 0.050f
    }
    val existingScale = regions.maxOf { it.estimatedFontScale }
    return max(scaleFromLocalTextWidth * 1.16f, existingScale * 1.08f).coerceIn(0.74f, 1.40f)
}

private fun List<AiTranslationLocalTextRegion>.clusterDirection(): AiTranslationTextDirection {
    val vertical = count { it.textDirection == AiTranslationTextDirection.VERTICAL }
    val horizontal = count { it.textDirection == AiTranslationTextDirection.HORIZONTAL }
    if (vertical > horizontal) return AiTranslationTextDirection.VERTICAL
    if (horizontal > vertical) return AiTranslationTextDirection.HORIZONTAL
    val bounds = boundingRect()
    return when {
        bounds.height > bounds.width * 1.12f -> AiTranslationTextDirection.VERTICAL
        else -> AiTranslationTextDirection.HORIZONTAL
    }
}

private fun List<AiDetectedTextLine>.detectedLineClusterDirection(): AiTranslationTextDirection =
    map { it.region }.clusterDirection()

private fun sharedTextDirection(
    left: AiTranslationTextDirection,
    right: AiTranslationTextDirection
): AiTranslationTextDirection? = when {
    left == right -> left
    left == AiTranslationTextDirection.AUTO -> right
    right == AiTranslationTextDirection.AUTO -> left
    else -> null
}

private fun aiLocalRegionReadingOrder(
    direction: AiTranslationTextDirection? = null
): Comparator<AiTranslationLocalTextRegion> = Comparator { left, right ->
    val resolved = direction ?: sharedTextDirection(left.textDirection, right.textDirection)
    if (resolved == AiTranslationTextDirection.VERTICAL) {
        compareValuesBy(left, right, { -it.rect.x }, { it.rect.y })
    } else {
        compareValuesBy(left, right, { it.rect.y }, { it.rect.x })
    }
}

private fun aiDetectedTextLineReadingOrder(
    direction: AiTranslationTextDirection? = null
): Comparator<AiDetectedTextLine> = Comparator { left, right ->
    aiLocalRegionReadingOrder(direction).compare(left.region, right.region)
}

private fun aiTextBlockReadingOrder(): Comparator<AiTextBlockCandidate> = Comparator { left, right ->
    val direction = sharedTextDirection(left.textDirection, right.textDirection)
    val leftRegion = AiTranslationLocalTextRegion(
        id = left.lines.firstOrNull()?.id.orEmpty(),
        rect = left.textBounds,
        textDirection = left.textDirection,
        textColor = "#111111",
        backgroundColor = "#FFFFFF",
        confidence = 0f,
        estimatedFontScale = 1f
    )
    val rightRegion = AiTranslationLocalTextRegion(
        id = right.lines.firstOrNull()?.id.orEmpty(),
        rect = right.textBounds,
        textDirection = right.textDirection,
        textColor = "#111111",
        backgroundColor = "#FFFFFF",
        confidence = 0f,
        estimatedFontScale = 1f
    )
    aiLocalRegionReadingOrder(direction).compare(leftRegion, rightRegion)
}

private fun List<AiTranslationLocalTextRegion>.boundingRect(): AiTranslationRect {
    val left = minOf { it.rect.x }
    val top = minOf { it.rect.y }
    val right = maxOf { it.rect.right }
    val bottom = maxOf { it.rect.bottom }
    return AiTranslationRect(
        x = left.coerceIn(0f, 1f),
        y = top.coerceIn(0f, 1f),
        width = (right - left).coerceIn(0f, 1f - left.coerceIn(0f, 1f)),
        height = (bottom - top).coerceIn(0f, 1f - top.coerceIn(0f, 1f))
    )
}

private fun unionRects(left: AiTranslationRect, right: AiTranslationRect): AiTranslationRect =
    listOf(left.toRegion(), right.toRegion()).boundingRect()

private fun AiTranslationRect.toRegion(): AiTranslationLocalTextRegion = AiTranslationLocalTextRegion(
    id = "",
    rect = this,
    textDirection = AiTranslationTextDirection.AUTO,
    textColor = "#111111",
    backgroundColor = "#FFFFFF",
    confidence = 0f,
    estimatedFontScale = 1f
)

private fun medianRegionThickness(
    regions: List<AiTranslationLocalTextRegion>,
    direction: AiTranslationTextDirection
): Float {
    val values = regions.map {
        when (direction) {
            AiTranslationTextDirection.VERTICAL -> it.rect.width
            AiTranslationTextDirection.HORIZONTAL -> it.rect.height
            AiTranslationTextDirection.AUTO -> min(it.rect.width, it.rect.height)
        }
    }.sorted()
    if (values.isEmpty()) return 0.02f
    return values[values.size / 2]
}

private fun dominantColor(values: List<String>): String =
    values
        .filter { parseHexRgb(it) != null }
        .groupingBy { it.uppercase() }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: values.firstOrNull().orEmpty().ifBlank { "#111111" }

private val AiTranslationRect.right: Float get() = x + width
private val AiTranslationRect.bottom: Float get() = y + height
private val AiTranslationRect.centerX: Float get() = x + width / 2f
private val AiTranslationRect.centerY: Float get() = y + height / 2f

private fun axisOverlapRatio(leftStart: Float, leftEnd: Float, rightStart: Float, rightEnd: Float): Float {
    val overlap = (min(leftEnd, rightEnd) - max(leftStart, rightStart)).coerceAtLeast(0f)
    val shorter = min(leftEnd - leftStart, rightEnd - rightStart).coerceAtLeast(0.0001f)
    return overlap / shorter
}

private fun horizontalGap(left: AiTranslationRect, right: AiTranslationRect): Float =
    max(max(left.x, right.x) - min(left.right, right.right), 0f)

private fun verticalGap(left: AiTranslationRect, right: AiTranslationRect): Float =
    max(max(left.y, right.y) - min(left.bottom, right.bottom), 0f)

private fun normalizedIntersectionOverUnion(
    left: AiTranslationRect,
    right: AiTranslationRect
): Float {
    val intersectionLeft = max(left.x, right.x)
    val intersectionTop = max(left.y, right.y)
    val intersectionRight = min(left.x + left.width, right.x + right.width)
    val intersectionBottom = min(left.y + left.height, right.y + right.height)
    val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
    val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
    val intersectionArea = intersectionWidth * intersectionHeight
    val unionArea = left.width * left.height + right.width * right.height - intersectionArea
    if (unionArea <= 0f) return 0f
    return intersectionArea / unionArea
}

internal data class AiTextComponent(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val area: Int,
    val darkPixels: Int,
    val lightPixels: Int
) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal data class AiTextCluster(
    val components: List<AiTextComponent>
) {
    val left: Int = components.minOf { it.left }
    val top: Int = components.minOf { it.top }
    val right: Int = components.maxOf { it.right }
    val bottom: Int = components.maxOf { it.bottom }
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
    val area: Int = components.sumOf { it.area }
    val darkPixels: Int = components.sumOf { it.darkPixels }
    val lightPixels: Int = components.sumOf { it.lightPixels }
}

internal fun findInkComponents(
    pixels: IntArray,
    width: Int,
    height: Int
): List<AiTextComponent> {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) return emptyList()
    val mask = ByteArray(width * height)
    pixels.forEachIndexed { index, color ->
        val gray = gray(color)
        if (gray <= 72 || gray >= 238) mask[index] = 1
    }

    val queue = IntArray(width * height)
    val components = mutableListOf<AiTextComponent>()
    for (start in mask.indices) {
        if (mask[start] != 1.toByte()) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        mask[start] = 2
        var left = start % width
        var right = left
        var top = start / width
        var bottom = top
        var area = 0
        var darkPixels = 0
        var lightPixels = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            area += 1
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
            if (gray(pixels[index]) < 128) darkPixels += 1 else lightPixels += 1

            val leftIndex = index - 1
            if (x > 0 && mask[leftIndex] == 1.toByte()) {
                mask[leftIndex] = 2
                queue[tail++] = leftIndex
            }
            val rightIndex = index + 1
            if (x < width - 1 && mask[rightIndex] == 1.toByte()) {
                mask[rightIndex] = 2
                queue[tail++] = rightIndex
            }
            val topIndex = index - width
            if (y > 0 && mask[topIndex] == 1.toByte()) {
                mask[topIndex] = 2
                queue[tail++] = topIndex
            }
            val bottomIndex = index + width
            if (y < height - 1 && mask[bottomIndex] == 1.toByte()) {
                mask[bottomIndex] = 2
                queue[tail++] = bottomIndex
            }
        }

        val component = AiTextComponent(left, top, right, bottom, area, darkPixels, lightPixels)
        if (component.looksLikeGlyph(width, height)) {
            components += component
        }
    }
    return components
}

internal fun mergeTextComponents(
    components: List<AiTextComponent>,
    imageWidth: Int,
    imageHeight: Int
): List<AiTextCluster> {
    val clusters = components
        .sortedWith(compareBy<AiTextComponent> { it.top }.thenBy { it.left })
        .fold(mutableListOf<AiTextCluster>()) { acc, component ->
            val targetIndex = acc.indexOfFirst { it.accepts(component, imageWidth, imageHeight) }
            if (targetIndex >= 0) {
                val existing = acc[targetIndex]
                acc[targetIndex] = AiTextCluster(existing.components + component)
            } else {
                acc += AiTextCluster(listOf(component))
            }
            acc
        }

    var merged = clusters
    var changed: Boolean
    do {
        changed = false
        val next = mutableListOf<AiTextCluster>()
        for (cluster in merged) {
            val targetIndex = next.indexOfFirst { it.accepts(cluster, imageWidth, imageHeight) }
            if (targetIndex >= 0) {
                next[targetIndex] = AiTextCluster(next[targetIndex].components + cluster.components)
                changed = true
            } else {
                next += cluster
            }
        }
        merged = next
    } while (changed)

    val imageArea = imageWidth * imageHeight
    return merged
        .filter { it.components.size >= 2 }
        .filter { it.width >= 3 && it.height >= 3 }
        .filter { it.width <= imageWidth * 0.35f && it.height <= imageHeight * 0.42f }
        .filter { it.area <= imageArea * 0.035f }
        .sortedByDescending { it.components.size * 4 + it.area }
}

private fun AiTextComponent.looksLikeGlyph(imageWidth: Int, imageHeight: Int): Boolean {
    val maxGlyphWidth = (imageWidth * 0.09f).toInt().coerceAtLeast(18)
    val maxGlyphHeight = (imageHeight * 0.09f).toInt().coerceAtLeast(18)
    if (area !in 3..4500) return false
    if (width > maxGlyphWidth || height > maxGlyphHeight) return false
    if (width <= 1 || height <= 1) return false
    val aspect = width.toFloat() / height.toFloat()
    return aspect in 0.08f..8f
}

private fun AiTextCluster.accepts(
    component: AiTextComponent,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    val glyph = max(component.width, component.height).coerceAtLeast(6)
    val gap = (glyph * 2.4f).toInt().coerceIn(8, max(imageWidth, imageHeight) / 18)
    val expandedLeft = left - gap
    val expandedTop = top - gap
    val expandedRight = right + gap
    val expandedBottom = bottom + gap
    val intersectsExpanded = component.right >= expandedLeft &&
        component.left <= expandedRight &&
        component.bottom >= expandedTop &&
        component.top <= expandedBottom
    if (!intersectsExpanded) return false

    val combinedWidth = max(right, component.right) - min(left, component.left) + 1
    val combinedHeight = max(bottom, component.bottom) - min(top, component.top) + 1
    return combinedWidth <= imageWidth * 0.42f && combinedHeight <= imageHeight * 0.48f
}

private fun AiTextCluster.accepts(
    other: AiTextCluster,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    val medianGlyph = (components + other.components)
        .map { max(it.width, it.height) }
        .sorted()
        .let { it[it.size / 2] }
        .coerceAtLeast(6)
    val gap = (medianGlyph * 2.2f).toInt().coerceIn(8, max(imageWidth, imageHeight) / 20)
    val intersectsExpanded = other.right >= left - gap &&
        other.left <= right + gap &&
        other.bottom >= top - gap &&
        other.top <= bottom + gap
    if (!intersectsExpanded) return false

    val combinedWidth = max(right, other.right) - min(left, other.left) + 1
    val combinedHeight = max(bottom, other.bottom) - min(top, other.top) + 1
    return combinedWidth <= imageWidth * 0.42f && combinedHeight <= imageHeight * 0.48f
}

internal fun inferTextDirection(
    cluster: AiTextCluster,
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO
): AiTranslationTextDirection {
    if (sourceTextProfile.usesHorizontalComicRules()) {
        return AiTranslationTextDirection.HORIZONTAL
    }
    val components = cluster.components
    if (components.size < 2) {
        return if (cluster.height > cluster.width * 1.18f) {
            AiTranslationTextDirection.VERTICAL
        } else {
            AiTranslationTextDirection.HORIZONTAL
        }
    }
    val glyph = components
        .map { max(it.width, it.height) }
        .sorted()
        .let { it[it.size / 2] }
        .coerceAtLeast(4)
    val columnTolerance = glyph * 0.9f
    val rowTolerance = glyph * 0.7f
    val verticalPairs = components.sumOf { a ->
        components.count { b ->
            b !== a && abs(a.centerX - b.centerX) <= columnTolerance && abs(a.centerY - b.centerY) > glyph * 0.7f
        }
    }
    val horizontalPairs = components.sumOf { a ->
        components.count { b ->
            b !== a && abs(a.centerY - b.centerY) <= rowTolerance && abs(a.centerX - b.centerX) > glyph * 0.7f
        }
    }
    return when {
        verticalPairs > horizontalPairs -> AiTranslationTextDirection.VERTICAL
        horizontalPairs > verticalPairs -> AiTranslationTextDirection.HORIZONTAL
        cluster.height > cluster.width * 1.1f -> AiTranslationTextDirection.VERTICAL
        else -> AiTranslationTextDirection.HORIZONTAL
    }
}

internal fun AiTextCluster.toLocalTextRegion(
    id: String,
    pixels: IntArray,
    detectionImageWidth: Int,
    detectionImageHeight: Int,
    sourceImageWidth: Int = detectionImageWidth,
    sourceImageHeight: Int = detectionImageHeight,
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO
): AiTranslationLocalTextRegion {
    val pad = 1
    val safeLeft = (left - pad).coerceAtLeast(0)
    val safeTop = (top - pad).coerceAtLeast(0)
    val safeRight = (right + pad).coerceAtMost(detectionImageWidth - 1)
    val safeBottom = (bottom + pad).coerceAtMost(detectionImageHeight - 1)
    val direction = inferTextDirection(this, sourceTextProfile)
    val background = estimateBackgroundColor(
        pixels = pixels,
        imageWidth = detectionImageWidth,
        imageHeight = detectionImageHeight,
        left = safeLeft,
        top = safeTop,
        right = safeRight,
        bottom = safeBottom
    )
    val textColor = estimateTextColor(
        pixels = pixels,
        imageWidth = detectionImageWidth,
        imageHeight = detectionImageHeight,
        left = safeLeft,
        top = safeTop,
        right = safeRight,
        bottom = safeBottom,
        backgroundColor = background
    )
    val rect = normalizedSourceRectFromDetectionPixels(
        left = safeLeft,
        top = safeTop,
        right = safeRight,
        bottom = safeBottom,
        detectionImageWidth = detectionImageWidth,
        detectionImageHeight = detectionImageHeight,
        sourceImageWidth = sourceImageWidth,
        sourceImageHeight = sourceImageHeight
    )
    val fontScale = when (direction) {
        AiTranslationTextDirection.VERTICAL -> rect.width / 0.045f
        AiTranslationTextDirection.HORIZONTAL -> rect.height / 0.055f
        AiTranslationTextDirection.AUTO -> 1f
    }.coerceIn(0.72f, 1.28f)
    return AiTranslationLocalTextRegion(
        id = id,
        rect = rect,
        textDirection = direction,
        textColor = textColor,
        backgroundColor = background,
        confidence = confidenceScore(detectionImageWidth, detectionImageHeight),
        estimatedFontScale = fontScale,
        rotationDegrees = estimatedRegionRotationDegreesForRect(rect, direction, sourceTextProfile)
    )
}

internal fun detectedTextDirectionForRect(
    rect: AiTranslationRect,
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO
): AiTranslationTextDirection =
    if (sourceTextProfile.usesHorizontalComicRules()) {
        AiTranslationTextDirection.HORIZONTAL
    } else if (rect.height > rect.width * 1.15f) {
        AiTranslationTextDirection.VERTICAL
    } else {
        AiTranslationTextDirection.HORIZONTAL
    }

@Suppress("UNUSED_PARAMETER")
internal fun estimatedRegionRotationDegreesForRect(
    rect: AiTranslationRect,
    direction: AiTranslationTextDirection,
    sourceTextProfile: AiSourceTextProfile
): Float = 0f

internal fun normalizedSourceRectFromDetectionPixels(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    detectionImageWidth: Int,
    detectionImageHeight: Int,
    sourceImageWidth: Int,
    sourceImageHeight: Int
): AiTranslationRect {
    val detectionWidth = detectionImageWidth.coerceAtLeast(1).toFloat()
    val detectionHeight = detectionImageHeight.coerceAtLeast(1).toFloat()
    val sourceWidth = sourceImageWidth.coerceAtLeast(1).toFloat()
    val sourceHeight = sourceImageHeight.coerceAtLeast(1).toFloat()
    val sourceLeft = left.coerceAtLeast(0) * sourceWidth / detectionWidth
    val sourceTop = top.coerceAtLeast(0) * sourceHeight / detectionHeight
    val sourceRight = (right.coerceAtLeast(left) + 1) * sourceWidth / detectionWidth
    val sourceBottom = (bottom.coerceAtLeast(top) + 1) * sourceHeight / detectionHeight
    return AiTranslationRect(
        x = (sourceLeft / sourceWidth).coerceIn(0f, 1f),
        y = (sourceTop / sourceHeight).coerceIn(0f, 1f),
        width = ((sourceRight - sourceLeft) / sourceWidth).coerceIn(0f, 1f),
        height = ((sourceBottom - sourceTop) / sourceHeight).coerceIn(0f, 1f)
    )
}

private fun AiTextCluster.confidenceScore(imageWidth: Int, imageHeight: Int): Float {
    val density = area.toFloat() / (width * height).coerceAtLeast(1)
    val componentScore = (components.size / 12f).coerceIn(0.2f, 1f)
    val sizeScore = (min(width / imageWidth.toFloat(), height / imageHeight.toFloat()) * 18f).coerceIn(0.15f, 1f)
    return (0.25f + density.coerceIn(0.05f, 0.55f) + componentScore * 0.25f + sizeScore * 0.2f)
        .coerceIn(0.35f, 0.92f)
}

private fun estimateBackgroundColor(
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
): String {
    val margin = max(4, min(right - left + 1, bottom - top + 1) / 2)
    val samples = ArrayList<AiRgb>()
    val sampleLeft = (left - margin).coerceAtLeast(0)
    val sampleTop = (top - margin).coerceAtLeast(0)
    val sampleRight = (right + margin).coerceAtMost(imageWidth - 1)
    val sampleBottom = (bottom + margin).coerceAtMost(imageHeight - 1)
    for (y in sampleTop..sampleBottom step 2) {
        for (x in sampleLeft..sampleRight step 2) {
            if (x in left..right && y in top..bottom) continue
            val color = pixels[y * imageWidth + x]
            samples += AiRgb(red(color), green(color), blue(color))
        }
    }
    if (samples.isEmpty()) return "#F5F5F5"
    val red = samples.map(AiRgb::red).medianChannel()
    val green = samples.map(AiRgb::green).medianChannel()
    val blue = samples.map(AiRgb::blue).medianChannel()
    return "#%02X%02X%02X".format(red, green, blue)
}

private fun List<Int>.medianChannel(): Int = sorted()[size / 2].coerceIn(0, 255)

private fun estimateTextColor(
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    backgroundColor: String
): String {
    if (pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
        return ensureReadableAiTextColor("#111111", backgroundColor)
    }
    var darkPixels = 0
    var lightPixels = 0
    val safeLeft = left.coerceIn(0, imageWidth - 1)
    val safeTop = top.coerceIn(0, imageHeight - 1)
    val safeRight = right.coerceIn(safeLeft, imageWidth - 1)
    val safeBottom = bottom.coerceIn(safeTop, imageHeight - 1)
    for (y in safeTop..safeBottom) {
        for (x in safeLeft..safeRight) {
            val value = gray(pixels[y * imageWidth + x])
            if (value <= 96) darkPixels += 1
            if (value >= 205) lightPixels += 1
        }
    }
    val detectedTextColor = when {
        backgroundColor == "#111111" && lightPixels > 0 -> "#F2F2F2"
        backgroundColor == "#FFFFFF" && darkPixels > 0 -> "#111111"
        lightPixels > darkPixels -> "#F2F2F2"
        else -> "#111111"
    }
    return ensureReadableAiTextColor(detectedTextColor, backgroundColor)
}

private fun gray(color: Int): Int =
    ((((color shr 16) and 0xFF) * 299) + (((color shr 8) and 0xFF) * 587) + ((color and 0xFF) * 114)) / 1000

internal fun normalizedRectDistance(a: AiTranslationRect, b: AiTranslationRect): Float =
    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.width - b.width) + abs(a.height - b.height)

internal fun AiTranslationLocalTextRegion.effectiveTextBounds(): AiTranslationRect =
    textBounds.effectiveRegionGeometryOrNull() ?: rect

internal fun AiTranslationLocalTextRegion.effectiveSourceMaskBounds(): AiTranslationRect =
    textBounds.effectiveRegionGeometryOrNull() ?: inferredSourceMaskBounds()

internal fun AiTranslationLocalTextRegion.effectiveRenderBounds(): AiTranslationRect =
    renderBounds.effectiveRegionGeometryOrNull() ?: rect

internal fun AiTranslationLocalTextRegion.effectiveRenderBoundsForKind(kind: AiTranslationBlockKind): AiTranslationRect =
    renderBounds.effectiveRegionGeometryOrNull() ?: inferredRenderBoundsForKind(kind)

internal fun AiTranslationLocalTextRegion.effectiveAiCropBounds(): AiTranslationRect =
    aiCropBounds.effectiveRegionGeometryOrNull() ?: inferredAiCropBounds()

private fun AiTranslationRect.effectiveRegionGeometryOrNull(): AiTranslationRect? =
    takeIf { width > 0f && height > 0f }

private fun AiTranslationLocalTextRegion.inferredRenderBoundsForKind(kind: AiTranslationBlockKind): AiTranslationRect {
    if (kind == AiTranslationBlockKind.SIGN && textDirection == AiTranslationTextDirection.HORIZONTAL) {
        return rect.expandNormalized(
            extraWidth = max(rect.width * 0.32f, 0.020f),
            extraHeight = max(rect.height * 0.18f, 0.008f)
        )
    }
    val usesDialogueLayout = kind == AiTranslationBlockKind.DIALOGUE || kind == AiTranslationBlockKind.NARRATION
    if (!usesDialogueLayout) return rect
    return when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> rect.expandNormalized(
            extraWidth = max(max(rect.width * 1.20f, rect.height * 0.15f), 0.060f),
            extraHeight = max(rect.height * 0.12f, 0.016f)
        )
        AiTranslationTextDirection.HORIZONTAL -> rect.expandNormalized(
            extraWidth = max(rect.width * 0.18f, 0.014f),
            extraHeight = max(rect.height * 0.45f, 0.018f)
        )
        AiTranslationTextDirection.AUTO -> rect.expandNormalized(
            extraWidth = max(rect.width * 0.35f, 0.018f),
            extraHeight = max(rect.height * 0.25f, 0.014f)
        )
    }
}

private fun AiTranslationLocalTextRegion.inferredSourceMaskBounds(): AiTranslationRect {
    val extraWidth = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> max(rect.width * 0.42f, 0.010f)
        AiTranslationTextDirection.HORIZONTAL -> max(rect.width * 0.16f, 0.014f)
        AiTranslationTextDirection.AUTO -> max(rect.width * 0.24f, 0.012f)
    }
    val extraHeight = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> max(rect.height * 0.08f, 0.014f)
        AiTranslationTextDirection.HORIZONTAL -> max(rect.height * 0.32f, 0.010f)
        AiTranslationTextDirection.AUTO -> max(rect.height * 0.18f, 0.012f)
    }
    return rect.expandNormalized(extraWidth = extraWidth, extraHeight = extraHeight)
}

private fun AiTranslationLocalTextRegion.inferredAiCropBounds(): AiTranslationRect {
    val base = sourceColumns
        .filter { it.width > 0f && it.height > 0f }
        .takeIf { it.isNotEmpty() }
        ?.let { columns -> columns.map { it.toRegion() }.boundingRect() }
        ?: effectiveTextBounds()
    val extraWidth = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> max(base.width * 0.22f, 0.006f)
        AiTranslationTextDirection.HORIZONTAL -> max(base.width * 0.10f, 0.008f)
        AiTranslationTextDirection.AUTO -> max(base.width * 0.14f, 0.007f)
    }
    val extraHeight = when (textDirection) {
        AiTranslationTextDirection.VERTICAL -> max(base.height * 0.05f, 0.008f)
        AiTranslationTextDirection.HORIZONTAL -> max(base.height * 0.18f, 0.006f)
        AiTranslationTextDirection.AUTO -> max(base.height * 0.10f, 0.007f)
    }
    return base.expandNormalized(extraWidth = extraWidth, extraHeight = extraHeight)
}

internal fun AiTranslationLocalTextRegion.effectiveSourceColumns(): List<AiTranslationRect> {
    val explicitColumns = sourceColumns.filter { it.width > 0f && it.height > 0f }
    if (explicitColumns.isNotEmpty()) return explicitColumns
    return listOf(effectiveTextBounds())
}

private fun AiTranslationRect.expandNormalized(extraWidth: Float, extraHeight: Float): AiTranslationRect {
    val left = (x - extraWidth / 2f).coerceAtLeast(0f)
    val top = (y - extraHeight / 2f).coerceAtLeast(0f)
    val right = (x + width + extraWidth / 2f).coerceAtMost(1f)
    val bottom = (y + height + extraHeight / 2f).coerceAtMost(1f)
    return AiTranslationRect(
        x = left,
        y = top,
        width = (right - left).coerceIn(0f, 1f - left),
        height = (bottom - top).coerceIn(0f, 1f - top)
    )
}

internal fun AiTranslationBlock.correctWithLocalRegion(region: AiTranslationLocalTextRegion): AiTranslationBlock {
    val safeTextColor = ensureReadableAiTextColor(region.textColor, region.backgroundColor)
    return copy(
        localRegionId = region.id,
        rect = region.effectiveSourceMaskBounds(),
        translationRect = region.effectiveRenderBoundsForKind(kind),
        textColor = safeTextColor,
        maskColor = region.backgroundColor,
        textDirection = region.textDirection,
        rotationDegrees = if (region.rotationDegrees != 0f) region.rotationDegrees else rotationDegrees,
        fontScale = region.estimatedFontScale,
        sourceColumns = region.effectiveSourceColumns()
    )
}

internal fun AiTranslationBlock.withReadableColors(): AiTranslationBlock =
    copy(textColor = ensureReadableAiTextColor(textColor, maskColor))

internal fun ensureReadableAiTextColor(textColor: String, backgroundColor: String): String {
    val background = parseHexRgb(backgroundColor) ?: return textColor.takeIf { parseHexRgb(it) != null } ?: "#111111"
    val foreground = parseHexRgb(textColor)
    if (foreground != null && contrastRatio(foreground, background) >= MIN_AI_TEXT_CONTRAST_RATIO) {
        return textColor
    }
    return if (relativeLuminance(background) >= 0.45) "#111111" else "#F2F2F2"
}

private data class AiRgb(val red: Int, val green: Int, val blue: Int)

private fun red(color: Int): Int = color ushr 16 and 0xFF
private fun green(color: Int): Int = color ushr 8 and 0xFF
private fun blue(color: Int): Int = color and 0xFF

private fun parseHexRgb(value: String): AiRgb? {
    val hex = value.trim().removePrefix("#")
    if (!Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return null
    return AiRgb(
        red = hex.substring(0, 2).toInt(16),
        green = hex.substring(2, 4).toInt(16),
        blue = hex.substring(4, 6).toInt(16)
    )
}

private fun contrastRatio(left: AiRgb, right: AiRgb): Double {
    val leftLuminance = relativeLuminance(left)
    val rightLuminance = relativeLuminance(right)
    val lighter = maxOf(leftLuminance, rightLuminance)
    val darker = minOf(leftLuminance, rightLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: AiRgb): Double =
    0.2126 * color.red.toLinearColorChannel() +
        0.7152 * color.green.toLinearColorChannel() +
        0.0722 * color.blue.toLinearColorChannel()

private fun Int.toLinearColorChannel(): Double {
    val value = this / 255.0
    return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

private fun aiImageSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = max(width, height)
    if (longest <= 0 || longest <= maxEdge) return 1
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val MIN_AI_TEXT_CONTRAST_RATIO = 3.0
