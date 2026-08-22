package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiSourceTextProfile
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
        assertTrue(detectorSource.contains("sourceLanguageTag = sourceLanguageTag"))
        assertTrue(detectorSource.contains("onDetectionStats: (AiLocalDetectionStats) -> Unit = {}"))
        assertTrue(!detectorSource.contains("mergePaddleRegionsWithOcrText"))
        assertTrue(paddleSource.contains("OrtEnvironment.getEnvironment()"))
        assertTrue(paddleSource.contains("paddleProbabilityMapToRects("))
        assertTrue(paddleSource.contains("bitmap.toPaddleDetectorInput(maxSide = paddleDetectorInputMaxSide("))
        assertTrue(paddleSource.contains("coerceIn(0.74f, 1.28f)"))
        assertTrue(paddleSource.contains("PaddleOnnxSessionCache"))
        assertTrue(paddleSource.contains("paddleRegionConfidence(this)"))
        assertTrue(!paddleSource.contains("runRecognitionModel"))
    }

    @Test
    fun detectorReportsPaddleAndHeuristicTimingSeparately() {
        assertTrue(detectorSource.contains("onTimingStep: (String, Long) -> Unit = { _, _ -> }"))
        assertTrue(detectorSource.contains("AI_TIMING_PADDLE_OCR"))
        assertTrue(detectorSource.contains("AI_TIMING_HEURISTIC_FALLBACK"))
        assertTrue(detectorSource.contains("timedLocalDetectionStep("))
    }

    @Test
    fun paddleRegionsUseTextBoxMergingWithoutHeuristicFallback() {
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
            rect = AiTranslationRect(0.658f, 0.18f, 0.030f, 0.22f)
        )

        val selected = selectLocalTextDetectionRegions(
            paddleRegions = listOf(leftColumn, rightColumn),
            heuristicRegions = { error("heuristic fallback executed with Paddle regions") },
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA,
            maxRegions = 64
        )

        assertEquals(1, selected.size)
        assertEquals("p0-r1", selected.single().id)
        val rect = selected.single().rect
        assertEquals(0.658f, rect.x, 0.0001f)
        assertEquals(0.18f, rect.y, 0.0001f)
        assertEquals(0.072f, rect.width, 0.0001f)
        assertEquals(0.22f, rect.height, 0.0001f)
    }

    @Test
    fun localDetectorDefinesTextLineTextBlockAndMaskRegionPipeline() {
        assertTrue(detectorSource.contains("internal data class AiDetectedTextLine"))
        assertTrue(detectorSource.contains("internal data class AiTextBlockCandidate"))
        assertTrue(detectorSource.contains("internal data class AiMaskRegion"))
        assertTrue(detectorSource.contains("detectedTextLinesFromLocalRegions("))
        assertTrue(detectorSource.contains("buildMangaTextBlocks("))
        assertTrue(detectorSource.contains("toLocalTextBoxRegions("))
    }

    @Test
    fun mangaTextBlocksMergeOnlyNearSameTopAndSameWidthVerticalLines() {
        val rightLine = AiDetectedTextLine(
            region = AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(0.70f, 0.18f, 0.034f, 0.34f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.90f
            )
        )
        val middleLine = AiDetectedTextLine(
            region = rightLine.region.copy(
                id = "p0-r2",
                rect = AiTranslationRect(0.656f, 0.185f, 0.034f, 0.18f)
            )
        )
        val leftLine = AiDetectedTextLine(
            region = rightLine.region.copy(
                id = "p0-r3",
                rect = AiTranslationRect(0.612f, 0.176f, 0.034f, 0.25f)
            )
        )
        val separateLine = AiDetectedTextLine(
            region = rightLine.region.copy(
                id = "p0-r4",
                rect = AiTranslationRect(0.570f, 0.300f, 0.034f, 0.22f)
            )
        )

        val blocks = buildMangaTextBlocks(
            lines = listOf(separateLine, leftLine, middleLine, rightLine),
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, blocks.size)
        assertEquals(listOf("p0-r1", "p0-r2", "p0-r3"), blocks.first().lines.map { it.id })
        assertEquals(listOf("p0-r4"), blocks.last().lines.map { it.id })
        assertEquals(
            listOf(rightLine.rect, middleLine.rect, leftLine.rect),
            blocks.first().maskRegions.map { it.rect }
        )
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
    fun nearbyHorizontalTextBoxesWithVisibleRowGapRemainSeparate() {
        val upperBox = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.10f, 0.70f, 0.22f, 0.026f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.82f
        )
        val lowerBox = upperBox.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.08f, 0.755f, 0.28f, 0.026f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(lowerBox, upperBox),
            AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON
        )

        assertEquals(2, merged.size)
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
            rect = AiTranslationRect(0.658f, 0.18f, 0.030f, 0.22f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(leftColumn, rightColumn)).single()

        assertEquals("p0-r1", merged.id)
        assertEquals(AiTranslationTextDirection.VERTICAL, merged.textDirection)
        assertTrue(merged.rect.x <= leftColumn.rect.x)
        assertTrue(merged.rect.width > rightColumn.rect.width + leftColumn.rect.width)
        assertTrue(merged.estimatedFontScale > rightColumn.estimatedFontScale)
    }

    @Test
    fun mergedVerticalColumnsKeepIndividualColumnGeometryForLayoutAndCrop() {
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
            rect = AiTranslationRect(0.658f, 0.184f, 0.030f, 0.18f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(listOf(leftColumn, rightColumn)).single()

        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.sourceColumns)
        val crop = merged.effectiveAiCropBounds()
        assertTrue(crop.x <= leftColumn.rect.x)
        assertTrue(crop.x + crop.width >= rightColumn.rect.x + rightColumn.rect.width)
        assertTrue(crop.width <= merged.rect.width + 0.018f)
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
    fun japaneseMangaKeepsAdjacentSpeechBubblesAsSeparateTextBoxes() {
        val columns = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(0.70f, 0.18f, 0.030f, 0.22f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.68f
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r2",
                rect = AiTranslationRect(0.658f, 0.18f, 0.030f, 0.22f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.68f
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r3",
                rect = AiTranslationRect(0.58f, 0.18f, 0.030f, 0.22f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.68f
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r4",
                rect = AiTranslationRect(0.538f, 0.18f, 0.030f, 0.22f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.94f,
                estimatedFontScale = 0.68f
            )
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(columns, AiSourceTextProfile.JAPANESE_MANGA)

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r3"), merged.map { it.id })
    }

    @Test
    fun japaneseMangaKeepsNearbyLargeSoundEffectSeparateFromDialogueColumn() {
        val dialogueColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.030f, 0.24f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.82f
        )
        val largeSoundEffect = dialogueColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.64f, 0.18f, 0.070f, 0.24f),
            estimatedFontScale = 1.36f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(largeSoundEffect, dialogueColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun japaneseMangaKeepsNearbySoundEffectLikeColumnSeparateWhenFontSizeDiffers() {
        val dialogueColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.050f, 0.25f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val soundEffectColumn = dialogueColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.63f, 0.17f, 0.065f, 0.30f),
            estimatedFontScale = 1.05f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(soundEffectColumn, dialogueColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun autoProfileUsesStrictVerticalMangaColumnMergeRules() {
        val dialogueRight = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.034f, 0.28f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val dialogueLeft = dialogueRight.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.655f, 0.184f, 0.034f, 0.24f),
            estimatedFontScale = 0.91f
        )
        val nearbySoundEffect = dialogueRight.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.613f, 0.178f, 0.040f, 0.28f),
            estimatedFontScale = 1.12f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(nearbySoundEffect, dialogueLeft, dialogueRight),
            AiSourceTextProfile.AUTO
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r3"), merged.map { it.id })
        assertEquals(listOf(dialogueRight.rect, dialogueLeft.rect), merged.first().sourceColumns)
        assertEquals(listOf(nearbySoundEffect.rect), merged.last().sourceColumns)
    }

    @Test
    fun japaneseMangaKeepsOverlappingSoundEffectLikeColumnSeparateFromDialogue() {
        val dialogueRight = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.035f, 0.26f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val dialogueLeft = dialogueRight.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.652f, 0.184f, 0.035f, 0.22f)
        )
        val soundEffectLikeColumn = dialogueRight.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.63f, 0.18f, 0.042f, 0.26f),
            estimatedFontScale = 0.96f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(soundEffectLikeColumn, dialogueLeft, dialogueRight),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r3"), merged.map { it.id })
        assertEquals(2, merged.first().sourceColumns.size)
        assertEquals(1, merged.last().sourceColumns.size)
    }

    @Test
    fun japaneseMangaKeepsSlightlyLargerAdjacentSoundEffectColumnSeparateFromDialogue() {
        val dialogueRight = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.20f, 0.034f, 0.22f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val dialogueLeft = dialogueRight.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.655f, 0.21f, 0.035f, 0.21f),
            estimatedFontScale = 0.91f
        )
        val soundEffectLikeColumn = dialogueRight.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.613f, 0.19f, 0.038f, 0.24f),
            estimatedFontScale = 1.05f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(soundEffectLikeColumn, dialogueLeft, dialogueRight),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r3"), merged.map { it.id })
        assertEquals(2, merged.first().sourceColumns.size)
        assertEquals(1, merged.last().sourceColumns.size)
    }

    @Test
    fun japaneseMangaMergesOnlyTopAlignedAdjacentDialogueColumns() {
        val dialogueRight = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.720f, 0.220f, 0.035f, 0.320f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val dialogueMiddle = dialogueRight.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.676f, 0.224f, 0.035f, 0.300f)
        )
        val dialogueLeft = dialogueRight.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.632f, 0.218f, 0.035f, 0.310f)
        )
        val nearbySoundEffect = dialogueRight.copy(
            id = "p0-r4",
            rect = AiTranslationRect(0.588f, 0.100f, 0.037f, 0.280f),
            estimatedFontScale = 0.92f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(nearbySoundEffect, dialogueLeft, dialogueMiddle, dialogueRight),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r4"), merged.map { it.id })
        assertEquals(
            listOf(dialogueRight.rect, dialogueMiddle.rect, dialogueLeft.rect),
            merged.first().sourceColumns
        )
        assertEquals(listOf(nearbySoundEffect.rect), merged.last().sourceColumns)
    }

    @Test
    fun japaneseMangaKeepsSameHeightSameWidthColumnsSeparateWhenHorizontalGapIsLoose() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.720f, 0.220f, 0.035f, 0.260f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val looseLeftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.590f, 0.222f, 0.035f, 0.250f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(looseLeftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun japaneseMangaMergesOnlyNearEqualWidthColumns() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.720f, 0.220f, 0.035f, 0.260f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val widerColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.675f, 0.222f, 0.043f, 0.250f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(widerColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun japaneseMangaMergesCloseDialogueColumnsEvenWhenColumnHeightsDiffer() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.034f, 0.34f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val middleColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.655f, 0.185f, 0.034f, 0.16f)
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.610f, 0.176f, 0.034f, 0.24f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, middleColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, middleColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesCloseDialogueColumnsWhenVerticalOverlapIsHigh() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.320f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val middleColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.656f, 0.205f, 0.034f, 0.280f)
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.612f, 0.190f, 0.034f, 0.300f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, middleColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, middleColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesCloseTopBandDialogueColumnsWhenWidthsMatch() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.240f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val middleColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.658f, 0.192f, 0.034f, 0.160f)
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.616f, 0.188f, 0.034f, 0.210f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, middleColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, middleColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesTopAlignedCloseColumnsWhenColumnLengthsDiffer() {
        val longColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.400f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val shortColumn = longColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.658f, 0.194f, 0.034f, 0.120f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(shortColumn, longColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(longColumn.rect, shortColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesCloseColumnsWithinOneGlyphTopBand() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.190f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.658f, 0.214f, 0.033f, 0.120f),
            estimatedFontScale = 0.89f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesSameBubbleColumnsWithOneColumnWidthGap() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.300f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.632f, 0.184f, 0.034f, 0.292f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesSameBubbleColumnsWithNaturalBalloonGap() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.030f, 0.300f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.622f, 0.184f, 0.030f, 0.292f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesCloseColumnsWhenMaskPaddingOverlaps() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.036f, 0.260f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.678f, 0.184f, 0.036f, 0.238f),
            estimatedFontScale = 0.88f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesCloseColumnsWhenOcrWidthsAndBackgroundsVary() {
        val rightColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.260f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val leftColumn = rightColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.660f, 0.184f, 0.024f, 0.238f),
            backgroundColor = "#E8E8E8",
            estimatedFontScale = 0.72f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(rightColumn.rect, leftColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesShortContainedColumnInsideSameBubble() {
        val longColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.220f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val shortColumn = longColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.654f, 0.194f, 0.034f, 0.074f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(shortColumn, longColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(longColumn.rect, shortColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesShortContainedColumnWhenOcrWidthIsNarrow() {
        val longColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.034f, 0.220f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val shortColumn = longColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.660f, 0.194f, 0.024f, 0.074f),
            estimatedFontScale = 0.88f
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(shortColumn, longColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(listOf(longColumn.rect, shortColumn.rect), merged.single().sourceColumns)
    }

    @Test
    fun japaneseMangaMergesShortTopAlignedColumnWhenDistanceIsClose() {
        val longColumn = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.70f, 0.18f, 0.030f, 0.32f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val shortColumn = longColumn.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.66f, 0.188f, 0.030f, 0.12f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(shortColumn, longColumn),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals("p0-r1", merged.single().id)
    }

    @Test
    fun japaneseMangaKeepsVerticallySeparatedSameColumnRegionsSeparate() {
        val upper = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.30f, 0.22f, 0.030f, 0.16f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.86f
        )
        val lower = upper.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.30f, 0.44f, 0.030f, 0.16f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(lower, upper),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(2, merged.size)
        assertEquals(listOf("p0-r1", "p0-r2"), merged.map { it.id })
    }

    @Test
    fun japaneseMangaMergesFragmentedSameColumnBeforeAdjacentColumnGrouping() {
        val rightUpper = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(0.700f, 0.180f, 0.032f, 0.096f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.94f,
            estimatedFontScale = 0.90f
        )
        val rightLower = rightUpper.copy(
            id = "p0-r2",
            rect = AiTranslationRect(0.701f, 0.288f, 0.031f, 0.118f)
        )
        val leftColumn = rightUpper.copy(
            id = "p0-r3",
            rect = AiTranslationRect(0.662f, 0.182f, 0.032f, 0.224f)
        )

        val merged = mergeLocalTextRegionsIntoTextBoxes(
            listOf(leftColumn, rightLower, rightUpper),
            AiSourceTextProfile.JAPANESE_MANGA
        )

        assertEquals(1, merged.size)
        assertEquals(2, merged.single().sourceColumns.size)
        assertTrue(merged.single().sourceColumns.first().height > rightUpper.rect.height + rightLower.rect.height)
        assertEquals(leftColumn.rect, merged.single().sourceColumns.last())
    }

    @Test
    fun koreanHorizontalWebtoonProfilePrefersHorizontalDetectedBoxes() {
        val rect = AiTranslationRect(x = 0.12f, y = 0.20f, width = 0.16f, height = 0.19f)

        assertEquals(
            AiTranslationTextDirection.HORIZONTAL,
            detectedTextDirectionForRect(rect, AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON)
        )
        assertEquals(
            AiTranslationTextDirection.VERTICAL,
            detectedTextDirectionForRect(rect, AiSourceTextProfile.JAPANESE_MANGA)
        )
    }

    @Test
    fun koreanProfileNormalizesMixedCachedDirectionsToHorizontal() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(0.10f, 0.20f, 0.24f, 0.08f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1f,
                rotationDegrees = 8f,
                sourceColumns = listOf(AiTranslationRect(0.10f, 0.20f, 0.03f, 0.08f))
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r2",
                rect = AiTranslationRect(0.10f, 0.30f, 0.24f, 0.08f),
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1f
            )
        )

        val normalized = normalizeLocalTextDirectionsForProfile(
            regions,
            AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON
        )

        assertEquals(
            listOf(AiTranslationTextDirection.HORIZONTAL, AiTranslationTextDirection.HORIZONTAL),
            normalized.map { it.textDirection }
        )
        assertEquals(0f, normalized.first().rotationDegrees)
        assertTrue(normalized.first().sourceColumns.isEmpty())
    }

    @Test
    fun koreanHorizontalProfileKeepsTallMultilineRegionHorizontal() {
        val rect = AiTranslationRect(x = 0.72f, y = 0.68f, width = 0.10f, height = 0.19f)

        assertEquals(
            AiTranslationTextDirection.HORIZONTAL,
            detectedTextDirectionForRect(rect, AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON)
        )
    }

    @Test
    fun normalHorizontalComicProfilePrefersHorizontalDetectedBoxes() {
        val rect = AiTranslationRect(x = 0.12f, y = 0.20f, width = 0.16f, height = 0.19f)

        assertEquals(
            AiTranslationTextDirection.HORIZONTAL,
            detectedTextDirectionForRect(rect, AiSourceTextProfile.HORIZONTAL_COMIC)
        )
    }

    @Test
    fun horizontalRegionsUseAxisAlignedOverlayGeometry() {
        val signRect = AiTranslationRect(x = 0.12f, y = 0.16f, width = 0.28f, height = 0.08f)
        val direction = detectedTextDirectionForRect(signRect, AiSourceTextProfile.JAPANESE_MANGA)

        assertEquals(AiTranslationTextDirection.HORIZONTAL, direction)
        assertEquals(
            0f,
            estimatedRegionRotationDegreesForRect(
                rect = signRect,
                direction = direction,
                sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA
            )
        )
        assertEquals(
            0f,
            estimatedRegionRotationDegreesForRect(
                rect = signRect,
                direction = direction,
                sourceTextProfile = AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON
            )
        )
    }

    @Test
    fun wideMergedVerticalColumnsKeepVerticalDirection() {
        val columns = listOf(0.70f, 0.658f, 0.616f, 0.574f, 0.532f).mapIndexed { index, x ->
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
        assertEquals("#0C0C0C", region.backgroundColor)
        assertEquals(AiTranslationTextDirection.VERTICAL, region.textDirection)
    }

    @Test
    fun localRegionPreservesStableGrayBackgroundAndUsesDarkText() {
        val width = 120
        val height = 120
        val pixels = IntArray(width * height) { 0xFF999999.toInt() }
        val cluster = AiTextCluster(
            listOf(
                AiTextComponent(left = 40, top = 30, right = 48, bottom = 44, area = 40, darkPixels = 40, lightPixels = 0),
                AiTextComponent(left = 40, top = 52, right = 48, bottom = 66, area = 42, darkPixels = 42, lightPixels = 0),
                AiTextComponent(left = 40, top = 74, right = 48, bottom = 88, area = 41, darkPixels = 41, lightPixels = 0)
            )
        )

        val region = cluster.toLocalTextRegion(
            id = "gray-panel",
            pixels = pixels,
            detectionImageWidth = width,
            detectionImageHeight = height
        )

        assertEquals("#999999", region.backgroundColor)
        assertEquals("#111111", region.textColor)
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
        assertEquals(region.effectiveSourceMaskBounds(), corrected.rect)
        assertEquals(region.effectiveRenderBoundsForKind(block.kind), corrected.translationRect)
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

        assertEquals(region.effectiveSourceMaskBounds(), corrected.rect)
        assertEquals(region.effectiveRenderBoundsForKind(block.kind), corrected.translationRect)
        assertEquals(1.05f, corrected.fontScale)
    }

    @Test
    fun localRegionEffectiveGeometrySeparatesSourceMaskAndRenderPlacement() {
        val textBounds = AiTranslationRect(x = 0.12f, y = 0.22f, width = 0.07f, height = 0.22f)
        val renderBounds = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.11f, height = 0.27f)
        val cropBounds = AiTranslationRect(x = 0.115f, y = 0.215f, width = 0.08f, height = 0.23f)
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.09f, y = 0.18f, width = 0.16f, height = 0.34f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.76f,
            estimatedFontScale = 1.05f,
            textBounds = textBounds,
            renderBounds = renderBounds,
            aiCropBounds = cropBounds
        )

        val corrected = AiTranslationBlock(
            translatedLines = listOf("示例"),
            rect = AiTranslationRect(x = 0.70f, y = 0.70f, width = 0.10f, height = 0.10f),
            translationRect = AiTranslationRect(x = 0.70f, y = 0.70f, width = 0.10f, height = 0.10f)
        ).correctWithLocalRegion(region)

        assertEquals(textBounds, region.effectiveTextBounds())
        assertEquals(cropBounds, region.effectiveAiCropBounds())
        assertEquals(textBounds, corrected.rect)
        assertEquals(renderBounds, corrected.translationRect)
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

        assertEquals(first.effectiveSourceMaskBounds(), corrected[0].rect)
        assertEquals(second.effectiveSourceMaskBounds(), corrected[1].rect)
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

        assertEquals(second.effectiveSourceMaskBounds(), corrected.rect)
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
        assertEquals(second.effectiveSourceMaskBounds(), corrected.rect)
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

        assertEquals(wrongRegion.effectiveSourceMaskBounds(), corrected.rect)
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

        assertEquals(first.effectiveSourceMaskBounds(), corrected[0].rect)
        assertEquals(second.effectiveSourceMaskBounds(), corrected[1].rect)
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

        assertEquals(first.effectiveSourceMaskBounds(), corrected[0].rect)
        assertEquals(second.effectiveSourceMaskBounds(), corrected[1].rect)
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

        assertEquals(firstByReadingOrder.effectiveSourceMaskBounds(), corrected.rect)
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

        assertEquals(firstByReadingOrder.effectiveSourceMaskBounds(), corrected.rect)
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
        assertEquals(region.effectiveSourceMaskBounds(), corrected.single().rect)
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

    @Test
    fun defaultSourceMaskExpandsDetectedTextBounds() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.30f, y = 0.22f, width = 0.030f, height = 0.24f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.88f
        )

        val mask = region.effectiveSourceMaskBounds()

        assertTrue(mask.x < region.rect.x)
        assertTrue(mask.y < region.rect.y)
        assertTrue(mask.width > region.rect.width)
        assertTrue(mask.height > region.rect.height)
    }

    @Test
    fun verticalDialogueRenderBoundsExpandsNarrowDetectedColumnIntoBubbleSpace() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.58f, y = 0.14f, width = 0.030f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.92f
        )

        val renderBounds = region.effectiveRenderBoundsForKind(AiTranslationBlockKind.DIALOGUE)

        assertTrue(renderBounds.width >= 0.085f)
        assertTrue(renderBounds.height > region.rect.height)
    }

    @Test
    fun defaultAiCropBoundsUseTightExpandedDetectedTextBounds() {
        val region = AiTranslationLocalTextRegion(
            id = "p0-r1",
            rect = AiTranslationRect(x = 0.58f, y = 0.14f, width = 0.030f, height = 0.30f),
            textDirection = AiTranslationTextDirection.VERTICAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.92f,
            estimatedFontScale = 0.92f
        )

        val cropBounds = region.effectiveAiCropBounds()
        val maskBounds = region.effectiveSourceMaskBounds()
        val renderBounds = region.effectiveRenderBoundsForKind(AiTranslationBlockKind.DIALOGUE)

        assertTrue(cropBounds.width > region.rect.width)
        assertTrue(cropBounds.height > region.rect.height)
        assertTrue(cropBounds.width <= maskBounds.width)
        assertTrue(cropBounds.width < renderBounds.width)
    }

    @Test
    fun highlyOverlappingDetectionRegionsCollapseBeforeTranslation() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(0.20f, 0.20f, 0.18f, 0.16f),
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.92f,
                estimatedFontScale = 1f
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r2",
                rect = AiTranslationRect(0.21f, 0.21f, 0.17f, 0.15f),
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.88f,
                estimatedFontScale = 1f
            )
        )

        val collapsed = collapseHighlyOverlappingLocalTextRegions(regions)

        assertEquals(1, collapsed.size)
        assertEquals(2, collapsed.single().sourceColumns.size)
    }

    @Test
    fun nearbySeparateDetectionRegionsRemainIndependent() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(0.20f, 0.20f, 0.08f, 0.16f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.92f,
                estimatedFontScale = 1f
            ),
            AiTranslationLocalTextRegion(
                id = "p0-r2",
                rect = AiTranslationRect(0.30f, 0.20f, 0.08f, 0.16f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.88f,
                estimatedFontScale = 1f
            )
        )

        val collapsed = collapseHighlyOverlappingLocalTextRegions(regions)

        assertEquals(2, collapsed.size)
    }
}
