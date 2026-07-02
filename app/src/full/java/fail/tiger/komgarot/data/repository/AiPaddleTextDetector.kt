package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
        return PaddleModelAssets(detectionModelFile = detectionModel)
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
        val input = bitmap.toPaddleDetectorInput()
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
                    return rects.take(maxRegions).mapIndexed { index, rect ->
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
    val detectionModelFile: File
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
    return rects.sortedByDescending { it.area }
}

internal data class PaddleTextRect(
    val rect: AiTranslationRect,
    val area: Int
)

private fun PaddleTextRect.toLocalRegion(
    id: String,
    pixels: IntArray,
    imageWidth: Int,
    imageHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    sourceTextProfile: AiSourceTextProfile
): AiTranslationLocalTextRegion {
    val pixelRect = rect.toPixelRect(imageWidth, imageHeight)
    val background = estimatePaddleBackgroundColor(pixels, imageWidth, imageHeight, pixelRect)
    val textColor = ensureReadableAiTextColor(if (background == "#FFFFFF") "#111111" else "#F2F2F2", background)
    val direction = detectedTextDirectionForRect(rect, sourceTextProfile)
    val fontScale = when (direction) {
        AiTranslationTextDirection.VERTICAL -> rect.width / 0.045f
        AiTranslationTextDirection.HORIZONTAL -> rect.height / 0.055f
        AiTranslationTextDirection.AUTO -> 1f
    }.coerceIn(0.55f, 1.20f)
    return AiTranslationLocalTextRegion(
        id = id,
        rect = rect,
        textDirection = direction,
        textColor = textColor,
        backgroundColor = background,
        confidence = 0.90f,
        estimatedFontScale = fontScale
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

private fun AiTranslationRect.toPixelRect(imageWidth: Int, imageHeight: Int): Rect = Rect(
    (x * imageWidth).roundToInt().coerceIn(0, imageWidth - 1),
    (y * imageHeight).roundToInt().coerceIn(0, imageHeight - 1),
    ((x + width) * imageWidth).roundToInt().coerceIn(1, imageWidth),
    ((y + height) * imageHeight).roundToInt().coerceIn(1, imageHeight)
)

private fun estimatePaddleBackgroundColor(pixels: IntArray, imageWidth: Int, imageHeight: Int, rect: Rect): String {
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
