package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.usesHorizontalComicRules
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AiPaddleTextDetector(
    private val context: Context,
    private val modelRepository: AiLocalModelRepository,
    private val preferredExecutionProvider: AiPaddleExecutionProvider = AiPaddleExecutionProvider.CPU
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionCache = PaddleOnnxSessionCache(environment)

    @Volatile
    private var nnapiDisabled = false

    internal fun detect(
        bitmap: Bitmap,
        pixels: IntArray,
        pageIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        settings: AiSettings,
        sourceLanguageTag: String,
        maxRegions: Int
    ): AiPaddleDetectionOutput {
        val assets = resolvePaddleAssets(settings) ?: return AiPaddleDetectionOutput.EMPTY
        return runCatching {
            runDetectionModel(
                bitmap = bitmap,
                pixels = pixels,
                pageIndex = pageIndex,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                assets = assets,
                maxRegions = maxRegions,
                sourceTextProfile = settings.sourceTextProfile,
                sourceLanguageTag = sourceLanguageTag,
                translationMode = settings.preferredMode
            )
        }.getOrElse { AiPaddleDetectionOutput.EMPTY }
    }

    override fun close() {
        sessionCache.close()
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
        sourceTextProfile: AiSourceTextProfile,
        sourceLanguageTag: String,
        translationMode: AiTranslationMode
    ): AiPaddleDetectionOutput {
        val preferredConfig = paddleOnnxExecutionConfig(
            tier = assets.tier,
            provider = if (nnapiDisabled) AiPaddleExecutionProvider.CPU else preferredExecutionProvider
        )
        return try {
            runDetectionModelWithConfig(
                bitmap = bitmap,
                pixels = pixels,
                pageIndex = pageIndex,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                assets = assets,
                maxRegions = maxRegions,
                sourceTextProfile = sourceTextProfile,
                sourceLanguageTag = sourceLanguageTag,
                translationMode = translationMode,
                executionConfig = preferredConfig
            )
        } catch (error: Exception) {
            if (preferredConfig.provider != AiPaddleExecutionProvider.NNAPI) throw error
            nnapiDisabled = true
            runDetectionModelWithConfig(
                bitmap = bitmap,
                pixels = pixels,
                pageIndex = pageIndex,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                assets = assets,
                maxRegions = maxRegions,
                sourceTextProfile = sourceTextProfile,
                sourceLanguageTag = sourceLanguageTag,
                translationMode = translationMode,
                executionConfig = paddleOnnxExecutionConfig(assets.tier, AiPaddleExecutionProvider.CPU)
            )
        }
    }

    private fun runDetectionModelWithConfig(
        bitmap: Bitmap,
        pixels: IntArray,
        pageIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        assets: PaddleModelAssets,
        maxRegions: Int,
        sourceTextProfile: AiSourceTextProfile,
        sourceLanguageTag: String,
        translationMode: AiTranslationMode,
        executionConfig: PaddleOnnxExecutionConfig
    ): AiPaddleDetectionOutput {
        val preprocessStartedAt = System.nanoTime()
        val input = bitmap.toPaddleDetectorInput(
            maxSide = paddleDetectorInputMaxSide(
                tier = assets.tier,
                sourceTextProfile = sourceTextProfile,
                translationMode = translationMode
            )
        )
        val preprocessMs = elapsedMilliseconds(preprocessStartedAt)
        val sessionAccess = sessionCache.acquire(assets.detectionModelFile, executionConfig)
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input.data),
            longArrayOf(1, 3, input.height.toLong(), input.width.toLong())
        ).use { tensor ->
            val inferenceStartedAt = System.nanoTime()
            sessionAccess.holder.session.run(mapOf(sessionAccess.holder.inputName to tensor)).use { result ->
                val inferenceMs = elapsedMilliseconds(inferenceStartedAt)
                val postProcessStartedAt = System.nanoTime()
                val probability = result.firstProbabilityMap() ?: return AiPaddleDetectionOutput.EMPTY
                val rects = paddleProbabilityMapToRects(
                    probabilityMap = probability.data,
                    mapWidth = probability.width,
                    mapHeight = probability.height,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    sourceTextProfile = sourceTextProfile,
                    sourceLanguageTag = sourceLanguageTag
                )
                val refinement = refinePaddleTextRectsWithInkTextLinesAndStats(
                    rects = rects,
                    pixels = pixels,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    sourceTextProfile = sourceTextProfile
                )
                val regions = refinement.rects.take(maxRegions).mapIndexed { index, rect ->
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
                val postProcessMs = elapsedMilliseconds(postProcessStartedAt)
                val estimatedPeakBytes = estimatePaddleWorkingSetBytes(
                    sourcePixelCount = pixels.size,
                    input = input,
                    probabilityCount = probability.data.size,
                    localScratchBytes = refinement.peakScratchBytes
                )
                return AiPaddleDetectionOutput(
                    regions = regions,
                    stats = AiLocalDetectionStats(
                        sessionWasReused = sessionAccess.wasReused,
                        sessionAcquireMs = sessionAccess.acquireMs,
                        preprocessMs = preprocessMs,
                        inferenceMs = inferenceMs,
                        postProcessMs = postProcessMs,
                        estimatedPeakWorkingSetBytes = estimatedPeakBytes,
                        regionCount = regions.size,
                        executionProvider = sessionAccess.holder.executionConfig.provider.name
                    )
                )
            }
        }
    }
}

