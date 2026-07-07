package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AiPaddleTextDetector(
    private val context: Context,
    private val modelRepository: AiLocalModelRepository
) {
    fun detect(
        bitmap: Bitmap,
        pixels: IntArray,
        pageIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        settings: AiSettings,
        maxRegions: Int
    ): List<AiTranslationLocalTextRegion> {
        val assets = resolvePaddleAssets(settings) ?: return emptyList()
        return runCatching {
            runDetectionModel(bitmap, pixels, pageIndex, sourceWidth, sourceHeight, assets, maxRegions, settings.sourceTextProfile)
        }.getOrElse { emptyList() }
    }

    private fun resolvePaddleAssets(settings: AiSettings): PaddleModelAssets? {
        val tier = if (settings.autoSelectDeviceTier) {
            recommendAiLocalModelTier(deviceProfile(context))
        } else {
            AiLocalModelTier.LOW
        }
        val plan = defaultAiLocalModelPlan(
            collectionId = settings.modelCollectionId,
            revision = settings.modelRevision,
            tier = tier
        )
        val detectionModel = modelRepository.installedFiles(plan.detRepoId, settings.modelRevision)
            .firstOrNull { it.name.endsWith(".onnx", ignoreCase = true) }
            ?: return null
        return PaddleModelAssets(detectionModelFile = detectionModel, tier = tier)
    }

    private fun runDetectionModel(
        bitmap: Bitmap,
        pixels: IntArray,
        pageIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        assets: PaddleModelAssets,
        maxRegions: Int,
        sourceTextProfile: AiSourceTextProfile
    ): List<AiTranslationLocalTextRegion> {
        val input = bitmap.toPaddleDetectorInput(maxSide = paddleDetectorInputMaxSide(assets.tier, sourceTextProfile))
        val env = OrtEnvironment.getEnvironment()
        env.createSession(assets.detectionModelFile.absolutePath, OrtSession.SessionOptions()).use { session ->
            val inputName = session.inputNames.firstOrNull() ?: return emptyList()
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input.data), longArrayOf(1, 3, input.height.toLong(), input.width.toLong())).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { result ->
                    val probability = result.firstProbabilityMap() ?: return emptyList()
                    val rects = paddleProbabilityMapToRects(
                        probabilityMap = probability.data,
                        mapWidth = probability.width,
                        mapHeight = probability.height,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight
                    )
                    val refinedRects = refinePaddleTextRectsWithInkTextLines(
                        rects = rects,
                        pixels = pixels,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        sourceTextProfile = sourceTextProfile
                    )
                    return refinedRects.take(maxRegions).mapIndexed { index, rect ->
                        rect.toLocalRegion(
                            id = "p$pageIndex-r${index + 1}",
                            pixels = pixels,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            sourceTextProfile = sourceTextProfile
                        )
                    }
                }
            }
        }
    }
}

private data class PaddleModelAssets(
    val detectionModelFile: File,
    val tier: AiLocalModelTier
)

internal data class PaddleDetectorInput(
    val width: Int,
    val height: Int,
    val data: FloatArray
)

internal fun Bitmap.toPaddleDetectorInput(maxSide: Int = 960): PaddleDetectorInput {
    val scale = min(1f, maxSide / max(width, height).coerceAtLeast(1).toFloat())
    val resizedWidth = (width * scale).roundToInt().coerceAtLeast(32).roundToMultipleOf32()
    val resizedHeight = (height * scale).roundToInt().coerceAtLeast(32).roundToMultipleOf32()
    val resized = if (resizedWidth == width && resizedHeight == height) this else Bitmap.createScaledBitmap(this, resizedWidth, resizedHeight, true)
    return try {
        val resizedPixels = IntArray(resizedWidth * resizedHeight)
        resized.getPixels(resizedPixels, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)
        val channelSize = resizedWidth * resizedHeight
        val input = FloatArray(channelSize * 3)
        resizedPixels.forEachIndexed { index, color ->
            val red = ((color shr 16) and 0xFF) / 255f
            val green = ((color shr 8) and 0xFF) / 255f
            val blue = (color and 0xFF) / 255f
            input[index] = (red - 0.485f) / 0.229f
            input[channelSize + index] = (green - 0.456f) / 0.224f
            input[channelSize * 2 + index] = (blue - 0.406f) / 0.225f
        }
        PaddleDetectorInput(width = resizedWidth, height = resizedHeight, data = input)
    } finally {
        if (resized !== this) resized.recycle()
    }
}

