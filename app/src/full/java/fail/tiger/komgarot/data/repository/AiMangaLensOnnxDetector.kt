package fail.tiger.komgarot.data.repository

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AiMangaLensOnnxDetector(
    private val modelRepository: AiLocalModelRepository,
    private val inputSize: Int = MANGA_LENS_INPUT_SIZE
) : AiBubbleRegionDetector {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()

    @Volatile
    private var session: OrtSession? = null

    override fun cacheVersion(): String = if (modelRepository.isMangaLensInstalled()) {
        "mangalens:${MANGA_LENS_MODEL_ASSET.sha256}:$MANGA_LENS_LAYOUT_VERSION"
    } else {
        "bubble-none:$MANGA_LENS_LAYOUT_VERSION"
    }

    override fun detect(source: IntArray, width: Int, height: Int): List<AiBubbleRegion> {
        require(width > 0 && height > 0 && source.size == width * height)
        if (!modelRepository.isMangaLensInstalled()) return emptyList()
        val scale = min(inputSize.toFloat() / width, inputSize.toFloat() / height)
        val resizedWidth = (width * scale).roundToInt().coerceIn(1, inputSize)
        val resizedHeight = (height * scale).roundToInt().coerceIn(1, inputSize)
        val padLeft = (inputSize - resizedWidth) / 2
        val padTop = (inputSize - resizedHeight) / 2
        val sourceBitmap = Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888)
        val resizedBitmap = Bitmap.createScaledBitmap(sourceBitmap, resizedWidth, resizedHeight, true)
        if (resizedBitmap !== sourceBitmap) sourceBitmap.recycle()
        return try {
            val resized = IntArray(resizedWidth * resizedHeight)
            resizedBitmap.getPixels(resized, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)
            val plane = inputSize * inputSize
            val input = FloatArray(plane * 3) { MANGA_LENS_PADDING_VALUE }
            for (y in 0 until resizedHeight) {
                for (x in 0 until resizedWidth) {
                    val color = resized[y * resizedWidth + x]
                    val index = (padTop + y) * inputSize + padLeft + x
                    input[index] = (color ushr 16 and 0xFF) / 255f
                    input[plane + index] = (color ushr 8 and 0xFF) / 255f
                    input[plane * 2 + index] = (color and 0xFF) / 255f
                }
            }
            val output = runModel(input)
            decodeMangaLensBubbleRegions(
                prediction = output.prediction,
                candidateCount = output.prediction.size / MANGA_LENS_OUTPUT_CHANNELS,
                prototype = output.prototype,
                prototypeWidth = output.prototypeWidth,
                prototypeHeight = output.prototypeHeight,
                sourcePixels = source,
                sourceWidth = width,
                sourceHeight = height,
                inputSize = inputSize,
                confidenceThreshold = MANGA_LENS_CONFIDENCE_THRESHOLD
            )
        } finally {
            resizedBitmap.recycle()
        }
    }

    private fun runModel(input: FloatArray): MangaLensModelOutput = synchronized(sessionLock) {
        val activeSession = session ?: createSession().also { session = it }
        val inputName = activeSession.inputNames.firstOrNull() ?: error("MangaLens input tensor is missing.")
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        ).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val predictionTensor = result[0] as? OnnxTensor ?: error("MangaLens prediction tensor is missing.")
                val prototypeTensor = result[1] as? OnnxTensor ?: error("MangaLens mask prototype tensor is missing.")
                val predictionBuffer = predictionTensor.floatBuffer
                    ?: error("MangaLens prediction tensor is not float32.")
                val prototypeBuffer = prototypeTensor.floatBuffer
                    ?: error("MangaLens mask prototype tensor is not float32.")
                val prototypeShape = prototypeTensor.info.shape
                MangaLensModelOutput(
                    prediction = FloatArray(predictionBuffer.remaining()).also(predictionBuffer::get),
                    prototype = FloatArray(prototypeBuffer.remaining()).also(prototypeBuffer::get),
                    prototypeWidth = prototypeShape.getOrNull(3)?.toInt() ?: error("MangaLens prototype width is missing."),
                    prototypeHeight = prototypeShape.getOrNull(2)?.toInt() ?: error("MangaLens prototype height is missing.")
                )
            }
        }
    }

    private fun createSession(): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return options.use {
            environment.createSession(modelRepository.mangaLensModelFile().absolutePath, it)
        }
    }

    override fun close() {
        synchronized(sessionLock) {
            session?.close()
            session = null
        }
    }
}

