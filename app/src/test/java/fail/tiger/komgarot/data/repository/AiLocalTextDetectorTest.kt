package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLocalTextDetectorTest {
    private val detectorSource = File("src/main/java/fail/tiger/komgarot/data/repository/AiLocalTextDetector.kt").readText()

    @Test
    fun detectorUsesHeuristicFallbackWithoutBundledTextRecognition() {
        val versionCatalog = File("../gradle/libs.versions.toml").readText()
        val appBuild = File("build.gradle.kts").readText()

        assertTrue(!versionCatalog.contains("mlkit-text-recognition-japanese"))
        assertTrue(!appBuild.contains("implementation(libs.mlkit.text.recognition.japanese)"))
        assertTrue(!detectorSource.contains("TextRecognition.getClient"))
        assertTrue(!detectorSource.contains("JapaneseTextRecognizerOptions"))
        assertTrue(!detectorSource.contains("recognizedText"))
        assertTrue(detectorSource.contains("findInkComponents("))
        assertTrue(detectorSource.contains("mergeTextComponents("))
        assertTrue(detectorSource.contains("detectWithHeuristic"))
    }

    @Test
    fun detectorUsesPaddleOnnxBeforeHeuristicFallbackWhenSettingsAreAvailable() {
        val versionCatalog = File("../gradle/libs.versions.toml").readText()
        val appBuild = File("build.gradle.kts").readText()
        val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()
        val paddleSource = File("src/full/java/fail/tiger/komgarot/data/repository/AiPaddleTextDetector.kt").readText()

        assertTrue(versionCatalog.contains("onnxruntime-android"))
        assertTrue(appBuild.contains("add(\"fullImplementation\", libs.onnxruntime.android)"))
        assertTrue(appSource.contains("AiPaddleTextDetector(applicationContext, aiLocalModelRepository)"))
        assertTrue(detectorSource.contains("paddleTextDetector: AiPaddleTextDetector? = null"))
        assertTrue(detectorSource.contains("paddleTextDetector?.detect("))
        assertTrue(!detectorSource.contains("mergePaddleRegionsWithOcrText"))
        assertTrue(paddleSource.contains("OrtEnvironment.getEnvironment()"))
        assertTrue(paddleSource.contains("paddleProbabilityMapToRects("))
        assertTrue(!paddleSource.contains("runRecognitionModel"))
    }

    @Test
    fun nearbyHorizontalDetectionLinesMergeIntoOneTextBoxRegion() {
        val firstLine = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.10f, 0.20f, 0.20f, 0.035f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.72f
        )
        val secondLine = firstLine.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.11f, 0.245f, 0.19f, 0.036f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(secondLine, firstLine)).single()

        assertEquals("p0-r1", merged.id)
        assertEquals(AiTranslationTextDirection.HORIZONTAL, merged.textDirection)
        assertTrue(merged.rect.y <= firstLine.rect.y)
        assertTrue(merged.rect.height > firstLine.rect.height + secondLine.rect.height)
        assertTrue(merged.estimatedFontScale > firstLine.estimatedFontScale)
    }

    @Test
    fun nearbyVerticalDetectionColumnsMergeIntoOneTextBoxRegion() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.030f, 0.22f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.68f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.65f, 0.18f, 0.030f, 0.22f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(leftColumn, rightColumn)).single()

        assertEquals("p0-r1", merged.id)
        assertEquals(AiTranslationTextDirection.VERTICAL, merged.textDirection)
        assertTrue(merged.rect.x <= leftColumn.rect.x)
        assertTrue(merged.rect.width > rightColumn.rect.width + leftColumn.rect.width)
        assertTrue(merged.estimatedFontScale > rightColumn.estimatedFontScale)
    }

    @Test
    fun nearbyVerticalColumnsWithDifferentTopsRemainSeparateTextBoxes() {
        val upperColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.030f, 0.22f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.68f
        )
        val lowerNearbyColumn = upperColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.65f, 0.30f, 0.030f, 0.22f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(lowerNearbyColumn, upperColumn))

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun wideMergedVerticalColumnsKeepVerticalDirection() {
        val columns = listOf(0.70f, 0.63f, 0.56f, 0.49f, 0.42f).mapIndexed { index, x ->
            AiTranslationLocalTextRegion(
                id = "p0-r${index + 1}",
                rect = AiTranslationRect(x, 0.18f, 0.030f, 0.18f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.68f
            )
        }

        val merged = mergeLocalTextRegionsIntoTextBoxes(columns).single()

        assertEquals(AiTranslationTextDirection.VERTICAL, merged.textDirection)
        assertTrue(merged.rect.width > merged.rect.height)
    }

    @Test
    fun distantDetectionTextBoxesRemainSeparateRegions() {
        val upper = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.10f, 0.20f, 0.20f, 0.035f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.82f
        )
        val lower = upper.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.11f, 0.54f, 0.19f, 0.036f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(lower, upper))

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun proguardKeepsOnnxRuntimeJavaWrappersForJni() {
        val rules = File("proguard-rules.pro").readText()

        assertTrue(rules.contains("-keep class ai.onnxruntime.** { *; }"))
        assertTrue(rules.contains("-dontwarn ai.onnxruntime.**"))
    }

    @Test
    fun nearbyGlyphComponentsMergeIntoOneTextCluster() {
        val clusters = mergeTextComponents(
            components = listOf(
                AiTextComponent(left = 10, top = 10, right = 16, bottom = 22, area = 24, darkPixels = 24, lightPixels = 0),
                AiTextComponent(left = 18, top = 11, right = 24, bottom = 23, area = 26, darkPixels = 26, lightPixels = 0),
                AiTextComponent(left = 11, top = 27, right = 17, bottom = 39, area = 25, darkPixels = 25, lightPixels = 0)
            ),
            imageWidth = 400,
            imageHeight = 600
        )

        assertEquals(1, clusters.size)
        assertEquals(3, clusters.first().components.size)
    }

    @Test
    fun distantGlyphComponentsRemainSeparateTextClusters() {
        val clusters = mergeTextComponents(
            components = listOf(
                AiTextComponent(left = 10, top = 10, right = 16, bottom = 22, area = 24, darkPixels = 24, lightPixels = 0),
                AiTextComponent(left = 18, top = 11, right = 24, bottom = 23, area = 26, darkPixels = 26, lightPixels = 0),
                AiTextComponent(left = 280, top = 400, right = 288, bottom = 414, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 292, top = 401, right = 300, bottom = 415, area = 31, darkPixels = 31, lightPixels = 0)
            ),
            imageWidth = 400,
            imageHeight = 600
        )

        assertEquals(2, clusters.size)
        assertTrue(clusters.all { it.components.size == 2 })
    }

    @Test
    fun scatteredArtworkStrokesAreRejectedAsTextClusters() {
        val clusters = mergeTextComponents(
            components = listOf(
                AiTextComponent(left = 20, top = 20, right = 36, bottom = 28, area = 22, darkPixels = 22, lightPixels = 0),
                AiTextComponent(left = 42, top = 34, right = 60, bottom = 42, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 66, top = 50, right = 84, bottom = 58, area = 28, darkPixels = 28, lightPixels = 0),
                AiTextComponent(left = 90, top = 66, right = 108, bottom = 74, area = 28, darkPixels = 28, lightPixels = 0)
            ),
            imageWidth = 240,
            imageHeight = 320
        )

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun wideVerticalJapaneseColumnsStayVertical() {
        val cluster = AiTextCluster(
            listOf(
                AiTextComponent(left = 10, top = 10, right = 18, bottom = 24, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 11, top = 30, right = 19, bottom = 44, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 12, top = 50, right = 20, bottom = 64, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 48, top = 10, right = 56, bottom = 24, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 49, top = 30, right = 57, bottom = 44, area = 30, darkPixels = 30, lightPixels = 0),
                AiTextComponent(left = 50, top = 50, right = 58, bottom = 64, area = 30, darkPixels = 30, lightPixels = 0)
            )
        )

        assertEquals(AiTranslationTextDirection.VERTICAL, inferTextDirection(cluster))
    }

    @Test
    fun localRegionKeepsWhiteTextOnDarkBackground() {
        val width = 120
        val height = 120
        val pixels = IntArray(width * height) { 0xFF0C0C0C.toInt() }
        val cluster = AiTextCluster(
            listOf(
                AiTextComponent(left = 40, top = 30, right = 48, bottom = 44, area = 40, darkPixels = 0, lightPixels = 40),
                AiTextComponent(left = 40, top = 52, right = 48, bottom = 66, area = 42, darkPixels = 0, lightPixels = 42),
                AiTextComponent(left = 40, top = 74, right = 48, bottom = 88, area = 41, darkPixels = 0, lightPixels = 41)
            )
        )

        val region = cluster.toLocalTextRegion(
            id = "p0-r1",
            pixels = pixels,
            detectionImageWidth = width,
            detectionImageHeight = height
        )

        assertEquals("#F2F2F2", region.textColor)
        assertEquals("#111111", region.backgroundColor)
        assertEquals(AiTranslationTextDirection.VERTICAL, region.textDirection)
    }

    @Test
    fun localRegionCorrectsAiBlockDirectionColorAndLooseRect() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.08f, height = 0.26f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#F2F2F2",
            backgroundColor = "#111111",
            confidence = 0.78f,
            estimatedFontScale = 1.1f
        )
        val block = AiTranslationBlock(
            translatedLines = listOf("这是一段示例排版文本。"),
            rect = AiTranslationRect(x = 0.07f, y = 0.17f, width = 0.20f, height = 0.38f),
            translationRect = AiTranslationRect(x = 0.07f, y = 0.17f, width = 0.20f, height = 0.38f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            maskColor = "#FFFFFF",
            fontScale = 0.8f
        )

        val corrected = block.correctWithLocalRegion(region)

        assertEquals(AiTranslationTextDirection.VERTICAL, corrected.textDirection)
        assertEquals("#F2F2F2", corrected.textColor)
        assertEquals("#111111", corrected.maskColor)
        assertEquals(region.rect, corrected.rect)
        assertEquals(region.rect, corrected.translationRect)
        assertEquals(1.1f, corrected.fontScale)
    }

    @Test
    fun localRegionAlwaysOwnsPlacementEvenWhenAiReturnsInnerRect() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.12f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.76f,
            estimatedFontScale = 1.05f
        )
        val tightRect = AiTranslationRect(x = 0.12f, y = 0.23f, width = 0.07f, height = 0.22f)
        val block = AiTranslationBlock(
            translatedLines = listOf("示例"),
            rect = tightRect,
            translationRect = tightRect,
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            maskColor = "#FFFFFF",
            fontScale = 0.98f
        )

        val corrected = block.correctWithLocalRegion(region)

        assertEquals(region.rect, corrected.rect)
        assertEquals(region.rect, corrected.translationRect)
        assertEquals(1.05f, corrected.fontScale)
    }

    @Test
    fun detectionRectMapsBackToOriginalImageRatioAfterSampling() {
        val rect = normalizedSourceRectFromDetectionPixels(
            left = 50,
            top = 100,
            right = 149,
            bottom = 299,
            detectionImageWidth = 500,
            detectionImageHeight = 1000,
            sourceImageWidth = 1000,
            sourceImageHeight = 2000
        )

        assertEquals(0.10f, rect.x, 0.0001f)
        assertEquals(0.10f, rect.y, 0.0001f)
        assertEquals(0.20f, rect.width, 0.0001f)
        assertEquals(0.20f, rect.height, 0.0001f)
    }

    @Test
    fun unreadableLocalRegionColorFallsBackToReadableTextColor() {
        val whiteBackgroundRegion = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.12f, height = 0.30f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#FFFFFF",
            backgroundColor = "#FFFFFF",
            confidence = 0.76f,
            estimatedFontScale = 1.0f
        )
        val darkBackgroundRegion = whiteBackgroundRegion.copy(
            id = "p0-r2",
            textColor = "#111111",
            backgroundColor = "#111111"
        )

        assertEquals("#111111", AiTranslationBlock().correctWithLocalRegion(whiteBackgroundRegion).textColor)
        assertEquals("#F2F2F2", AiTranslationBlock().correctWithLocalRegion(darkBackgroundRegion).textColor)
    }

    @Test
    fun localRegionsCorrectDriftedAiBlocksByReadingOrderWhenCountsMatch() {
        val first = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.70f, y = 0.10f, width = 0.08f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.82f,
            estimatedFontScale = 1.0f
        )
        val second = first.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.20f, y = 0.12f, width = 0.08f, height = 0.28f)
        )
        val driftedBlocks = listOf(
            AiTranslationBlock(rect = AiTranslationRect(x = 0.12f, y = 0.70f, width = 0.18f, height = 0.12f)),
            AiTranslationBlock(rect = AiTranslationRect(x = 0.40f, y = 0.72f, width = 0.18f, height = 0.12f))
        )

        val corrected = correctBlocksWithLocalRegions(driftedBlocks, listOf(first, second))

        assertEquals(first.rect, corrected[0].rect)
        assertEquals(second.rect, corrected[1].rect)
    }

    @Test
    fun localRegionIdCorrectsAiBlockWithExactRegionMatch() {
        val first = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.70f, y = 0.10f, width = 0.08f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.82f,
            estimatedFontScale = 1.0f
        )
        val second = first.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.20f, y = 0.12f, width = 0.08f, height = 0.28f)
        )
        val block = AiTranslationBlock(
            localRegionId = "p0-r2",
            sourceText = "Sample Name",
            rect = AiTranslationRect(x = 0.90f, y = 0.90f, width = 0.06f, height = 0.06f)
        )

        val corrected = correctBlocksWithLocalRegions(listOf(block), listOf(first, second)).single()

        assertEquals(second.rect, corrected.rect)
    }

    @Test
    fun validLocalRegionIdIsTrusted() {
        val firstByReadingOrder = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.76f, y = 0.22f, width = 0.08f, height = 0.22f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.86f,
            estimatedFontScale = 0.94f
        )
        val second = firstByReadingOrder.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.18f, y = 0.72f, width = 0.12f, height = 0.16f)
        )
        val block = AiTranslationBlock(
            localRegionId = second.id,
            sourceText = "",
            translatedLines = listOf("示例文本已经稳定。"),
            rect = AiTranslationRect(x = 0.02f, y = 0.02f, width = 0.10f, height = 0.10f),
            textDirection = AiTranslationTextDirection.HORIZONTAL
        )

        val corrected = correctBlocksWithLocalRegions(listOf(block), listOf(firstByReadingOrder, second)).single()

        assertEquals(second.id, corrected.localRegionId)
        assertEquals(second.rect, corrected.rect)
        assertEquals(AiTranslationTextDirection.VERTICAL, corrected.textDirection)
    }

    @Test
    fun validLocalRegionIdOwnsPlacementEvenWhenSourceTextDiffers() {
        val wrongRegion = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.10f, y = 0.58f, width = 0.05f, height = 0.32f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.86f
        )
        val actualRegion = wrongRegion.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.72f, y = 0.58f, width = 0.12f, height = 0.22f)
        )
        val block = AiTranslationBlock(
            localRegionId = wrongRegion.id,
            sourceText = "Sample vertical text",
            translatedLines = listOf("这里是示例竖排文本。"),
            rect = AiTranslationRect(x = 0.70f, y = 0.56f, width = 0.14f, height = 0.24f),
            textDirection = AiTranslationTextDirection.VERTICAL
        )

        val corrected = correctBlocksWithLocalRegions(listOf(block), listOf(wrongRegion, actualRegion)).single()

        assertEquals(wrongRegion.rect, corrected.rect)
        assertEquals(wrongRegion.id, corrected.localRegionId)
    }

    @Test
    fun localRegionMatchingFallsBackForBlocksWithoutUsableIds() {
        val first = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.72f, y = 0.10f, width = 0.06f, height = 0.24f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.82f,
            estimatedFontScale = 0.92f
        )
        val second = first.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.46f, y = 0.58f, width = 0.07f, height = 0.20f),
            estimatedFontScale = 0.86f
        )
        val blocks = listOf(
            AiTranslationBlock(
                localRegionId = "p0-r1",
                rect = AiTranslationRect(x = 0.12f, y = 0.70f, width = 0.20f, height = 0.12f),
                textDirection = AiTranslationTextDirection.HORIZONTAL
            ),
            AiTranslationBlock(
                localRegionId = "",
                rect = AiTranslationRect(x = 0.82f, y = 0.84f, width = 0.16f, height = 0.12f),
                textDirection = AiTranslationTextDirection.HORIZONTAL
            )
        )

        val corrected = correctBlocksWithLocalRegions(blocks, listOf(first, second))

        assertEquals(first.rect, corrected[0].rect)
        assertEquals(second.rect, corrected[1].rect)
        assertEquals(AiTranslationTextDirection.VERTICAL, corrected[1].textDirection)
        assertEquals(0.86f, corrected[1].fontScale)
    }

    @Test
    fun localRegionMatchingFallsBackWhenAiReturnsInvalidIds() {
        val first = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.78f, y = 0.18f, width = 0.07f, height = 0.20f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.82f,
            estimatedFontScale = 0.9f
        )
        val second = first.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.18f, y = 0.70f, width = 0.10f, height = 0.12f),
            textDirection = AiTranslationTextDirection.HORIZONTAL
        )
        val blocks = listOf(
            AiTranslationBlock(localRegionId = "made-up-1", rect = AiTranslationRect(x = 0.40f, y = 0.40f, width = 0.18f, height = 0.18f)),
            AiTranslationBlock(localRegionId = "made-up-2", rect = AiTranslationRect(x = 0.42f, y = 0.44f, width = 0.18f, height = 0.18f))
        )

        val corrected = correctBlocksWithLocalRegions(blocks, listOf(first, second))

        assertEquals(first.rect, corrected[0].rect)
        assertEquals(second.rect, corrected[1].rect)
    }

    @Test
    fun localRegionMatchingUsesReadingOrderWhenBlockHasNoUsableId() {
        val firstByReadingOrder = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.72f, y = 0.10f, width = 0.06f, height = 0.20f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.96f,
            estimatedFontScale = 0.9f
        )
        val secondByReadingOrder = firstByReadingOrder.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.22f, y = 0.42f, width = 0.08f, height = 0.24f)
        )
        val block = AiTranslationBlock(
            sourceText = "Sample Name",
            translatedLines = listOf("示例名称"),
            rect = AiTranslationRect(x = 0.80f, y = 0.80f, width = 0.12f, height = 0.12f)
        )

        val corrected = correctBlocksWithLocalRegions(listOf(block), listOf(firstByReadingOrder, secondByReadingOrder)).single()

        assertEquals(firstByReadingOrder.rect, corrected.rect)
        assertEquals(firstByReadingOrder.id, corrected.localRegionId)
    }

    @Test
    fun localRegionMatchingIgnoresNearbyAiGeometryAndUsesLocalReadingOrder() {
        val firstByReadingOrder = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.72f, y = 0.10f, width = 0.06f, height = 0.22f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.9f
        )
        val lowerLeft = firstByReadingOrder.copy(
            id = "p0-r2",
            rect = AiTranslationRect(x = 0.20f, y = 0.70f, width = 0.08f, height = 0.16f),
            textDirection = AiTranslationTextDirection.HORIZONTAL
        )
        val block = AiTranslationBlock(
            rect = AiTranslationRect(x = 0.19f, y = 0.69f, width = 0.10f, height = 0.18f),
            textDirection = AiTranslationTextDirection.HORIZONTAL
        )

        val corrected = correctBlocksWithLocalRegions(listOf(block), listOf(firstByReadingOrder, lowerLeft)).single()

        assertEquals(firstByReadingOrder.rect, corrected.rect)
        assertEquals(firstByReadingOrder.id, corrected.localRegionId)
    }

    @Test
    fun localRegionMatchingDropsAiBlocksThatCannotMapToLocalRegions() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.70f, y = 0.10f, width = 0.08f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.82f,
            estimatedFontScale = 1.0f
        )
        val blocks = listOf(
            AiTranslationBlock(sourceText = "SFX", translatedLines = listOf("音效")),
            AiTranslationBlock(sourceText = "made up", translatedLines = listOf("虚构"), rect = AiTranslationRect(x = 0.10f, y = 0.80f, width = 0.20f, height = 0.10f))
        )

        val corrected = correctBlocksWithLocalRegions(blocks, listOf(region))

        assertEquals(1, corrected.size)
        assertEquals(region.rect, corrected.single().rect)
        assertEquals(listOf("音效"), corrected.single().translatedLines)
    }

    @Test
    fun localContextSuppliesPageImageSizeForOverlayAlignment() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.08f, height = 0.26f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.8f,
            estimatedFontScale = 1.0f
        )
        val page = AiTranslatedPage(
            pageIndex = 0,
            imageWidth = 0,
            imageHeight = 0,
            blocks = listOf(AiTranslationBlock(rect = region.rect, translationRect = region.rect))
        )
        val context = AiTranslationLocalPageContext(
            pageIndex = 0,
            imageWidth = 768,
            imageHeight = 1117,
            regions = listOf(region)
        )

        val corrected = correctPageWithLocalContext(page, AiTranslationMode.LOCAL_DETECTION, context)

        assertEquals(768, corrected.imageWidth)
        assertEquals(1117, corrected.imageHeight)
    }

    @Test
    fun localContextOverridesAiImageSizeInLocalModes() {
        val page = AiTranslatedPage(
            pageIndex = 0,
            imageWidth = 512,
            imageHeight = 768,
            blocks = listOf(
                AiTranslationBlock(
                    rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.08f, height = 0.26f),
                    textColor = "#FFFFFF",
                    maskColor = "#FFFFFF"
                )
            )
        )
        val context = AiTranslationLocalPageContext(
            pageIndex = 0,
            imageWidth = 1536,
            imageHeight = 2234,
            regions = emptyList()
        )

        val corrected = correctPageWithLocalContext(page, AiTranslationMode.LOCAL_DETECTION, context)

        assertEquals(1536, corrected.imageWidth)
        assertEquals(2234, corrected.imageHeight)
        assertEquals("#111111", corrected.blocks.single().textColor)
    }
}