internal fun paddleProbabilityMapToRects(
    probabilityMap: FloatArray,
    mapWidth: Int,
    mapHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    threshold: Float = 0.30f
): List<PaddleTextRect> {
    if (probabilityMap.size < mapWidth * mapHeight || mapWidth <= 0 || mapHeight <= 0) return emptyList()
    val visited = BooleanArray(mapWidth * mapHeight)
    val queue = IntArray(mapWidth * mapHeight)
    val rects = mutableListOf<PaddleTextRect>()
    for (start in 0 until mapWidth * mapHeight) {
        if (visited[start] || probabilityMap[start] < threshold) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        var left = start % mapWidth
        var right = left
        var top = start / mapWidth
        var bottom = top
        var area = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % mapWidth
            val y = index / mapWidth
            area += 1
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
            fun visit(nextX: Int, nextY: Int) {
                if (nextX !in 0 until mapWidth || nextY !in 0 until mapHeight) return
                val next = nextY * mapWidth + nextX
                if (visited[next] || probabilityMap[next] < threshold) return
                visited[next] = true
                queue[tail++] = next
            }
            visit(x - 1, y)
            visit(x + 1, y)
            visit(x, y - 1)
            visit(x, y + 1)
        }
        val width = right - left + 1
        val height = bottom - top + 1
        val sourceRect = normalizedSourceRectFromDetectionPixels(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            detectionImageWidth = mapWidth,
            detectionImageHeight = mapHeight,
            sourceImageWidth = sourceWidth,
            sourceImageHeight = sourceHeight
        )
        if (area >= 6 && width >= 2 && height >= 2 && sourceRect.width <= 0.45f && sourceRect.height <= 0.45f) {
            rects += PaddleTextRect(sourceRect, area)
        }
    }
    return filterBroadPaddleTextRects(rects).sortedByDescending { it.area }
}

internal data class PaddleTextRect(
    val rect: AiTranslationRect,
    val area: Int
)