private data class MangaLensModelOutput(
    val prediction: FloatArray,
    val prototype: FloatArray,
    val prototypeWidth: Int,
    val prototypeHeight: Int
)

internal fun decodeMangaLensBubbleRegions(
    prediction: FloatArray,
    candidateCount: Int,
    prototype: FloatArray,
    prototypeWidth: Int,
    prototypeHeight: Int,
    sourcePixels: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    inputSize: Int,
    confidenceThreshold: Float
): List<AiBubbleRegion> {
    if (candidateCount <= 0 || prediction.size < MANGA_LENS_OUTPUT_CHANNELS * candidateCount) return emptyList()
    if (prototypeWidth <= 0 || prototypeHeight <= 0 || prototype.size < MANGA_LENS_MASK_CHANNELS * prototypeWidth * prototypeHeight) {
        return decodeMangaLensBubbleBoxes(
            output = prediction,
            candidateCount = candidateCount,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            inputSize = inputSize,
            confidenceThreshold = confidenceThreshold
        ).map { AiBubbleRegion(rect = it, safeTextRect = it.insetForMangaLensText()) }
    }
    val transform = mangaLensTransform(sourceWidth, sourceHeight, inputSize)
    val candidates = decodeMangaLensCandidates(
        output = prediction,
        candidateCount = candidateCount,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        inputSize = inputSize,
        confidenceThreshold = confidenceThreshold
    )
    return candidates.map { candidate ->
        val mask = decodeMangaLensMask(
            prediction = prediction,
            candidateIndex = candidate.index,
            candidateCount = candidateCount,
            prototype = prototype,
            prototypeWidth = prototypeWidth,
            prototypeHeight = prototypeHeight,
            modelBox = candidate.modelBox,
            inputSize = inputSize
        )
        val outline = mangaLensMaskOutline(
            mask = mask,
            prototypeWidth = prototypeWidth,
            prototypeHeight = prototypeHeight,
            transform = transform,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            inputSize = inputSize
        )
        val sampledColors = mangaLensMaskColors(
            mask = mask,
            prototypeWidth = prototypeWidth,
            prototypeHeight = prototypeHeight,
            sourcePixels = sourcePixels,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            transform = transform,
            inputSize = inputSize
        )
        val background = sampledColors.medianColor()
        AiBubbleRegion(
            rect = candidate.rect,
            safeTextRect = outline.safeTextRectOrNull() ?: candidate.rect.insetForMangaLensText(),
            outline = outline,
            backgroundColor = background.toHexColor(),
            solidFill = sampledColors.isSolidAround(background)
        )
    }
}

internal fun decodeMangaLensBubbleBoxes(
    output: FloatArray,
    candidateCount: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    inputSize: Int,
    confidenceThreshold: Float
): List<AiTranslationRect> {
    return decodeMangaLensCandidates(
        output = output,
        candidateCount = candidateCount,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        inputSize = inputSize,
        confidenceThreshold = confidenceThreshold
    ).map(MangaLensBubbleCandidate::rect)
}

