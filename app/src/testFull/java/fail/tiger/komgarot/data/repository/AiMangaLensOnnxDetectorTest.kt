package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMangaLensOnnxDetectorTest {
    @Test
    fun segmentationOutputBuildsInsetBubbleOutlineAndSafeTextBounds() {
        val candidateCount = 1
        val prediction = FloatArray(37 * candidateCount)
        prediction[0] = 8f
        prediction[1] = 8f
        prediction[2] = 12f
        prediction[3] = 12f
        prediction[4] = 0.9f
        prediction[5] = 8f
        val prototype = FloatArray(32 * 4 * 4) { index ->
            val spatial = index % 16
            val x = spatial % 4
            val y = spatial / 4
            if (index / 16 == 0 && x in 1..2 && y in 1..2) 1f else -1f
        }

        val bubble = decodeMangaLensBubbleRegions(
            prediction = prediction,
            candidateCount = candidateCount,
            prototype = prototype,
            prototypeWidth = 4,
            prototypeHeight = 4,
            sourcePixels = IntArray(16 * 16) { 0xFFF8F8F8.toInt() },
            sourceWidth = 16,
            sourceHeight = 16,
            inputSize = 16,
            confidenceThreshold = 0.45f
        ).single()

        assertTrue(bubble.outline.size >= 4)
        assertTrue(bubble.safeTextRect.x >= bubble.rect.x)
        assertTrue(bubble.safeTextRect.y >= bubble.rect.y)
        assertTrue(bubble.safeTextRect.x + bubble.safeTextRect.width <= bubble.rect.x + bubble.rect.width)
        assertTrue(bubble.safeTextRect.y + bubble.safeTextRect.height <= bubble.rect.y + bubble.rect.height)
        assertTrue(bubble.solidFill)
    }

    @Test
    fun texturedBubbleKeepsFillRestrictedToSourceTextMask() {
        val pixels = IntArray(16 * 16) { index ->
            val x = index % 16
            val y = index / 16
            if ((x / 4 + y / 4) % 2 == 0) 0xFF202020.toInt() else 0xFFF0F0F0.toInt()
        }
        val prediction = FloatArray(37).apply {
            this[0] = 8f
            this[1] = 8f
            this[2] = 12f
            this[3] = 12f
            this[4] = 0.9f
            this[5] = 8f
        }
        val prototype = FloatArray(32 * 4 * 4) { index -> if (index / 16 == 0) 1f else 0f }

        val bubble = decodeMangaLensBubbleRegions(
            prediction = prediction,
            candidateCount = 1,
            prototype = prototype,
            prototypeWidth = 4,
            prototypeHeight = 4,
            sourcePixels = pixels,
            sourceWidth = 16,
            sourceHeight = 16,
            inputSize = 16,
            confidenceThreshold = 0.45f
        ).single()

        assertFalse(bubble.solidFill)
    }

    @Test
    fun detectorOutputMapsLetterboxedBubbleBackToPageCoordinates() {
        val candidateCount = 2
        val output = FloatArray(37 * candidateCount)
        output[0 * candidateCount] = 800f
        output[1 * candidateCount] = 800f
        output[2 * candidateCount] = 400f
        output[3 * candidateCount] = 300f
        output[4 * candidateCount] = 0.9f
        output[4 * candidateCount + 1] = 0.1f

        val bubble = decodeMangaLensBubbleBoxes(
            output = output,
            candidateCount = candidateCount,
            sourceWidth = 800,
            sourceHeight = 1200,
            inputSize = 1600,
            confidenceThreshold = 0.45f
        ).single()

        assertEquals(0.3125f, bubble.x, 0.002f)
        assertEquals(0.40625f, bubble.y, 0.002f)
        assertEquals(0.375f, bubble.width, 0.002f)
        assertEquals(0.1875f, bubble.height, 0.002f)
    }

    @Test
    fun detectorOutputSuppressesLowerConfidenceOverlappingBubble() {
        val candidateCount = 2
        val output = FloatArray(37 * candidateCount)
        output[0] = 800f
        output[1] = 805f
        output[candidateCount] = 800f
        output[candidateCount + 1] = 805f
        output[candidateCount * 2] = 400f
        output[candidateCount * 2 + 1] = 400f
        output[candidateCount * 3] = 400f
        output[candidateCount * 3 + 1] = 400f
        output[candidateCount * 4] = 0.91f
        output[candidateCount * 4 + 1] = 0.72f

        val bubbles = decodeMangaLensBubbleBoxes(
            output = output,
            candidateCount = candidateCount,
            sourceWidth = 1600,
            sourceHeight = 1600,
            inputSize = 1600,
            confidenceThreshold = 0.45f
        )

        assertEquals(1, bubbles.size)
        assertEquals(0.375f, bubbles.single().x, 0.002f)
    }
}