enum class AiPaddleExecutionProvider {
    CPU,
    NNAPI
}

internal data class PaddleOnnxExecutionConfig(
    val provider: AiPaddleExecutionProvider,
    val intraOpThreads: Int
)

internal fun paddleOnnxExecutionConfig(
    tier: AiLocalModelTier,
    provider: AiPaddleExecutionProvider
): PaddleOnnxExecutionConfig {
    val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val requestedThreads = when (tier) {
        AiLocalModelTier.LOW -> 2
        AiLocalModelTier.BALANCED -> 3
        AiLocalModelTier.HIGH -> 4
    }
    return PaddleOnnxExecutionConfig(provider, requestedThreads.coerceAtMost(availableCores))
}

private data class PaddleOnnxSessionKey(
    val modelPath: String,
    val modelLength: Long,
    val modelModifiedAt: Long,
    val executionConfig: PaddleOnnxExecutionConfig
)

private data class PaddleOnnxSessionHolder(
    val sessionOptions: OrtSession.SessionOptions,
    val session: OrtSession,
    val inputName: String,
    val executionConfig: PaddleOnnxExecutionConfig
) : Closeable {
    override fun close() {
        session.close()
        sessionOptions.close()
    }
}

private data class PaddleOnnxSessionAccess(
    val holder: PaddleOnnxSessionHolder,
    val wasReused: Boolean,
    val acquireMs: Long
)

private class PaddleOnnxSessionCache(
    private val environment: OrtEnvironment
) : Closeable {
    private val sessions = linkedMapOf<PaddleOnnxSessionKey, PaddleOnnxSessionHolder>()

    @Synchronized
    fun acquire(modelFile: File, executionConfig: PaddleOnnxExecutionConfig): PaddleOnnxSessionAccess {
        val startedAt = System.nanoTime()
        val key = PaddleOnnxSessionKey(
            modelPath = modelFile.absolutePath,
            modelLength = modelFile.length(),
            modelModifiedAt = modelFile.lastModified(),
            executionConfig = executionConfig
        )
        sessions[key]?.let { holder ->
            return PaddleOnnxSessionAccess(holder, wasReused = true, acquireMs = elapsedMilliseconds(startedAt))
        }
        val options = OrtSession.SessionOptions()
        var session: OrtSession? = null
        try {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(executionConfig.intraOpThreads)
            options.setMemoryPatternOptimization(true)
            options.setCPUArenaAllocator(true)
            if (executionConfig.provider == AiPaddleExecutionProvider.NNAPI) options.addNnapi()
            session = environment.createSession(modelFile.absolutePath, options)
            val inputName = session.inputNames.firstOrNull()
                ?: error("Paddle detection model does not expose an input")
            val holder = PaddleOnnxSessionHolder(options, session, inputName, executionConfig)
            sessions[key] = holder
            return PaddleOnnxSessionAccess(holder, wasReused = false, acquireMs = elapsedMilliseconds(startedAt))
        } catch (error: Exception) {
            session?.close()
            options.close()
            throw error
        }
    }

    @Synchronized
    override fun close() {
        sessions.values.forEach { holder -> runCatching(holder::close) }
        sessions.clear()
    }
}