private fun decodeMangaLensCandidates(
    output: FloatArray,
    candidateCount: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    inputSize: Int,
    confidenceThreshold: Float
): List<MangaLensBubbleCandidate> {
    if (candidateCount <= 0 || output.size < MANGA_LENS_OUTPUT_CHANNELS * candidateCount) return emptyList()
    val transform = mangaLensTransform(sourceWidth, sourceHeight, inputSize)
    val candidates = buildList {
        for (index in 0 until candidateCount) {
            val confidence = output[4 * candidateCount + index]
            if (confidence < confidenceThreshold) continue
            val centerX = output[index]
            val centerY = output[candidateCount + index]
            val boxWidth = output[candidateCount * 2 + index]
            val boxHeight = output[candidateCount * 3 + index]
            val modelBox = MangaLensModelBox(
                left = centerX - boxWidth / 2f,
                top = centerY - boxHeight / 2f,
                right = centerX + boxWidth / 2f,
                bottom = centerY + boxHeight / 2f
            )
            val left = ((modelBox.left - transform.padLeft) / transform.scale).coerceIn(0f, sourceWidth.toFloat())
            val top = ((modelBox.top - transform.padTop) / transform.scale).coerceIn(0f, sourceHeight.toFloat())
            val right = ((modelBox.right - transform.padLeft) / transform.scale).coerceIn(0f, sourceWidth.toFloat())
            val bottom = ((modelBox.bottom - transform.padTop) / transform.scale).coerceIn(0f, sourceHeight.toFloat())
            if (right <= left || bottom <= top) continue
            add(
                MangaLensBubbleCandidate(
                    index = index,
                    rect = AiTranslationRect(
                        x = left / sourceWidth,
                        y = top / sourceHeight,
                        width = (right - left) / sourceWidth,
                        height = (bottom - top) / sourceHeight
                    ),
                    modelBox = modelBox,
                    confidence = confidence
                )
            )
        }
    }
    val selected = mutableListOf<MangaLensBubbleCandidate>()
    candidates.sortedByDescending(MangaLensBubbleCandidate::confidence).forEach { candidate ->
        if (selected.none { bubbleIntersectionOverUnion(candidate.rect, it.rect) > MANGA_LENS_NMS_IOU_THRESHOLD }) {
            selected += candidate
        }
    }
    return selected
}

private data class MangaLensBubbleCandidate(
    val index: Int,
    val rect: AiTranslationRect,
    val modelBox: MangaLensModelBox,
    val confidence: Float
)

private data class MangaLensModelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private data class MangaLensTransform(
    val scale: Float,
    val padLeft: Float,
    val padTop: Float
)

private fun mangaLensTransform(sourceWidth: Int, sourceHeight: Int, inputSize: Int): MangaLensTransform {
    val scale = min(inputSize.toFloat() / sourceWidth, inputSize.toFloat() / sourceHeight)
    val resizedWidth = (sourceWidth * scale).roundToInt().coerceIn(1, inputSize)
    val resizedHeight = (sourceHeight * scale).roundToInt().coerceIn(1, inputSize)
    return MangaLensTransform(
        scale = scale,
        padLeft = ((inputSize - resizedWidth) / 2).toFloat(),
        padTop = ((inputSize - resizedHeight) / 2).toFloat()
    )
}

private fun decodeMangaLensMask(
    prediction: FloatArray,
    candidateIndex: Int,
    candidateCount: Int,
    prototype: FloatArray,
    prototypeWidth: Int,
    prototypeHeight: Int,
    modelBox: MangaLensModelBox,
    inputSize: Int
): BooleanArray {
    val spatialSize = prototypeWidth * prototypeHeight
    val mask = BooleanArray(spatialSize)
    val left = (modelBox.left * prototypeWidth / inputSize).toInt().coerceIn(0, prototypeWidth - 1)
    val top = (modelBox.top * prototypeHeight / inputSize).toInt().coerceIn(0, prototypeHeight - 1)
    val right = kotlin.math.ceil(modelBox.right * prototypeWidth / inputSize).toInt().coerceIn(left + 1, prototypeWidth)
    val bottom = kotlin.math.ceil(modelBox.bottom * prototypeHeight / inputSize).toInt().coerceIn(top + 1, prototypeHeight)
    for (y in top until bottom) {
        for (x in left until right) {
            val spatialIndex = y * prototypeWidth + x
            var logit = 0f
            for (channel in 0 until MANGA_LENS_MASK_CHANNELS) {
                val coefficient = prediction[(MANGA_LENS_MASK_COEFFICIENT_OFFSET + channel) * candidateCount + candidateIndex]
                logit += coefficient * prototype[channel * spatialSize + spatialIndex]
            }
            mask[spatialIndex] = logit >= 0f
        }
    }
    return mask
}

