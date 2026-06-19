package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPaddleTextDetectorTest {
    @Test
    fun detectorInputSourceUsesRgbImageNetNormalization() {
        val source = File("src/main/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

        assertTrue(source.contains("input[index] = (red - 0.485f) / 0.229f"))
        assertTrue(source.contains("input[channelSize + index] = (green - 0.456f) / 0.224f"))
        assertTrue(source.contains("input[channelSize * 2 + index] = (blue - 0.406f) / 0.225f"))
    }

    @Test
    fun probabilityMapComponentsBecomeNormalizedSourceRects() {
        val width = 20
        val height = 20
        val map = FloatArray(width * height)
        for (y in 5..9) {
            for (x in 8..11) {
                map[y * width + x] = 0.92f
            }
        }

        val rects = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = width,
            mapHeight = height,
            sourceWidth = 1000,
            sourceHeight = 2000
        )

        assertEquals(1, rects.size)
        val rect = rects.single().rect
        assertEquals(0.40f, rect.x, 0.0001f)
        assertEquals(0.25f, rect.y, 0.0001f)
        assertEquals(0.20f, rect.width, 0.0001f)
        assertEquals(0.25f, rect.height, 0.0001f)
    }

    @Test
    fun probabilityMapRejectsLargeArtworkRegions() {
        val width = 20
        val height = 20
        val map = FloatArray(width * height) { 0.95f }

        val rects = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = width,
            mapHeight = height,
            sourceWidth = 1000,
            sourceHeight = 2000
        )

        assertTrue(rects.isEmpty())
    }

    @Test
    fun paddleDetectorSourceContainsDetectionPathOnly() {
        val source = File("src/main/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

        assertTrue(source.contains("paddleProbabilityMapToRects("))
        assertTrue(source.contains("toPaddleDetectorInput("))
        assertTrue(!source.contains("runRecognitionModel"))
        assertTrue(!source.contains("PaddleRecognitionAssets"))
        assertTrue(!source.contains("loadPaddleDictionary"))
        assertTrue(!source.contains("paddleCtcDecode"))
    }
}