private fun elapsedMilliseconds(startedAtNanos: Long): Long =
    ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)

private fun estimatePaddleWorkingSetBytes(
    sourcePixelCount: Int,
    input: PaddleDetectorInput,
    probabilityCount: Int,
    localScratchBytes: Long
): Long =
    sourcePixelCount.toLong() * Int.SIZE_BYTES +
        input.data.size.toLong() * Float.SIZE_BYTES +
        input.width.toLong() * input.height * Int.SIZE_BYTES +
        probabilityCount.toLong() * Float.SIZE_BYTES +
        localScratchBytes

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
    sourceTextProfile: AiSourceTextProfile = AiSourceTextProfile.AUTO,
    sourceLanguageTag: String = "",
    threshold: Float? = null
): List<PaddleTextRect> {
    if (probabilityMap.size < mapWidth * mapHeight || mapWidth <= 0 || mapHeight <= 0) return emptyList()
    val parameters = paddleDbPostProcessParameters(sourceTextProfile, sourceLanguageTag)
    val bitmapThreshold = threshold ?: parameters.bitmapThreshold
    val visited = BooleanArray(mapWidth * mapHeight)
    val queue = IntArray(mapWidth * mapHeight)
    val rects = mutableListOf<PaddleTextRect>()
    for (start in 0 until mapWidth * mapHeight) {
        if (visited[start] || probabilityMap[start] < bitmapThreshold) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        var left = start % mapWidth
        var right = left
        var top = start / mapWidth
        var bottom = top
        var area = 0
        var totalScore = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumYY = 0.0
        var sumXY = 0.0
        while (head < tail) {
            val index = queue[head++]
            val x = index % mapWidth
            val y = index / mapWidth
            area += 1
            totalScore += probabilityMap[index]
            sumX += x
            sumY += y
            sumXX += x.toDouble() * x
            sumYY += y.toDouble() * y
            sumXY += x.toDouble() * y
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
            fun visit(nextX: Int, nextY: Int) {
                if (nextX !in 0 until mapWidth || nextY !in 0 until mapHeight) return
                val next = nextY * mapWidth + nextX
                if (visited[next] || probabilityMap[next] < bitmapThreshold) return
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
        val meanScore = (totalScore / area.coerceAtLeast(1)).toFloat()
        val expansionX = max(1, (width * (parameters.contourExpansionRatio - 1f) * 0.5f).roundToInt())
        val expansionY = max(1, (height * (parameters.contourExpansionRatio - 1f) * 0.5f).roundToInt())
        val expandedLeft = (left - expansionX).coerceAtLeast(0)
        val expandedTop = (top - expansionY).coerceAtLeast(0)
        val expandedRight = (right + expansionX).coerceAtMost(mapWidth - 1)
        val expandedBottom = (bottom + expansionY).coerceAtMost(mapHeight - 1)
        val sourceRect = normalizedSourceRectFromDetectionPixels(
            left = expandedLeft,
            top = expandedTop,
            right = expandedRight,
            bottom = expandedBottom,
            detectionImageWidth = mapWidth,
            detectionImageHeight = mapHeight,
            sourceImageWidth = sourceWidth,
            sourceImageHeight = sourceHeight
        )
        if (
            area >= parameters.minimumComponentPixels &&
            width >= 2 &&
            height >= 2 &&
            meanScore >= parameters.boxScoreThreshold &&
            sourceRect.width <= parameters.maximumNormalizedSpan &&
            sourceRect.height <= parameters.maximumNormalizedSpan
        ) {
            val fillRatio = area.toFloat() / (width * height).coerceAtLeast(1)
            val geometryQuality = paddleRectGeometryQuality(width, height, fillRatio, area)
            rects += PaddleTextRect(
                rect = sourceRect,
                area = area,
                meanScore = meanScore,
                geometryQuality = geometryQuality,
                rotationDegrees = paddleComponentRotationDegrees(
                    area = area,
                    sumX = sumX,
                    sumY = sumY,
                    sumXX = sumXX,
                    sumYY = sumYY,
                    sumXY = sumXY
                )
            )
        }
    }
    return filterBroadPaddleTextRects(rects).sortedByDescending { it.area }
}

internal data class PaddleTextRect(
    val rect: AiTranslationRect,
    val area: Int,
    val meanScore: Float = 0.90f,
    val geometryQuality: Float = 0.80f,
    val rotationDegrees: Float = 0f
)

internal data class PaddleDbPostProcessParameters(
    val bitmapThreshold: Float,
    val boxScoreThreshold: Float,
    val contourExpansionRatio: Float,
    val minimumComponentPixels: Int,
    val maximumNormalizedSpan: Float = 0.45f
)

internal fun paddleDbPostProcessParameters(
    sourceTextProfile: AiSourceTextProfile,
    sourceLanguageTag: String
): PaddleDbPostProcessParameters {
    val language = sourceLanguageTag.trim().lowercase().substringBefore('-')
    return when {
        language == "ja" || sourceTextProfile == AiSourceTextProfile.JAPANESE_MANGA ->
            PaddleDbPostProcessParameters(0.27f, 0.49f, 1.24f, 5)
        language == "ko" || sourceTextProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON ->
            PaddleDbPostProcessParameters(0.29f, 0.52f, 1.18f, 6)
        language == "en" ->
            PaddleDbPostProcessParameters(0.32f, 0.56f, 1.12f, 6)
        else ->
            PaddleDbPostProcessParameters(0.30f, 0.53f, 1.16f, 6)
    }
}

private fun paddleRectGeometryQuality(
    width: Int,
    height: Int,
    fillRatio: Float,
    area: Int
): Float {
    val aspectRatio = max(width, height).toFloat() / min(width, height).coerceAtLeast(1)
    val fillQuality = (fillRatio / 0.58f).coerceIn(0f, 1f)
    val sizeQuality = (area / 24f).coerceIn(0f, 1f)
    val aspectQuality = when {
        aspectRatio <= 12f -> 1f
        aspectRatio <= 24f -> 0.72f
        else -> 0.42f
    }
    return (fillQuality * 0.50f + sizeQuality * 0.30f + aspectQuality * 0.20f).coerceIn(0f, 1f)
}

private fun paddleComponentRotationDegrees(
    area: Int,
    sumX: Double,
    sumY: Double,
    sumXX: Double,
    sumYY: Double,
    sumXY: Double
): Float {
    if (area < 2) return 0f
    val count = area.toDouble()
    val covarianceXX = sumXX / count - (sumX / count) * (sumX / count)
    val covarianceYY = sumYY / count - (sumY / count) * (sumY / count)
    val covarianceXY = sumXY / count - (sumX / count) * (sumY / count)
    var degrees = Math.toDegrees(0.5 * kotlin.math.atan2(2.0 * covarianceXY, covarianceXX - covarianceYY)).toFloat()
    while (degrees > 45f) degrees -= 90f
    while (degrees < -45f) degrees += 90f
    return degrees.takeIf { abs(it) >= 0.75f } ?: 0f
}

internal fun refinePaddleTextRectsWithInkTextLines(
    rects: List<PaddleTextRect>,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): List<PaddleTextRect> =
    refinePaddleTextRectsWithInkTextLinesAndStats(
        rects = rects,
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        sourceTextProfile = sourceTextProfile
    ).rects

private data class PaddleRectRefinementResult(
    val rects: List<PaddleTextRect>,
    val peakScratchBytes: Long
)

private fun refinePaddleTextRectsWithInkTextLinesAndStats(
    rects: List<PaddleTextRect>,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): PaddleRectRefinementResult {
    if (rects.isEmpty() || pixels.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
        return PaddleRectRefinementResult(rects, 0L)
    }
    val peakScratchBytes = rects.maxOfOrNull { textRect ->
        paddleInkScratchBytes(textRect.rect, imageWidth, imageHeight)
    } ?: 0L
    val refined = rects.flatMap { textRect ->
        splitPaddleTextRectIntoInkTextLineRects(
            rect = textRect.rect,
            pixels = pixels,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceTextProfile = sourceTextProfile
        ).map { splitRect ->
            textRect.copy(rect = splitRect)
        }
    }.sortedWith(paddleTextRectReadingOrder(sourceTextProfile))
    return PaddleRectRefinementResult(refined, peakScratchBytes)
}

internal fun paddleInkScratchBytes(
    rect: AiTranslationRect,
    imageWidth: Int,
    imageHeight: Int
): Long {
    val bounds = rect.toPaddleInkBounds(imageWidth, imageHeight) ?: return 0L
    val area = (bounds.right - bounds.left).coerceAtLeast(0).toLong() *
        (bounds.bottom - bounds.top).coerceAtLeast(0)
    return area * (Byte.SIZE_BYTES + Int.SIZE_BYTES)
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
        !sourceTextProfile.usesHorizontalComicRules() &&
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
    val localWidth = safeBounds.right - safeBounds.left
    val localHeight = safeBounds.bottom - safeBounds.top
    val scratch = PaddleInkScratch(
        mask = ByteArray(localWidth * localHeight),
        queue = IntArray(localWidth * localHeight)
    )
    val darkComponents = findPaddleInkComponentsForRect(
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        bounds = safeBounds,
        backgroundGray = backgroundGray,
        detectLightText = false,
        scratch = scratch
    )
    val lightComponents = findPaddleInkComponentsForRect(
        pixels = pixels,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        bounds = safeBounds,
        backgroundGray = backgroundGray,
        detectLightText = true,
        scratch = scratch
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
    detectLightText: Boolean,
    scratch: PaddleInkScratch
): List<AiTextComponent> {
    val safeLeft = bounds.left
    val safeTop = bounds.top
    val safeRight = bounds.right
    val safeBottom = bounds.bottom
    val localWidth = safeRight - safeLeft
    val localHeight = safeBottom - safeTop
    val localArea = localWidth * localHeight
    if (localArea <= 0 || scratch.mask.size < localArea || scratch.queue.size < localArea) return emptyList()
    scratch.mask.fill(0, 0, localArea)
    val darkThreshold = min(backgroundGray - 28, 172).coerceAtLeast(32)
    val lightThreshold = max(backgroundGray + 28, 176).coerceAtMost(238)
    for (localY in 0 until localHeight) {
        val sourceY = safeTop + localY
        for (localX in 0 until localWidth) {
            val sourceX = safeLeft + localX
            val value = paddleGray(pixels[sourceY * imageWidth + sourceX])
            val ink = if (detectLightText) value >= lightThreshold else value <= darkThreshold
            if (ink) scratch.mask[localY * localWidth + localX] = 1
        }
    }

    val components = mutableListOf<AiTextComponent>()
    for (startLocalY in 0 until localHeight) {
        for (startLocalX in 0 until localWidth) {
            val start = startLocalY * localWidth + startLocalX
            if (scratch.mask[start] != 1.toByte()) continue
            var head = 0
            var tail = 0
            scratch.queue[tail++] = start
            scratch.mask[start] = 2
            var left = safeLeft + startLocalX
            var right = left
            var top = safeTop + startLocalY
            var bottom = top
            var area = 0
            var darkPixels = 0
            var lightPixels = 0
            while (head < tail) {
                val localIndex = scratch.queue[head++]
                val localX = localIndex % localWidth
                val localY = localIndex / localWidth
                val x = safeLeft + localX
                val y = safeTop + localY
                val gray = paddleGray(pixels[y * imageWidth + x])
                area += 1
                if (gray < 128) darkPixels += 1 else lightPixels += 1
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
                fun visit(nextLocalX: Int, nextLocalY: Int) {
                    if (nextLocalX !in 0 until localWidth || nextLocalY !in 0 until localHeight) return
                    val next = nextLocalY * localWidth + nextLocalX
                    if (scratch.mask[next] != 1.toByte()) return
                    scratch.mask[next] = 2
                    scratch.queue[tail++] = next
                }
                visit(localX - 1, localY)
                visit(localX + 1, localY)
                visit(localX, localY - 1)
                visit(localX, localY + 1)
            }
            val component = AiTextComponent(left, top, right, bottom, area, darkPixels, lightPixels)
            if (component.looksLikePaddleGlyph(imageWidth, imageHeight)) {
                components += component
            }
        }
    }
    return components
}

private data class PaddleInkScratch(
    val mask: ByteArray,
    val queue: IntArray
)

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
    val histogram = IntArray(256)
    var sampleCount = 0
    for (y in bounds.top until bounds.bottom step 2) {
        for (x in bounds.left until bounds.right step 2) {
            histogram[paddleGray(pixels[y * imageWidth + x])] += 1
            sampleCount += 1
        }
    }
    if (sampleCount == 0) return 245
    val target = sampleCount * 3 / 4
    var accumulated = 0
    histogram.forEachIndexed { gray, count ->
        accumulated += count
        if (accumulated >= target) return gray
    }
    return 245
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
    val confidence = paddleRegionConfidence(this)
    val detectedRotation = rotationDegrees.takeIf { abs(it) >= 0.75f }
        ?: estimatedRegionRotationDegreesForRect(rect, direction, sourceTextProfile)
    return AiTranslationLocalTextRegion(
        id = id,
        rect = rect,
        textDirection = direction,
        textColor = textColor,
        backgroundColor = background,
        confidence = confidence,
        estimatedFontScale = fontScale,
        rotationDegrees = detectedRotation
    )
}

internal fun paddleRegionConfidence(textRect: PaddleTextRect): Float =
    (textRect.meanScore * 0.78f + textRect.geometryQuality * 0.22f).coerceIn(0.35f, 0.99f)

private data class PaddleProbabilityMap(
    val width: Int,
    val height: Int,
    val data: FloatArray
)

private fun OrtSession.Result.firstProbabilityMap(): PaddleProbabilityMap? {
    val output = get(0) as? OnnxTensor ?: return null
    val shape = output.tensorShapeOrNull()
    val height = shape?.getOrNull(shape.size - 2)?.toInt()?.takeIf { it > 0 }
    val width = shape?.getOrNull(shape.size - 1)?.toInt()?.takeIf { it > 0 }
    if (height == null || width == null) return null
    val elementCount = width * height
    val buffer = output.floatBuffer ?: return null
    if (buffer.remaining() < elementCount) return null
    if (buffer.remaining() > elementCount) buffer.position(buffer.limit() - elementCount)
    val data = FloatArray(elementCount)
    buffer.get(data)
    return PaddleProbabilityMap(width = width, height = height, data = data)
}

private fun OnnxTensor.tensorShapeOrNull(): LongArray? =
    (info as? TensorInfo)?.shape

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