private fun mangaLensMaskOutline(
    mask: BooleanArray,
    prototypeWidth: Int,
    prototypeHeight: Int,
    transform: MangaLensTransform,
    sourceWidth: Int,
    sourceHeight: Int,
    inputSize: Int
): List<AiTranslationPoint> {
    val rows = buildList {
        for (y in 0 until prototypeHeight) {
            var left = prototypeWidth
            var right = -1
            for (x in 0 until prototypeWidth) {
                if (mask[y * prototypeWidth + x]) {
                    left = min(left, x)
                    right = max(right, x)
                }
            }
            if (right >= left) add(Triple(y, left, right))
        }
    }
    if (rows.size < 2) return emptyList()
    val verticalErode = if (rows.size >= 8) {
        max(1, (rows.size * MANGA_LENS_MASK_ERODE_RATIO).roundToInt())
    } else {
        0
    }
    val erodedRows = rows.drop(verticalErode).dropLast(verticalErode)
    if (erodedRows.size < 2) return emptyList()
    val sampleStep = max(1, erodedRows.size / MANGA_LENS_OUTLINE_MAX_ROWS)
    val sampled = erodedRows.filterIndexed { index, _ -> index % sampleStep == 0 }.toMutableList()
    if (sampled.last() != erodedRows.last()) sampled += erodedRows.last()
    fun point(y: Int, x: Float): AiTranslationPoint {
        val modelX = (x + 0.5f) * inputSize / prototypeWidth
        val modelY = (y + 0.5f) * inputSize / prototypeHeight
        return AiTranslationPoint(
            x = ((modelX - transform.padLeft) / transform.scale / sourceWidth).coerceIn(0f, 1f),
            y = ((modelY - transform.padTop) / transform.scale / sourceHeight).coerceIn(0f, 1f)
        )
    }
    val leftSide = sampled.map { (y, left, right) ->
        val inset = max(1f, (right - left + 1) * MANGA_LENS_MASK_ERODE_RATIO)
        point(y, left + inset)
    }
    val rightSide = sampled.asReversed().map { (y, left, right) ->
        val inset = max(1f, (right - left + 1) * MANGA_LENS_MASK_ERODE_RATIO)
        point(y, right - inset)
    }
    return (leftSide + rightSide).distinct()
}

private fun mangaLensMaskColors(
    mask: BooleanArray,
    prototypeWidth: Int,
    prototypeHeight: Int,
    sourcePixels: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    transform: MangaLensTransform,
    inputSize: Int
): List<Int> {
    if (sourcePixels.size < sourceWidth * sourceHeight) return emptyList()
    val colors = ArrayList<Int>()
    val sampleStep = max(1, min(prototypeWidth, prototypeHeight) / 96)
    for (y in 0 until prototypeHeight step sampleStep) {
        for (x in 0 until prototypeWidth step sampleStep) {
            if (!mask[y * prototypeWidth + x]) continue
            val modelX = (x + 0.5f) * inputSize / prototypeWidth
            val modelY = (y + 0.5f) * inputSize / prototypeHeight
            val sourceX = ((modelX - transform.padLeft) / transform.scale).roundToInt()
            val sourceY = ((modelY - transform.padTop) / transform.scale).roundToInt()
            if (sourceX in 0 until sourceWidth && sourceY in 0 until sourceHeight) {
                colors += sourcePixels[sourceY * sourceWidth + sourceX]
            }
        }
    }
    return colors
}