internal fun refinePaddleTextRectsWithInkTextLines(
    rects: List<PaddleTextRect>,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): List<PaddleTextRect> {
    if (rects.isEmpty() || pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return rects
    return rects.flatMap { textRect ->
        splitPaddleTextRectIntoInkTextLineRects(
            rect = textRect.rect,
            pixels = pixels,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceTextProfile = sourceTextProfile
        ).map { splitRect ->
            PaddleTextRect(rect = splitRect, area = textRect.area)
        }
    }.sortedWith(paddleTextRectReadingOrder(sourceTextProfile))
}

internal fun splitPaddleTextRectIntoInkTextLineRects(
    rect: AiTranslationRect,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): List<AiTranslationRect> {
    if (pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0 || rect.width <= 0f || rect.height <= 0f) {
        return listOf(rect)
    }
    val pixelRect = rect.toPaddleInkBounds(imageWidth, imageHeight) ?: return listOf(rect)
    val components = findPaddleInkComponentsForBounds(pixels, imageWidth, imageHeight, pixelRect)
        .filter { it.overlaps(pixelRect) && it.centerInside(pixelRect) }
    if (components.size < 4) return listOf(rect)
    val verticalClusters = components.toPaddleVerticalInkLineClusters()
    val horizontalClusters = components.toPaddleHorizontalInkLineClusters()
    val prefersVerticalInkLines =
        sourceTextProfile != AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON &&
            verticalClusters.size >= 2 &&
            verticalClusters.size >= horizontalClusters.size
    val direction = if (prefersVerticalInkLines) {
        AiTranslationTextDirection.VERTICAL
    } else {
        detectedTextDirectionForRect(rect, sourceTextProfile)
    }
    if (!paddleTextRectAllowsInkLineSplitting(pixelRect, components, direction)) return listOf(rect)
    val clusters = when (direction) {
        AiTranslationTextDirection.VERTICAL -> verticalClusters
        AiTranslationTextDirection.HORIZONTAL -> horizontalClusters
        AiTranslationTextDirection.AUTO -> horizontalClusters
    }
    if (clusters.size < 2) return listOf(rect)
    val splitRects = clusters
        .mapNotNull {
            it.toNormalizedPaddleSplitRect(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        }
        .filter { split -> rectIntersectionArea(split, rect) / (split.width * split.height).coerceAtLeast(0.0001f) >= 0.80f }
        .sortedWith(aiTranslationRectReadingOrder(direction))
    return splitRects.takeIf { it.size >= 2 } ?: listOf(rect)
}

private fun paddleTextRectAllowsInkLineSplitting(
    bounds: PaddleInkBounds,
    components: List<AiTextComponent>,
    direction: AiTranslationTextDirection
): Boolean {
    if (components.size < 6) return false
    val primarySizes = when (direction) {
        AiTranslationTextDirection.VERTICAL -> components.map { it.width }
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> components.map { it.height }
    }.filter { it > 0 }.sorted()
    if (primarySizes.isEmpty()) return false
    val medianPrimarySize = primarySizes[primarySizes.size / 2].coerceAtLeast(1)
    val primarySpan = when (direction) {
        AiTranslationTextDirection.VERTICAL -> bounds.right - bounds.left
        AiTranslationTextDirection.HORIZONTAL,
        AiTranslationTextDirection.AUTO -> bounds.bottom - bounds.top
    }
    val minimumSplitSpan = max(medianPrimarySize * 6.0f, 46.0f)
    return primarySpan >= minimumSplitSpan
}

internal fun filterBroadPaddleTextRects(rects: List<PaddleTextRect>): List<PaddleTextRect> {
    if (rects.size < 3) return rects
    return rects.filterNot { candidate ->
        candidate.isBroadPaddleTextRect() &&
            rects.count { other ->
                other !== candidate &&
                    other.area < candidate.area * 0.55f &&
                    paddleRectInsideRatio(inner = other.rect, outer = candidate.rect) >= 0.82f
            } >= 2
    }
}

private fun PaddleTextRect.isBroadPaddleTextRect(): Boolean =
    rect.width >= 0.18f &&
        rect.height >= 0.18f &&
        rect.width * rect.height >= 0.045f

private fun List<AiTextComponent>.toPaddleVerticalInkLineClusters(): List<AiTextCluster> =
    toPaddleInkLineClusters(
        primaryCenter = { centerX },
        secondaryCenter = { centerY },
        primarySize = { width },
        secondarySize = { height },
        descendingPrimary = true
    )

private fun List<AiTextComponent>.toPaddleHorizontalInkLineClusters(): List<AiTextCluster> =
    toPaddleInkLineClusters(
        primaryCenter = { centerY },
        secondaryCenter = { centerX },
        primarySize = { height },
        secondarySize = { width },
        descendingPrimary = false
    )

private fun List<AiTextComponent>.toPaddleInkLineClusters(
    primaryCenter: AiTextComponent.() -> Float,
    secondaryCenter: AiTextComponent.() -> Float,
    primarySize: AiTextComponent.() -> Int,
    secondarySize: AiTextComponent.() -> Int,
    descendingPrimary: Boolean
): List<AiTextCluster> {
    if (isEmpty()) return emptyList()
    val primarySizes = map(primarySize).sorted()
    val secondarySizes = map(secondarySize).sorted()
    val primaryTolerance = max(primarySizes[primarySizes.size / 2] * 1.35f, 5.5f)
    val secondaryGapLimit = max(secondarySizes[secondarySizes.size / 2] * 2.4f, 10f)
    val ordered = if (descendingPrimary) {
        sortedByDescending(primaryCenter)
    } else {
        sortedBy(primaryCenter)
    }
    val groups = mutableListOf<MutableList<AiTextComponent>>()
    ordered.forEach { component ->
        val target = groups.firstOrNull { group ->
            val groupPrimaryCenter = group.map(primaryCenter).average().toFloat()
            if (abs(groupPrimaryCenter - component.primaryCenter()) > primaryTolerance) return@firstOrNull false
            val nearestSecondary = group.minOf { abs(it.secondaryCenter() - component.secondaryCenter()) }
            nearestSecondary <= secondaryGapLimit * 3.0f
        }
        if (target != null) {
            target += component
        } else {
            groups += mutableListOf(component)
        }
    }
    return groups
        .map { group -> AiTextCluster(group) }
        .filter { it.components.size >= 2 }
        .sortedWith(compareByDescending<AiTextCluster> { it.components.size }.thenByDescending { it.area })
}

private fun AiTextCluster.toNormalizedPaddleSplitRect(
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int
): AiTranslationRect? {
    if (components.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null
    val pad = max(1, (components.map { min(it.width, it.height) }.sorted().let { it[it.size / 2] } * 0.20f).roundToInt())
    return normalizedSourceRectFromDetectionPixels(
        left = (left - pad).coerceAtLeast(0),
        top = (top - pad).coerceAtLeast(0),
        right = (right + pad).coerceAtMost(imageWidth - 1),
        bottom = (bottom + pad).coerceAtMost(imageHeight - 1),
        detectionImageWidth = imageWidth,
        detectionImageHeight = imageHeight,
        sourceImageWidth = sourceWidth,
        sourceImageHeight = sourceHeight
    ).takeIf { it.width > 0f && it.height > 0f }
}

private fun paddleTextRectReadingOrder(sourceTextProfile: AiSourceTextProfile): Comparator<PaddleTextRect> =
    Comparator { left, right ->
        val direction = detectedTextDirectionForRect(left.rect, sourceTextProfile)
        aiTranslationRectReadingOrder(direction).compare(left.rect, right.rect)
    }

private fun aiTranslationRectReadingOrder(direction: AiTranslationTextDirection): Comparator<AiTranslationRect> =
    if (direction == AiTranslationTextDirection.VERTICAL) {
        compareByDescending<AiTranslationRect> { it.x }.thenBy { it.y }
    } else {
        compareBy<AiTranslationRect> { it.y }.thenBy { it.x }
    }

private fun AiTextComponent.overlaps(rect: PaddleInkBounds): Boolean =
    right >= rect.left && left <= rect.right && bottom >= rect.top && top <= rect.bottom

private fun AiTextComponent.centerInside(rect: PaddleInkBounds): Boolean =
    centerX >= rect.left && centerX <= rect.right && centerY >= rect.top && centerY <= rect.bottom

internal fun findPaddleInkComponentsForRect(
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
): List<AiTextComponent> {
    return findPaddleInkComponentsForBounds(
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        bounds = PaddleInkBounds(left, top, right, bottom)
    )
}

private fun findPaddleInkComponentsForBounds(
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    bounds: PaddleInkBounds
): List<AiTextComponent> {
    if (pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return emptyList()
    val safeBounds = bounds.toSafePaddleInkBounds(imageWidth, imageHeight) ?: return emptyList()
    val backgroundGray = estimatePaddleInkBackgroundGray(pixels, imageWidth, safeBounds)
    val darkComponents = findPaddleInkComponentsForRect(
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        bounds = safeBounds,
        backgroundGray = backgroundGray,
        detectLightText = false
    )
    val lightComponents = findPaddleInkComponentsForRect(
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        bounds = safeBounds,
        backgroundGray = backgroundGray,
        detectLightText = true
    )
    return mergeOverlappingPaddleInkComponents(darkComponents + lightComponents)
}

private fun mergeOverlappingPaddleInkComponents(
    components: List<AiTextComponent>
): List<AiTextComponent> {
    if (components.size < 2) return components
    val merged = mutableListOf<AiTextComponent>()
    components
        .sortedWith(compareBy<AiTextComponent> { it.left }.thenBy { it.top })
        .forEach { component ->
            var current = component
            var index = 0
            while (index < merged.size) {
                val existing = merged[index]
                if (paddleInkComponentsShouldMerge(existing, current)) {
                    current = existing.mergePaddleInkComponent(current)
                    merged.removeAt(index)
                    index = 0
                } else {
                    index += 1
                }
            }
            merged += current
        }
    return merged.sortedWith(compareBy<AiTextComponent> { it.left }.thenBy { it.top })
}

private fun paddleInkComponentsShouldMerge(
    left: AiTextComponent,
    right: AiTextComponent
): Boolean {
    val overlapLeft = max(left.left, right.left)
    val overlapTop = max(left.top, right.top)
    val overlapRight = min(left.right, right.right)
    val overlapBottom = min(left.bottom, right.bottom)
    val overlapArea = max(0, overlapRight - overlapLeft + 1) * max(0, overlapBottom - overlapTop + 1)
    val smallerArea = min(left.area, right.area).coerceAtLeast(1)
    if (overlapArea.toFloat() / smallerArea >= 0.24f) return true
    val horizontalGap = max(max(left.left, right.left) - min(left.right, right.right), 0)
    val verticalGap = max(max(left.top, right.top) - min(left.bottom, right.bottom), 0)
    val verticalOverlap = max(0, min(left.bottom, right.bottom) - max(left.top, right.top) + 1)
    val horizontalOverlap = max(0, min(left.right, right.right) - max(left.left, right.left) + 1)
    val shorterHeight = min(left.height, right.height).coerceAtLeast(1)
    val shorterWidth = min(left.width, right.width).coerceAtLeast(1)
    return (horizontalGap <= 1 && verticalOverlap.toFloat() / shorterHeight >= 0.48f) ||
        (verticalGap <= 1 && horizontalOverlap.toFloat() / shorterWidth >= 0.48f)
}

private fun AiTextComponent.mergePaddleInkComponent(
    other: AiTextComponent
): AiTextComponent =
    AiTextComponent(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
        area = area + other.area,
        darkPixels = darkPixels + other.darkPixels,
        lightPixels = lightPixels + other.lightPixels
    )

private fun findPaddleInkComponentsForRect(
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    bounds: PaddleInkBounds,
    backgroundGray: Int,
    detectLightText: Boolean
): List<AiTextComponent> {
    val mask = ByteArray(imageWidth * imageHeight)
    val safeLeft = bounds.left
    val safeTop = bounds.top
    val safeRight = bounds.right
    val safeBottom = bounds.bottom
    val darkThreshold = min(backgroundGray - 28, 172).coerceAtLeast(32)
    val lightThreshold = max(backgroundGray + 28, 176).coerceAtMost(238)
    for (y in safeTop until safeBottom) {
        for (x in safeLeft until safeRight) {
            val value = paddleGray(pixels[y * imageWidth + x])
            val ink = if (detectLightText) value >= lightThreshold else value <= darkThreshold
            if (ink) mask[y * imageWidth + x] = 1
        }
    }

    val queue = IntArray(imageWidth * imageHeight)
    val components = mutableListOf<AiTextComponent>()
    for (startY in safeTop until safeBottom) {
        for (startX in safeLeft until safeRight) {
            val start = startY * imageWidth + startX
            if (mask[start] != 1.toByte()) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            mask[start] = 2
            var left = startX
            var right = startX
            var top = startY
            var bottom = startY
            var area = 0
            var darkPixels = 0
            var lightPixels = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % imageWidth
                val y = index / imageWidth
                val gray = paddleGray(pixels[index])
                area += 1
                if (gray < 128) darkPixels += 1 else lightPixels += 1
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
                fun visit(nextX: Int, nextY: Int) {
                    if (nextX !in safeLeft until safeRight || nextY !in safeTop until safeBottom) return
                    val next = nextY * imageWidth + nextX
                    if (mask[next] != 1.toByte()) return
                    mask[next] = 2
                    queue[tail++] = next
                }
                visit(x - 1, y)
                visit(x + 1, y)
                visit(x, y - 1)
                visit(x, y + 1)
            }
            val component = AiTextComponent(left, top, right, bottom, area, darkPixels, lightPixels)
            if (component.looksLikePaddleGlyph(imageWidth, imageHeight)) {
                components += component
            }
        }
    }
    return components
}

private data class PaddleInkBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

private fun PaddleInkBounds.toSafePaddleInkBounds(imageWidth: Int, imageHeight: Int): PaddleInkBounds? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    val safeLeft = left.coerceIn(0, imageWidth - 1)
    val safeTop = top.coerceIn(0, imageHeight - 1)
    val safeRight = right.coerceIn(safeLeft + 1, imageWidth)
    val safeBottom = bottom.coerceIn(safeTop + 1, imageHeight)
    return PaddleInkBounds(safeLeft, safeTop, safeRight, safeBottom)
}

private fun estimatePaddleInkBackgroundGray(
    pixels: IntArray,
    imageWidth: Int,
    bounds: PaddleInkBounds
): Int {
    val values = mutableListOf<Int>()
    for (y in bounds.top until bounds.bottom step 2) {
        for (x in bounds.left until bounds.right step 2) {
            values += paddleGray(pixels[y * imageWidth + x])
        }
    }
    if (values.isEmpty()) return 245
    values.sort()
    return values[values.size * 3 / 4]
}

private fun AiTextComponent.looksLikePaddleGlyph(imageWidth: Int, imageHeight: Int): Boolean {
    val maxGlyphWidth = (imageWidth * 0.16f).toInt().coerceAtLeast(18)
    val maxGlyphHeight = (imageHeight * 0.16f).toInt().coerceAtLeast(18)
    if (area !in 3..4500) return false
    if (width > maxGlyphWidth || height > maxGlyphHeight) return false
    if (width <= 1 || height <= 1) return false
    val aspect = width.toFloat() / height.toFloat()
    return aspect in 0.08f..8f
}

private fun paddleGray(color: Int): Int =
    ((((color shr 16) and 0xFF) * 299) + (((color shr 8) and 0xFF) * 587) + ((color and 0xFF) * 114)) / 1000

private fun paddleRectInsideRatio(inner: AiTranslationRect, outer: AiTranslationRect): Float {
    val innerArea = inner.width * inner.height
    if (innerArea <= 0f) return 0f
    return rectIntersectionArea(inner, outer) / innerArea
}

private fun rectIntersectionArea(left: AiTranslationRect, right: AiTranslationRect): Float {
    val x1 = max(left.x, right.x)
    val y1 = max(left.y, right.y)
    val x2 = min(left.x + left.width, right.x + right.width)
    val y2 = min(left.y + left.height, right.y + right.height)
    return max(0f, x2 - x1) * max(0f, y2 - y1)
}

private fun PaddleTextRect.toLocalRegion(
    id: String,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): AiTranslationLocalTextRegion {
    val pixelRect = rect.toPaddleInkBounds(imageWidth, imageHeight) ?: PaddleInkBounds(0, 0, imageWidth, imageHeight)
    val background = estimatePaddleBackgroundColor(pixels, imageWidth, imageHeight, pixelRect)
    val textColor = ensureReadableAiTextColor(if (background == "#FFFFFF") "#111111" else "#F2F2F2", background)
    val direction = detectedTextDirectionForRect(rect, sourceTextProfile)
    val fontScale = when (direction) {
        AiTranslationTextDirection.VERTICAL -> rect.width / 0.045f
        AiTranslationTextDirection.HORIZONTAL -> rect.height / 0.055f
        AiTranslationTextDirection.AUTO -> 1f
    }.coerceIn(0.74f, 1.28f)
    return AiTranslationLocalTextRegion(
        id = id,
        rect = rect,
        textDirection = direction,
        textColor = textColor,
        backgroundColor = background,
        confidence = 0.90f,
        estimatedFontScale = fontScale,
        rotationDegrees = estimatedRegionRotationDegreesForRect(rect, direction, sourceTextProfile)
    )
}

private data class PaddleProbabilityMap(
    val width: Int,
    val height: Int,
    val data: FloatArray
)

private fun OrtSession.Result.firstProbabilityMap(): PaddleProbabilityMap? {
    val output = get(0) ?: return null
    val shape = output.tensorShapeOrNull()
    val value = output.value
    val data = when (value) {
        is Array<*> -> value.flattenFloats()
        is FloatArray -> value
        else -> return null
    }
    val height = shape?.getOrNull(shape.size - 2)?.toInt()?.takeIf { it > 0 }
    val width = shape?.getOrNull(shape.size - 1)?.toInt()?.takeIf { it > 0 }
    if (height == null || width == null || data.size < width * height) return null
    return PaddleProbabilityMap(width = width, height = height, data = data)
}

private fun OnnxValue.tensorShapeOrNull(): LongArray? =
    (info as? TensorInfo)?.shape

private fun Any?.flattenFloats(): FloatArray {
    val values = mutableListOf<Float>()
    fun append(value: Any?) {
        when (value) {
            is Float -> values += value
            is FloatArray -> value.forEach { values += it }
            is Array<*> -> value.forEach(::append)
        }
    }
    append(this)
    return values.toFloatArray()
}

private fun AiTranslationRect.toPaddleInkBounds(imageWidth: Int, imageHeight: Int): PaddleInkBounds? =
    PaddleInkBounds(
        left = (x * imageWidth).roundToInt(),
        top = (y * imageHeight).roundToInt(),
        right = ((x + width) * imageWidth).roundToInt(),
        bottom = ((y + height) * imageHeight).roundToInt()
    ).toSafePaddleInkBounds(imageWidth, imageHeight)

private fun estimatePaddleBackgroundColor(pixels: IntArray, imageWidth: Int, imageHeight: Int, rect: PaddleInkBounds): String {
    if (pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return "#FFFFFF"
    val sampleLeft = (rect.left - 4).coerceAtLeast(0)
    val sampleTop = (rect.top - 4).coerceAtLeast(0)
    val sampleRight = (rect.right + 4).coerceAtMost(imageWidth - 1)
    val sampleBottom = (rect.bottom + 4).coerceAtMost(imageHeight - 1)
    var total = 0L
    var count = 0
    for (y in sampleTop..sampleBottom step 2) {
        for (x in sampleLeft..sampleRight step 2) {
            if (x in rect.left..rect.right && y in rect.top..rect.bottom) continue
            val color = pixels[y * imageWidth + x]
            val gray = ((((color shr 16) and 0xFF) * 299) + (((color shr 8) and 0xFF) * 587) + ((color and 0xFF) * 114)) / 1000
            total += gray
            count += 1
        }
    }
    val average = if (count == 0) 245 else (total / count).toInt()
    return if (average >= 132) "#FFFFFF" else "#111111"
}

private fun Int.roundToMultipleOf32(): Int = ((this + 31) / 32).coerceAtLeast(1) * 32
