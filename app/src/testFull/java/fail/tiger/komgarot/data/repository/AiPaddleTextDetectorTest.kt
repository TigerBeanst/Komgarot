package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPaddleTextDetectorTest {

    @Test
    fun detectorInputSourceUsesRgbImageNetNormalization() {
        val source = File("src/full/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

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
        assertEquals(0.35f, rect.x, 0.0001f)
        assertEquals(0.20f, rect.y, 0.0001f)
        assertEquals(0.30f, rect.width, 0.0001f)
        assertEquals(0.35f, rect.height, 0.0001f)
        assertEquals(0.92f, rects.single().meanScore, 0.0001f)
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
        val source = File("src/full/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

        assertTrue(source.contains("paddleProbabilityMapToRects("))
        assertTrue(source.contains("toPaddleDetectorInput("))
        assertTrue(!source.contains("runRecognitionModel"))
        assertTrue(!source.contains("PaddleRecognitionAssets"))
        assertTrue(!source.contains("loadPaddleDictionary"))
        assertTrue(!source.contains("paddleCtcDecode"))
    }

    @Test
    fun languageCalibratedDbThresholdsKeepKoreanAndEnglishPagedComicsIndependent() {
        val width = 24
        val height = 24
        val map = FloatArray(width * height)
        for (y in 8..12) {
            for (x in 7..13) map[y * width + x] = 0.54f
        }

        val korean = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = width,
            mapHeight = height,
            sourceWidth = 1200,
            sourceHeight = 1800,
            sourceLanguageTag = "ko"
        )
        val english = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = width,
            mapHeight = height,
            sourceWidth = 1200,
            sourceHeight = 1800,
            sourceLanguageTag = "en"
        )

        assertEquals(1, korean.size)
        assertTrue(english.isEmpty())
    }

    @Test
    fun japanesePagedComicUsesLowerDbThresholdForFineStrokes() {
        val map = FloatArray(20 * 20)
        for (y in 6..10) {
            for (x in 9..11) map[y * 20 + x] = 0.50f
        }

        val japanese = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = 20,
            mapHeight = 20,
            sourceWidth = 1000,
            sourceHeight = 1600,
            sourceTextProfile = fail.tiger.komgarot.data.local.AiSourceTextProfile.JAPANESE_MANGA,
            sourceLanguageTag = "ja-JP"
        )

        assertEquals(1, japanese.size)
        assertEquals(0.50f, japanese.single().meanScore, 0.0001f)
    }

    @Test
    fun probabilityContourCarriesSlantedRotationInformation() {
        val width = 24
        val height = 24
        val map = FloatArray(width * height)
        for (x in 4..10) {
            val y = 5 + (x - 4) / 2
            map[y * width + x] = 0.94f
            map[(y + 1) * width + x] = 0.94f
        }

        val rect = paddleProbabilityMapToRects(
            probabilityMap = map,
            mapWidth = width,
            mapHeight = height,
            sourceWidth = 1200,
            sourceHeight = 1800,
            sourceLanguageTag = "ko"
        ).single()

        assertTrue(rect.rotationDegrees in 15f..35f)
    }

    @Test
    fun confidenceCombinesModelProbabilityAndGeometryQuality() {
        val weaker = PaddleTextRect(
            rect = fail.tiger.komgarot.data.local.AiTranslationRect(0.1f, 0.1f, 0.2f, 0.1f),
            area = 18,
            meanScore = 0.62f,
            geometryQuality = 0.55f
        )
        val stronger = weaker.copy(meanScore = 0.94f, geometryQuality = 0.91f)

        assertTrue(paddleRegionConfidence(stronger) > paddleRegionConfidence(weaker))
        assertTrue(paddleRegionConfidence(stronger) < 1f)
    }

    @Test
    fun inkRefinementScratchCapacityTracksLocalBoundsArea() {
        val bytes = paddleInkScratchBytes(
            rect = fail.tiger.komgarot.data.local.AiTranslationRect(0.10f, 0.20f, 0.20f, 0.10f),
            imageWidth = 1000,
            imageHeight = 2000
        )

        assertEquals(200L * 200L * 5L, bytes)
        assertTrue(bytes < 1000L * 2000L * 5L)
    }

    @Test
    fun paddleSourceReusesSessionsAndReadsProbabilityAsFloatBuffer() {
        val source = File("src/full/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

        assertTrue(source.contains("private val sessions = linkedMapOf"))
        assertTrue(source.contains("wasReused = true"))
        assertTrue(source.contains("output.floatBuffer"))
        assertTrue(!source.contains("mutableListOf<Float>()"))
        assertTrue(!source.contains("flattenFloats"))
        assertTrue(source.contains("override fun close()"))
    }
}