private fun List<Int>.medianColor(): Int {
    if (isEmpty()) return 0xFFFFFFFF.toInt()
    fun median(shift: Int): Int = map { it ushr shift and 0xFF }.sorted()[size / 2]
    return (0xFF shl 24) or (median(16) shl 16) or (median(8) shl 8) or median(0)
}

private fun List<Int>.isSolidAround(median: Int): Boolean {
    if (isEmpty()) return false
    val medianRed = median ushr 16 and 0xFF
    val medianGreen = median ushr 8 and 0xFF
    val medianBlue = median and 0xFF
    val close = count { color ->
        kotlin.math.abs((color ushr 16 and 0xFF) - medianRed) <= MANGA_LENS_SOLID_CHANNEL_TOLERANCE &&
            kotlin.math.abs((color ushr 8 and 0xFF) - medianGreen) <= MANGA_LENS_SOLID_CHANNEL_TOLERANCE &&
            kotlin.math.abs((color and 0xFF) - medianBlue) <= MANGA_LENS_SOLID_CHANNEL_TOLERANCE
    }
    return close.toFloat() / size >= MANGA_LENS_SOLID_PIXEL_RATIO
}

private fun Int.toHexColor(): String =
    "#%02X%02X%02X".format(this ushr 16 and 0xFF, this ushr 8 and 0xFF, this and 0xFF)

private fun List<AiTranslationPoint>.safeTextRectOrNull(): AiTranslationRect? {
    if (size < 4) return null
    val bounds = AiTranslationRect(
        x = minOf { it.x },
        y = minOf { it.y },
        width = maxOf { it.x } - minOf { it.x },
        height = maxOf { it.y } - minOf { it.y }
    )
    return bounds.insetForMangaLensText()
}

private fun AiTranslationRect.insetForMangaLensText(): AiTranslationRect {
    val horizontalInset = width * MANGA_LENS_TEXT_HORIZONTAL_INSET_RATIO
    val verticalInset = height * MANGA_LENS_TEXT_VERTICAL_INSET_RATIO
    return AiTranslationRect(
        x = x + horizontalInset,
        y = y + verticalInset,
        width = (width - horizontalInset * 2f).coerceAtLeast(0f),
        height = (height - verticalInset * 2f).coerceAtLeast(0f)
    )
}

private fun bubbleIntersectionOverUnion(left: AiTranslationRect, right: AiTranslationRect): Float {
    val intersectionWidth = (min(left.x + left.width, right.x + right.width) - max(left.x, right.x)).coerceAtLeast(0f)
    val intersectionHeight = (min(left.y + left.height, right.y + right.height) - max(left.y, right.y)).coerceAtLeast(0f)
    val intersection = intersectionWidth * intersectionHeight
    val union = left.width * left.height + right.width * right.height - intersection
    return if (union <= 0f) 0f else intersection / union
}

private const val MANGA_LENS_INPUT_SIZE = 1600
private const val MANGA_LENS_OUTPUT_CHANNELS = 37
private const val MANGA_LENS_MASK_CHANNELS = 32
private const val MANGA_LENS_MASK_COEFFICIENT_OFFSET = 5
private const val MANGA_LENS_CONFIDENCE_THRESHOLD = 0.45f
private const val MANGA_LENS_NMS_IOU_THRESHOLD = 0.5f
private const val MANGA_LENS_PADDING_VALUE = 114f / 255f
private const val MANGA_LENS_LAYOUT_VERSION = "bubble-mask-layout-v2"
private const val MANGA_LENS_MASK_ERODE_RATIO = 0.02f
private const val MANGA_LENS_OUTLINE_MAX_ROWS = 24
private const val MANGA_LENS_TEXT_HORIZONTAL_INSET_RATIO = 0.18f
private const val MANGA_LENS_TEXT_VERTICAL_INSET_RATIO = 0.14f
private const val MANGA_LENS_SOLID_CHANNEL_TOLERANCE = 24
private const val MANGA_LENS_SOLID_PIXEL_RATIO = 0.72f
