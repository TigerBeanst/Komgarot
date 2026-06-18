package fail.tiger.komgarot.data.repository

import java.io.File
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiTranslationRepositoryStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/repository/AiTranslationRepository.kt").readText()

    @Test
    fun repositoryExposesBookAndPageActions() {
        assertTrue(source.contains("fun startBookTranslation("))
        assertTrue(source.contains("suspend fun retryPageTranslation("))
        assertTrue(source.contains("fun deletePageTranslation("))
        assertTrue(source.contains("suspend fun testPageTranslationConfiguration("))
    }

    @Test
    fun retryPageTranslationIsAwaitableByReaderViewModel() {
        val signatureStart = source.indexOf("suspend fun retryPageTranslation(")
        val signatureEnd = source.indexOf("fun deletePageTranslation(", signatureStart)
        val retrySource = source.substring(signatureStart, signatureEnd)

        assertTrue(retrySource.contains("cachedPages: List<PageDto> = emptyList()"))
        assertTrue(retrySource.contains("val runMode = AiTranslationMode.LOCAL_DETECTION"))
        assertTrue(retrySource.contains("ensureBookFile(book, book.media.pagesCount, runMode)"))
        assertTrue(retrySource.contains("cachedPages = cachedPages"))
        assertTrue(retrySource.contains("AiTranslationPageActionResult("))
        assertTrue(retrySource.contains("ok = result.ok"))
        assertTrue(retrySource.contains("summary = result.summary.ifBlank"))
        assertTrue(!retrySource.contains("scope.launch"))
    }

    @Test
    fun repositoryReusesLoadedPageListForRetryAndBatchWork() {
        assertTrue(source.contains("cachedPages: List<PageDto> = emptyList()"))
        assertTrue(source.contains("knownPages: List<PageDto> = emptyList()"))
        assertTrue(source.contains("knownPages.takeIf { it.isNotEmpty() }"))
        assertTrue(source.contains("translatePendingPagesInPageOrder("))
        val batchStart = source.indexOf("private suspend fun translateBatch(")
        val batchEnd = source.indexOf("private fun saveTranslatedPages(", batchStart)
        val batchSource = source.substring(batchStart, batchEnd)
        assertTrue(batchSource.contains("pages: List<PageDto>"))
        assertTrue(!batchSource.contains("bookRepository.getPages(book.id)"))
    }

    @Test
    fun pageRetryReturnsDiagnosticSummary() {
        assertTrue(source.contains("data class AiTranslationPageActionResult("))
        assertTrue(source.contains("val summary: String = \"\""))
        assertTrue(source.contains("summary = result.summary.ifBlank"))
        assertTrue(!source.contains("AI translation failed before a diagnostic summary was produced."))
    }

    @Test
    fun batchAggregationProvidesDiagnosticSummary() {
        assertTrue(source.contains("summarizeAiTranslationResults("))
        assertTrue(source.contains("AI translation batch failed: pages="))
        assertTrue(source.contains("failedBatches="))
    }

    @Test
    fun repositoryUsesConfiguredConcurrencyAndStoresTranslatedPages() {
        assertTrue(source.contains("Semaphore("))
        assertTrue(source.contains("prefs.aiConcurrentRequests.first()"))
        assertTrue(source.contains("prefs.aiPagesPerRequest.first()"))
        assertTrue(source.contains("prefs.aiMaxImagesPerRequest.first()"))
        assertTrue(source.contains("timeoutSeconds = prefs.aiTimeoutSeconds.first()"))
        assertTrue(source.contains("timeoutSeconds = settings.timeoutSeconds"))
        assertTrue(source.contains("store.upsertPages("))
    }

    @Test
    fun fullBookTranslationStartsPagesInPageIndexOrderAcrossConcurrentWorkers() {
        assertTrue(source.contains("AtomicInteger"))
        assertTrue(source.contains("translatePendingPagesInPageOrder("))
        assertTrue(source.contains("pending.sorted()"))
        assertTrue(source.contains("val nextPageOffset = AtomicInteger(0)"))
        assertTrue(source.contains("val pageIndex = orderedPending[offset]"))
        assertTrue(source.contains("translateBatch(book, serverUrl, settings, apiKey, imageUrlExtraQuery, listOf(pageIndex), allPages)"))
    }

    @Test
    fun base64ImageInputIsCompressedBeforeSendingToAi() {
        assertTrue(source.contains("preparePageInput("))
        assertTrue(source.contains("fallbackMimeType = page.mediaType"))
        assertTrue(source.contains("compressPageImageForAi(tempFile, settings.imageMaxEdge)"))
        assertTrue(source.contains("compressPageImageForAi("))
        assertTrue(source.contains("BitmapFactory.Options().apply { inJustDecodeBounds = true }"))
        assertTrue(source.contains("Bitmap.CompressFormat.JPEG"))
        assertTrue(source.contains("AI_IMAGE_JPEG_QUALITY"))
        assertTrue(source.contains("tempFile.outputStream().use"))
        assertTrue(source.contains("Base64.getEncoder().encodeToString(compressed.bytes)"))
        assertTrue(!source.contains("Base64.getEncoder().encodeToString(body.bytes())"))
    }

    @Test
    fun repositoryBuildsRegionCropImagesAndBatchesByImageLimit() {
        assertTrue(source.contains("buildTextRegionImageInputs("))
        assertTrue(source.contains("BitmapRegionDecoder"))
        assertTrue(source.contains("toAiCropRect("))
        assertTrue(source.contains("regionImagesPerRequest(settings.maxImagesPerRequest)"))
        assertTrue(source.contains("val chunkImages = listOf(preparedPage.pageImageInput) + regionImages"))
        assertTrue(source.contains("val chunkContext = preparedPage.localContext.copy(regions = regionChunk)"))
        assertTrue(source.contains("localRegionId = region.id"))
    }

    @Test
    fun repositorySendsDetectedRegionsWithoutLocalText() {
        assertTrue(source.contains("val detectedRegionCount = localPageContexts.sumOf { it.regions.size }"))
        assertTrue(source.contains("if (detectedRegionCount <= 0)"))
        assertTrue(source.contains("Local text detection found zero text boxes"))
    }

    @Test
    fun repositoryVerifiesTranslatedPagesCanBeReadBackAfterSave() {
        assertTrue(source.contains("verifySavedTranslatedPages("))
        assertTrue(source.contains("AI translation save verification failed:"))
        assertTrue(source.contains("\"AI translation save verification failed: page=\$failedPage, savedStatus=\$savedStatus"))
        assertTrue(source.contains("savedStatus="))
        assertTrue(source.contains("requestedPages="))
        assertTrue(source.contains("savedPages="))
        assertTrue(source.contains("rawPagesCount="))
        assertTrue(source.contains("rawBookState="))
        assertTrue(source.contains("fileExists="))
        assertTrue(source.contains("file="))
    }

    @Test
    fun forcedRetryReportsFailureWhenNoPageWasQueued() {
        assertTrue(source.contains("if (pending.isEmpty())"))
        assertTrue(source.contains("failRun(book.id, pageIndexes, \"No page was queued for AI translation.\")"))
        assertTrue(source.contains("AiTranslationRunResult(ok = pageIndexes.isNotEmpty())"))
    }

    @Test
    fun repositoryStoresFailureSummariesForRetryDiagnostics() {
        assertTrue(source.contains("markPagesFailed("))
        assertTrue(source.contains("AI model configuration is incomplete."))
        assertTrue(source.contains("No page was queued for AI translation."))
        assertTrue(source.contains("No page image input was built."))
        assertTrue(source.contains("AI response did not contain parsable page translation JSON."))
    }

    @Test
    fun repositoryUsesDetectedRegionsBeforeAiRequest() {
        assertTrue(source.contains("localPageContexts.sumOf { it.regions.size }"))
        assertTrue(source.contains("buildTextRegionImageInputs("))
    }

    @Test
    fun returnedPagesAreAlignedToRequestedPageIndexes() {
        val returned = listOf(AiTranslatedPage(pageIndex = 6))

        val aligned = alignReturnedPagesToRequestedIndexes(returned, listOf(2))

        assertEquals(listOf(2), aligned.map { it.pageIndex })
    }

    @Test
    fun testTranslationBypassesGlobalAiTranslationEnabledSwitch() {
        assertTrue(source.contains("requireEnabled: Boolean"))
        assertTrue(source.contains("requireEnabled = false"))
        assertTrue(source.contains("if (requireEnabled && !settings.enabled)"))
    }

    @Test
    fun localDetectionModeBuildsLocalPageContextBeforeAiRequest() {
        assertTrue(source.contains("private val localTextDetector: AiLocalTextDetector = AiLocalTextDetector()"))
        assertTrue(source.contains("val runMode = AiTranslationMode.LOCAL_DETECTION"))
        assertTrue(source.contains("preparePageInput("))
        assertTrue(source.contains("localTextDetector.detect("))
        assertTrue(source.contains("localPageContexts = prepared.map { it.localContext }"))
        assertTrue(source.contains("localPageContexts = listOf(chunkContext)"))
    }

    @Test
    fun localDetectionPlaceholdersAreSavedBeforeAiResponse() {
        assertTrue(source.contains("localDetectionPlaceholderPage("))
        assertTrue(source.contains("store.upsertPages(bookId, listOf(localDetectionPlaceholderPage(localContext, mode)))"))
    }

    @Test
    fun localDetectionPlaceholderPageUsesRegionsAsRunningBlocks() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 7,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p7-r1",
                    rect = AiTranslationRect(x = 0.20f, y = 0.30f, width = 0.08f, height = 0.16f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.84f,
                    estimatedFontScale = 0.92f
                )
            )
        )

        val page = localDetectionPlaceholderPage(context, AiTranslationMode.LOCAL_DETECTION)

        assertEquals(7, page.pageIndex)
        assertEquals(AiTranslationPageStatus.RUNNING, page.status)
        assertEquals(1200, page.imageWidth)
        assertEquals(1800, page.imageHeight)
        assertEquals(AiTranslationMode.LOCAL_DETECTION.storedValue, page.mode)
        assertEquals(1, page.blocks.size)
        assertEquals("p7-r1", page.blocks.single().localRegionId)
        assertEquals(context.regions.single().rect, page.blocks.single().rect)
        assertEquals(context.regions.single().rect, page.blocks.single().translationRect)
        assertEquals(AiTranslationTextDirection.VERTICAL, page.blocks.single().textDirection)
        assertEquals(emptyList<String>(), page.blocks.single().translatedLines)
    }

    @Test
    fun appInjectsLocalTextDetectorWithPaddleDetector() {
        val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()

        assertTrue(appSource.contains("import fail.tiger.komgarot.data.repository.AiLocalTextDetector"))
        assertTrue(appSource.contains("import fail.tiger.komgarot.data.repository.AiPaddleTextDetector"))
        assertTrue(appSource.contains("aiLocalModelRepository = AiLocalModelRepository(filesDir)"))
        assertTrue(appSource.contains("paddleTextDetector = AiPaddleTextDetector(applicationContext, aiLocalModelRepository)"))
    }

    @Test
    fun localDetectionModeCorrectsSavedBlocksWithLocalRegions() {
        assertTrue(source.contains("translatedPagesFromLocalRegionResponse("))
        assertTrue(source.contains("buildTranslatedPageFromLocalContext("))
        assertTrue(source.contains("val localImageWidth = localContext?.imageWidth?.takeIf { it > 0 }"))
        assertTrue(source.contains("localImageWidth ?: page.imageWidth.takeIf { it > 0 } ?: 0"))
        assertTrue(source.contains(".map { it.withReadableColors() }"))
        assertTrue(!source.contains("mode == AiTranslationMode.VISION"))
        assertTrue(!source.contains("canTrustLocalRegionId("))
    }

    @Test
    fun repositoryAlwaysDownloadsPageForLocalDetectionBeforeRemoteAiTranslation() {
        assertTrue(!source.contains("mode == AiTranslationMode.VISION && settings.imageTransport == AiImageTransport.IMAGE_URL"))
        val inputStart = source.indexOf("private fun preparePageInput(")
        val inputEnd = source.indexOf("private fun updateTask(", inputStart)
        val inputSource = source.substring(inputStart, inputEnd)
        assertTrue(inputSource.contains("localTextDetector.detect(tempFile, pageIndex, settings)"))
        assertTrue(inputSource.contains("store.upsertPages(bookId, listOf(localDetectionPlaceholderPage(localContext, mode)))"))
    }

    @Test
    fun translatePagesPreservesBookModeDuringRetryAndTest() {
        assertTrue(source.contains("ensureBookFile(book, allPages.size, runMode)"))
        assertTrue(!source.contains("ensureBookFile(book, book.media.pagesCount)"))
        assertTrue(!source.contains("mode: AiTranslationMode = AiTranslationMode.VISION"))
    }

    @Test
    fun bookModeUsesPinnedModeOrExistingTranslationStateBeforeGlobalDefault() {
        assertTrue(source.contains("fun preferredModeForBook(bookId: String): AiTranslationMode = AiTranslationMode.LOCAL_DETECTION"))
        assertTrue(!source.contains("it.translation.modePinned || it.hasPageTranslationState()"))
        assertTrue(!source.contains("modePinned = true"))
    }

    @Test
    fun localRegionTranslationResponseUsesOnlyLocalRegionPlacementAndStyle() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 3,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p3-r1",
                    rect = AiTranslationRect(x = 0.70f, y = 0.12f, width = 0.08f, height = 0.24f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.91f,
                    estimatedFontScale = 0.96f
                )
            )
        )
        val json = """
            {
              "pages": [
                {
                  "pageIndex": 3,
                  "translations": [
                    {
                      "localRegionId": "p3-r1",
                      "sourceText": "まだ終わってない",
                      "translatedLines": ["还没有结束"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val page = translatedPagesFromLocalRegionResponse(
            normalizedJson = json,
            fallbackPageIndexes = listOf(3),
            localPageContexts = listOf(context),
            mode = AiTranslationMode.LOCAL_DETECTION
        ).single()
        val block = page.blocks.single()

        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals(1200, page.imageWidth)
        assertEquals(1800, page.imageHeight)
        assertEquals(AiTranslationMode.LOCAL_DETECTION.storedValue, page.mode)
        assertEquals("p3-r1", block.localRegionId)
        assertEquals(AiTranslationBlockKind.DIALOGUE, block.kind)
        assertEquals("まだ終わってない", block.sourceText)
        assertEquals(listOf("还没有结束"), block.translatedLines)
        assertEquals(context.regions.single().rect, block.rect)
        assertEquals(context.regions.single().rect, block.translationRect)
        assertEquals(AiTranslationTextDirection.VERTICAL, block.textDirection)
        assertEquals("#111111", block.textColor)
        assertEquals("#FFFFFF", block.maskColor)
        assertEquals(0.82f, block.maskAlpha)
        assertEquals(0.96f, block.fontScale)
    }
}
