package fail.tiger.komgarot.data.repository

import java.io.File
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationFailureCategory
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationRegionStatus
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import fail.tiger.komgarot.data.remote.AiTranslationRequestResult
import fail.tiger.komgarot.data.remote.AiTranslationUsage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiTranslationRepositoryStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/repository/AiTranslationRepository.kt").readText()

    @Test
    fun repositoryPropagatesTranslationBehaviorSettingsToPromptsAndRequests() {
        assertTrue(source.contains("skipSoundEffects = prefs.aiSkipSoundEffects.first()"))
        assertTrue(source.contains("reasoningEffort = prefs.aiReasoningEffort.first()"))
        assertTrue(source.contains("aiTranslationSystemPrompt(settings.skipSoundEffects)"))
        assertTrue(source.contains("skipSoundEffects = settings.skipSoundEffects"))
        assertTrue(source.contains("reasoningEffort = settings.reasoningEffort"))
    }

    @Test
    fun repositoryExposesBookAndPageActions() {
        assertTrue(source.contains("fun startBookTranslation("))
        assertTrue(source.contains("suspend fun retryPageTranslation("))
        assertTrue(source.contains("suspend fun resumePagesTranslation("))
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
    fun retryPagesClearPageCacheOnlyAfterQueuePermitIsAcquired() {
        val retryStart = source.indexOf("suspend fun retryPagesTranslation(")
        val retryEnd = source.indexOf("fun deletePageTranslation(", retryStart)
        assertTrue(retryStart >= 0)
        assertTrue(retryEnd > retryStart)
        val retrySource = source.substring(retryStart, retryEnd)

        val permitIndex = retrySource.indexOf("bookTranslationQueue.withPermit")
        val clearIndex = retrySource.indexOf("deletePageTranslation(book.id, pageIndex)")
        val translateIndex = retrySource.indexOf("translatePages(")
        assertTrue(permitIndex >= 0)
        assertTrue(clearIndex > permitIndex)
        assertTrue(translateIndex > clearIndex)
    }

    @Test
    fun resumePagesKeepsSavedPageAndSchedulesRemainingRegions() {
        val resumeStart = source.indexOf("suspend fun resumePagesTranslation(")
        val resumeEnd = source.indexOf("suspend fun retryPagesTranslation(", resumeStart)
        assertTrue(resumeStart >= 0)
        assertTrue(resumeEnd > resumeStart)
        val resumeSource = source.substring(resumeStart, resumeEnd)

        assertTrue(resumeSource.contains("translatePages("))
        assertTrue(!resumeSource.contains("deletePageTranslation("))
        assertTrue(source.contains("initialBlocksByRegion[region.id]?.regionStatus != AiTranslationRegionStatus.DONE"))
        assertTrue(source.contains("val regionChunks = remainingRegions.chunked(regionImagesPerRequest(settings))"))
    }

    @Test
    fun repositoryReusesLoadedPageListForRetryAndBatchWork() {
        assertTrue(source.contains("cachedPages: List<PageDto> = emptyList()"))
        assertTrue(source.contains("knownPages: List<PageDto> = emptyList()"))
        assertTrue(source.contains("knownPages.takeIf { it.isNotEmpty() }"))
        assertTrue(source.contains("translatePendingPagesInPageOrder("))
        val pendingStart = source.indexOf("private suspend fun translatePendingPagesInPageOrder(")
        val pendingEnd = source.indexOf("private fun ensureBookFile(", pendingStart)
        val pendingSource = source.substring(pendingStart, pendingEnd)
        assertTrue(pendingSource.contains("allPages: List<PageDto>"))
        assertTrue(!pendingSource.contains("bookRepository.getPages(book.id)"))
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
    fun bookTranslationRunsThroughGlobalQueueAndUpdatesProgressAfterEachPage() {
        assertTrue(source.contains("private val bookTranslationQueue = Semaphore(1)"))
        assertTrue(source.contains("launchTrackedBookJob(book.id)"))
        assertTrue(source.contains("operationGate.trackBookJob(bookId, job)"))
        assertTrue(source.contains("bookTranslationQueue.withPermit"))
        assertTrue(source.contains("AiTranslationTaskStatus.QUEUED"))
        assertTrue(source.contains("updateTask(book, AiTranslationTaskStatus.RUNNING)"))
        assertTrue(source.contains("onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }"))
    }

    @Test
    fun taskStateUsesTargetPageScopeAndKeepsQueuedWorkVisible() {
        val updateStart = source.indexOf("private fun updateTask(")
        val updateEnd = source.indexOf("private suspend fun awaitAiTranslationTaskResumed", updateStart)
        assertTrue(updateStart >= 0)
        assertTrue(updateEnd > updateStart)
        val updateSource = source.substring(updateStart, updateEnd)

        assertTrue(updateSource.contains("val targets = targetPageIndexes"))
        assertTrue(updateSource.contains("val targetPages = pages.filter { it.pageIndex in targetSet }"))
        assertTrue(updateSource.contains("pageCount = targets.size"))
        assertTrue(updateSource.contains("targetPageIndexes = targets"))
        assertTrue(updateSource.contains("completedPages = completedPages"))
    }

    @Test
    fun queueRunnerTurnsInterruptedWorkIntoRecoverablePausedTasks() {
        assertTrue(source.contains("interruptedBookIds.forEach(store::recoverInterruptedPages)"))
        assertTrue(source.contains("status = AiTranslationTaskStatus.PAUSED"))
        assertTrue(source.contains("recoveryRequired = true"))
        assertTrue(source.contains("paused = state.paused || interruptedBookIds.isNotEmpty()"))
    }

    @Test
    fun repositoryCanPurgeMissingCachedTranslationBooks() {
        assertTrue(source.contains("suspend fun scanMissingBookTranslations()"))
        assertTrue(source.contains("suspend fun purgeMissingBookTranslations(candidateBookIds: List<String>)"))
        assertTrue(source.contains("store.listBookIds()"))
        assertTrue(source.contains("scanAiTranslationPurgeCandidates("))
        assertTrue(source.contains("AiTranslationPurgeScanResult.Ready"))
    }

    @Test
    fun pageTranslationPreparationFollowsDynamicReaderPriority() {
        assertTrue(source.contains("translatePendingPagesInPageOrder("))
        assertTrue(source.contains("pending.distinct()"))
        assertTrue(source.contains("requestControl.scheduler.claimNextPageForPreparation()"))
        assertTrue(source.contains("preparedResults.send(prepared)"))
        assertTrue(source.contains("preparedResults.receive()"))
        assertTrue(source.contains("effectiveAiTranslationRemoteWorkerCount("))
        assertTrue(source.contains("effectiveAiTranslationPreparationWorkerCount()"))
        assertTrue(source.contains("AiTranslationWindowScheduler("))
        assertTrue(source.contains("requestControl.scheduler.markPageCompleted(prepared.pageIndex)"))
        assertTrue(source.contains("translatePreparedPage("))
    }

    @Test
    fun repositoryUsesS3ForImageUrlTransportWithoutSharingKomgaUrls() {
        val imageInputStart = source.indexOf("private fun imageInputFromBytes(")
        val imageInputEnd = source.indexOf("private fun fallbackBase64Input(", imageInputStart)
        assertTrue(imageInputStart >= 0)
        assertTrue(imageInputEnd > imageInputStart)
        val imageInputSource = source.substring(imageInputStart, imageInputEnd)

        assertTrue(source.contains("AiS3ImageUploader"))
        assertTrue(source.contains("secure.s3ImageUrlConfigOrNull()"))
        assertTrue(source.contains("private val imageUploadHttpClient: OkHttpClient = OkHttpClient()"))
        assertTrue(source.contains("AiS3ImageUploader(imageUploadHttpClient, it)"))
        assertTrue(imageInputSource.contains("val uploader = s3Uploader ?: error(\"Image URL transport requires complete S3 image URL settings.\")"))
        assertTrue(imageInputSource.contains("uploader.uploadImage(bytes, mimeType, key)"))
        assertTrue(imageInputSource.contains("transport = AiImageTransport.IMAGE_URL"))
        assertTrue(imageInputSource.contains("base64 = \"\""))
        assertTrue(imageInputSource.contains("fallbackBase64 = Base64.getEncoder().encodeToString(bytes)"))
        assertTrue(imageInputSource.contains("fallbackBase64Input("))
        assertTrue(!imageInputSource.contains("s3Uploader?.uploadImage("))
        assertTrue(!imageInputSource.contains(".getOrNull()"))
        assertTrue(!source.contains("appendImageUrlExtraQuery(url, imageUrlExtraQuery)"))
    }

    @Test
    fun repositoryCachesLocalDetectionAndRegionCropImages() {
        assertTrue(source.contains("readLocalPageContext("))
        assertTrue(source.contains("saveLocalPageContext("))
        assertTrue(source.contains("aiLocalContextCacheKey("))
        assertTrue(source.contains("\"local-v14\""))
        assertTrue(source.contains("sourceLanguage.detectionCacheKey()"))
        assertTrue(source.contains("readRegionCrop("))
        assertTrue(source.contains("saveRegionCrop("))
        assertTrue(source.contains("aiRegionCropCacheKey("))
        assertTrue(source.contains("\"region-v2\""))
    }

    @Test
    fun repositoryResolvesSeriesSourceLanguageOncePerTranslationRun() {
        val translateStart = source.indexOf("private suspend fun translatePages(")
        val translateEnd = source.indexOf("private suspend fun translatePendingPagesInPageOrder(", translateStart)
        assertTrue(translateStart >= 0)
        assertTrue(translateEnd > translateStart)
        val translateSource = source.substring(translateStart, translateEnd)

        assertEquals(1, Regex("resolveAiSourceLanguageSession\\(book\\)").findAll(translateSource).count())
        assertTrue(translateSource.contains("sourceLanguageSession = sourceLanguageSession"))
        assertTrue(translateSource.contains("sourceLanguageSession.current()"))

        val resolverStart = source.indexOf("private suspend fun resolveAiSourceLanguageSession(")
        val resolverEnd = source.indexOf("private suspend fun translatePreparedPage(", resolverStart)
        val resolverSource = source.substring(resolverStart, resolverEnd)
        assertEquals(1, Regex("bookRepository\\.getSeriesMetadata\\(").findAll(resolverSource).count())
        assertTrue(resolverSource.contains("withTimeoutOrNull(AI_SOURCE_LANGUAGE_METADATA_TIMEOUT_MS)"))
        assertTrue(resolverSource.contains("aiSourceLanguageOnMetadataFailure(book.seriesId, cachedState)"))
        assertTrue(resolverSource.contains("store.readSeriesSourceLanguage(book.seriesId)"))
        assertTrue(resolverSource.contains("store.saveSeriesSourceLanguage(resolved)"))
    }

    @Test
    fun readerTranslationResumesPausedQueueBeforeWaitingForOcr() {
        val resumeStart = source.indexOf("suspend fun resumePagesTranslation(")
        val resumeEnd = source.indexOf("suspend fun retryPagesTranslation(", resumeStart)
        val resumeSource = source.substring(resumeStart, resumeEnd)
        val retryStart = resumeEnd
        val retryEnd = source.indexOf("fun deletePageTranslation(", retryStart)
        val retrySource = source.substring(retryStart, retryEnd)

        assertTrue(resumeSource.contains("resumeAiTranslationQueueForReader()"))
        assertTrue(retrySource.contains("resumeAiTranslationQueueForReader()"))
        assertTrue(source.contains("if (state.paused) store.saveTaskState(state.copy(paused = false))"))
    }

    @Test
    fun repositoryKeepsRuntimePageTimingStatsOutsideTranslationJson() {
        assertTrue(source.contains("data class AiTranslationPageTiming("))
        assertTrue(source.contains("data class AiTranslationTimingStep("))
        assertTrue(source.contains("private val pageTimingStats = ConcurrentHashMap<String, AiTranslationPageTiming>()"))
        assertTrue(source.contains("fun readPageTiming(bookId: String, pageIndex: Int): AiTranslationPageTiming?"))
        assertTrue(source.contains("recordAiTranslationTiming("))
        assertTrue(source.contains("data class AiTranslationRequestStats("))
        assertTrue(source.contains("firstRegionVisibleMs"))
        assertTrue(source.contains("recordRequest(result)"))
        assertTrue(source.contains("recordRetry()"))
        assertTrue(source.contains("recordPageContext(pageContextStrategy, compressed.bytes.size)"))
        assertTrue(source.contains("timedAiTranslationStep("))
        assertTrue(source.contains("AI_TIMING_PAGE_IMAGE_CACHE"))
        assertTrue(source.contains("AI_TIMING_LOCAL_DETECTION_CACHE"))
        assertTrue(source.contains("AI_TIMING_PAGE_IMAGE_INPUT"))
        assertTrue(source.contains("AI_TIMING_REGION_CROP_IMAGES"))
        assertTrue(source.contains("AI_TIMING_AI_REQUEST"))
        assertTrue(source.contains("AI_TIMING_AI_RESPONSE_PARSE"))
        assertTrue(source.contains("AI_TIMING_SAVE_AND_VERIFY"))
        assertTrue(!source.contains("timingStats"))
    }

    @Test
    fun base64ImageInputIsCompressedBeforeSendingToAi() {
        assertTrue(source.contains("preparePageInput("))
        assertTrue(source.contains("strategy = pageContextStrategy"))
        assertTrue(source.contains("compressPageImageForAi("))
        assertTrue(source.contains("FULL_PAGE_768(\"full_page_768\", 768)"))
        assertTrue(source.contains("AI_PAGE_CONTEXT_JPEG_QUALITY"))
        assertTrue(source.contains("BitmapFactory.Options().apply { inJustDecodeBounds = true }"))
        assertTrue(source.contains("Bitmap.CompressFormat.JPEG"))
        assertTrue(source.contains("AI_IMAGE_JPEG_QUALITY"))
        assertTrue(source.contains("entry.tempFile.outputStream().use"))
        assertTrue(source.contains("bytes = compressed.bytes"))
        assertTrue(source.contains("fallbackBase64Input(bytes"))
        assertTrue(source.contains("Base64.getEncoder().encodeToString(bytes)"))
        assertTrue(!source.contains("Base64.getEncoder().encodeToString(body.bytes())"))
    }

    @Test
    fun pageContextMaskRectsUseOcrSourceMaskBounds() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(x = 0.20f, y = 0.30f, width = 0.10f, height = 0.20f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1.0f,
                textBounds = AiTranslationRect(x = 0.18f, y = 0.28f, width = 0.14f, height = 0.24f)
            )
        )

        val rects = pageContextTextMaskRectsForAi(
            imageWidth = 100,
            imageHeight = 200,
            regions = regions
        )

        assertEquals(1, rects.size)
        assertEquals(AiPageContextMaskRect(left = 18, top = 56, right = 32, bottom = 104), rects.single())
    }

    @Test
    fun pageContextMaskRectsUseSourceColumnsWhenAvailable() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p0-r1",
                rect = AiTranslationRect(x = 0.20f, y = 0.30f, width = 0.20f, height = 0.30f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1.0f,
                sourceColumns = listOf(
                    AiTranslationRect(x = 0.30f, y = 0.20f, width = 0.03f, height = 0.20f),
                    AiTranslationRect(x = 0.25f, y = 0.22f, width = 0.03f, height = 0.18f)
                )
            )
        )

        val rects = pageContextTextMaskRectsForAi(
            imageWidth = 100,
            imageHeight = 200,
            regions = regions
        )

        assertEquals(
            listOf(
                AiPageContextMaskRect(left = 28, top = 38, right = 35, bottom = 82),
                AiPageContextMaskRect(left = 23, top = 42, right = 30, bottom = 82)
            ),
            rects
        )
    }

    @Test
    fun repositoryBuildsRegionCropImagesAndBatchesByImageLimit() {
        assertTrue(source.contains("private class AiRegionImageInputProvider("))
        assertTrue(source.contains("BitmapRegionDecoder"))
        assertTrue(source.contains("region.effectiveAiCropBounds().toAiCropRect("))
        assertTrue(source.contains("regionImagesPerRequest(settings)"))
        assertTrue(source.contains("preparedPage.regionImageProvider.build(regionChunk)"))
        assertTrue(source.contains("val chunkImages = listOf(preparedPage.pageImageInput) + regionImages"))
        assertTrue(source.contains("val chunkContext = preparedPage.localContext.copy(regions = regionChunk)"))
        assertTrue(source.contains("localRegionId = region.id"))
    }

    @Test
    fun regionChunksTranslateConcurrentlyAndSavePartialPageResults() {
        val translateStart = source.indexOf("private suspend fun translatePreparedPage(")
        val translateEnd = source.indexOf("private fun localDetectionEmptyTextMessage(", translateStart)
        assertTrue(translateStart >= 0)
        assertTrue(translateEnd > translateStart)
        val translateSource = source.substring(translateStart, translateEnd)

        assertTrue(translateSource.contains("val regionChunks = remainingRegions.chunked(regionImagesPerRequest(settings))"))
        assertTrue(translateSource.contains("translatePreparedRegionChunks("))
        assertTrue(source.contains("requestControl.scheduler.withPermit(preparedPage.localContext.pageIndex)"))
        assertTrue(source.contains("requestControl.scheduler.recordFeedback(result)"))
        assertTrue(source.contains("requestControl.scheduler.markFirstRegionVisible("))
        assertTrue(source.contains("async {"))
        assertTrue(translateSource.contains("savePartialTranslatedPageFragment("))
        assertTrue(source.contains("saveRunningRegionChunk("))
        assertTrue(source.contains("blocks = localContext.regions.map { region -> localDetectionPlaceholderBlock(region) }"))
        assertTrue(source.contains("val current = blocksByRegion[region.id] ?: localDetectionPlaceholderBlock(region)"))
    }

    @Test
    fun regionChunksUseConfiguredSerialOrParallelMode() {
        val translateStart = source.indexOf("private suspend fun translatePreparedPage(")
        val translateEnd = source.indexOf("private suspend fun translatePreparedRegionChunk(", translateStart)
        assertTrue(translateStart >= 0)
        assertTrue(translateEnd > translateStart)
        val translateSource = source.substring(translateStart, translateEnd)

        assertTrue(source.contains("import fail.tiger.komgarot.data.local.AiTranslationRequestMode"))
        assertTrue(source.contains("requestMode = prefs.aiTranslationRequestMode.first()"))
        assertTrue(translateSource.contains("translatePreparedRegionChunks("))
        assertTrue(source.contains("private suspend fun translatePreparedRegionChunks("))
        assertTrue(source.contains("AiTranslationRequestMode.SERIAL -> regionChunks.map { regionChunk ->"))
        assertTrue(source.contains("AiTranslationRequestMode.PARALLEL -> coroutineScope"))
        assertTrue(source.contains("configuredRequestLimit = if (settings.requestMode == AiTranslationRequestMode.SERIAL)"))
    }

    @Test
    fun cancellationPropagatesWithoutMarkingPagesFailed() {
        assertTrue(source.contains("import kotlinx.coroutines.CancellationException"))
        assertTrue(source.contains("fun resetRunningPages(bookId: String, pageIndexes: List<Int>)"))
        assertTrue(source.contains("store.resetRunningPages(bookId, pageIndexes)"))

        val prepareCatchStart = source.indexOf("} catch (throwable: Throwable) {")
        val prepareCatchEnd = source.indexOf("PreparedAiPageResult.Failed", prepareCatchStart)
        assertTrue(prepareCatchStart >= 0)
        assertTrue(prepareCatchEnd > prepareCatchStart)
        val prepareCatchSource = source.substring(prepareCatchStart, prepareCatchEnd)
        assertTrue(prepareCatchSource.contains("if (throwable is CancellationException) throw throwable"))

        val resolverStart = source.indexOf("private suspend fun resolveAiSourceLanguageSession(")
        val resolverEnd = source.indexOf("private suspend fun translatePreparedPage(", resolverStart)
        assertTrue(resolverStart >= 0)
        assertTrue(resolverEnd > resolverStart)
        val resolverSource = source.substring(resolverStart, resolverEnd)
        assertTrue(resolverSource.contains("catch (error: CancellationException)"))
        assertTrue(resolverSource.contains("throw error"))
    }

    @Test
    fun emptyChunkTranslationCompletesRegionWithoutVisibleText() {
        val chunkStart = source.indexOf("private suspend fun translatePreparedRegionChunk(")
        val chunkEnd = source.indexOf("private suspend fun translateRegionChunkImages(", chunkStart)
        assertTrue(chunkStart >= 0)
        assertTrue(chunkEnd > chunkStart)
        val chunkSource = source.substring(chunkStart, chunkEnd)

        assertTrue(chunkSource.contains("pageFragment ?: emptyTranslatedRegionPage(chunkContext, runMode)"))
        assertFalse(chunkSource.contains("AI response did not bind a translation to region="))
        assertTrue(source.contains("if (chunkResult is PreparedRegionChunkResult.Success)"))
        assertTrue(source.contains("chunkResults.filterIsInstance<PreparedRegionChunkResult.Failed>()"))
        assertTrue(source.contains("requestControl.stop(chunkResult)"))
    }

    @Test
    fun emptyChunkDoesNotReportFirstVisibleTranslation() {
        val chunkStart = source.indexOf("private suspend fun translatePreparedRegionChunkAndSavePartial(")
        val chunkEnd = source.indexOf("private suspend fun translatePreparedRegionChunk(", chunkStart)
        assertTrue(chunkStart >= 0)
        assertTrue(chunkEnd > chunkStart)
        val chunkSource = source.substring(chunkStart, chunkEnd)

        assertTrue(chunkSource.contains("val hasVisibleTranslation = chunkResult.fragment.blocks.any"))
        assertTrue(chunkSource.contains("block.translatedLines.any(String::isNotBlank)"))
        assertTrue(chunkSource.contains("if (hasVisibleTranslation)"))
    }

    @Test
    fun failedRegionChunkPreservesPartialPageFragments() {
        val translateStart = source.indexOf("private suspend fun translatePreparedPage(")
        val translateEnd = source.indexOf("private fun localDetectionEmptyTextMessage(", translateStart)
        assertTrue(translateStart >= 0)
        assertTrue(translateEnd > translateStart)
        val translateSource = source.substring(translateStart, translateEnd)

        assertTrue(translateSource.contains("val failedPartialPage = saveFailedPartialTranslatedPage("))
        assertTrue(translateSource.contains("if (failedPartialPage != null) onPageUpdated(failedPartialPage)"))
        assertTrue(translateSource.contains("return AiTranslationRunResult(ok = false, summary = failedChunk.summary.take(1200))"))
        assertTrue(source.contains("private fun saveFailedPartialTranslatedPage("))
        assertTrue(source.contains("status = AiTranslationPageStatus.FAILED"))
        assertTrue(source.contains("errorSummary = summary.take(1200)"))
        assertTrue(source.contains("errorCategory = category.storedValue"))
        assertTrue(source.contains("store.upsertPages(bookId, listOf(failedPage))"))
    }

    @Test
    fun imageUrlFetchTimeoutFallsBackToBase64ChunkRetry() {
        val chunkStart = source.indexOf("private suspend fun translatePreparedRegionChunk(")
        val chunkEnd = source.indexOf("private fun savePartialTranslatedPageFragment(", chunkStart)
        assertTrue(chunkStart >= 0)
        assertTrue(chunkEnd > chunkStart)
        val chunkSource = source.substring(chunkStart, chunkEnd)

        assertTrue(chunkSource.contains("isRetryableImageUrlFetchFailure(result)"))
        assertTrue(chunkSource.contains("chunkImages.map { it.asBase64Fallback() }"))
        assertTrue(chunkSource.contains("translateRegionChunkImages("))
        assertTrue(source.contains("private fun isRetryableImageUrlFetchFailure("))
        assertTrue(source.contains("invalid_image_url"))
        assertTrue(source.contains("timeout while downloading"))
        assertTrue(source.contains("private fun isRetryableAiChunkFailure("))
        assertTrue(source.contains("delay(aiTranslationRetryDelayMs("))
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
    fun repositoryWaitsForTaskResumeBeforePreparationAndRemoteRequests() {
        assertTrue(source.contains("private suspend fun awaitAiTranslationTaskResumed("))
        assertTrue(source.contains("while (store.readTaskState().paused)"))
        assertTrue(source.contains("delay(AI_TRANSLATION_TASK_PAUSE_POLL_MS)"))

        val pendingStart = source.indexOf("private suspend fun translatePendingPagesInPageOrder(")
        val pendingEnd = source.indexOf("private fun ensureBookFile(", pendingStart)
        val pendingSource = source.substring(pendingStart, pendingEnd)
        assertTrue(pendingSource.contains("awaitAiTranslationTaskResumed()"))
        assertTrue(pendingSource.indexOf("awaitAiTranslationTaskResumed()") < pendingSource.indexOf("preparePageInput("))
        val preparedRemoteJob = pendingSource.indexOf("is PreparedAiPageResult.Prepared")
        val remoteResume = pendingSource.indexOf("awaitAiTranslationTaskResumed()", preparedRemoteJob)
        val remoteTranslate = pendingSource.indexOf("translatePreparedPage(", remoteResume)
        assertTrue(preparedRemoteJob >= 0)
        assertTrue(remoteResume > preparedRemoteJob)
        assertTrue(remoteTranslate > remoteResume)

        val chunkStart = source.indexOf("private suspend fun translatePreparedRegionChunk(")
        val chunkEnd = source.indexOf("private suspend fun translateRegionChunkImages(", chunkStart)
        val chunkSource = source.substring(chunkStart, chunkEnd)
        assertTrue(chunkSource.contains("awaitAiTranslationTaskResumed()"))
        assertTrue(chunkSource.indexOf("awaitAiTranslationTaskResumed()") < chunkSource.indexOf("translateRegionChunkImages("))
    }

    @Test
    fun repositoryStoresFailureCategoriesForTaskDiagnostics() {
        assertTrue(source.contains("AiTranslationFailureCategory"))
        assertTrue(source.contains("aiTranslationFailureCategory("))
        assertTrue(source.contains("errorCategory = category.storedValue"))
        assertTrue(source.contains("failureCategories = failedPages"))
        assertTrue(source.contains("filterKeys { it.isNotBlank() }"))
    }

    @Test
    fun failureCategoryClassifierMapsCommonFailures() {
        assertEquals(
            AiTranslationFailureCategory.MODEL_CONFIGURATION,
            aiTranslationFailureCategory("AI model configuration is incomplete.")
        )
        assertEquals(
            AiTranslationFailureCategory.LOCAL_TEXT_EMPTY,
            aiTranslationFailureCategory("Local text detection found zero text boxes for pages=1.")
        )
        assertEquals(
            AiTranslationFailureCategory.NETWORK_OR_API,
            aiTranslationFailureCategory("AI request timed out after 30s.", AiTranslationErrorCategory.NETWORK_OR_API)
        )
        assertEquals(
            AiTranslationFailureCategory.NETWORK_OR_API,
            aiTranslationFailureCategory("HTTP 429", AiTranslationErrorCategory.RATE_LIMITED)
        )
        assertEquals(
            AiTranslationFailureCategory.MODEL_CONFIGURATION,
            aiTranslationFailureCategory("HTTP 401", AiTranslationErrorCategory.AUTHENTICATION)
        )
        assertEquals(
            AiTranslationFailureCategory.NON_JSON_RESPONSE,
            aiTranslationFailureCategory("page=2: model returned plain text", AiTranslationErrorCategory.NON_JSON_RESPONSE)
        )
        assertEquals(
            AiTranslationFailureCategory.JSON_VALIDATION_FAILED,
            aiTranslationFailureCategory("AI response did not contain parsable page translation JSON.")
        )
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
        assertTrue(source.contains("preparedPage.regionImageProvider.build(regionChunk)"))
    }

    @Test
    fun repositorySplitsDetectionPreparationFromRemoteTranslation() {
        val pendingStart = source.indexOf("private suspend fun translatePendingPagesInPageOrder(")
        val pendingEnd = source.indexOf("private fun ensureBookFile(", pendingStart)
        assertTrue(pendingStart >= 0)
        assertTrue(pendingEnd > pendingStart)
        val pendingSource = source.substring(pendingStart, pendingEnd)

        assertTrue(pendingSource.contains("preparePageInput("))
        assertTrue(pendingSource.contains("translatePreparedPage("))
        assertTrue(pendingSource.indexOf("preparePageInput(") < pendingSource.indexOf("translatePreparedPage("))
        assertTrue(pendingSource.contains("Channel<PreparedAiPageResult>"))
        assertTrue(pendingSource.contains("PreparedAiPageResult.Failed"))
    }

    @Test
    fun localDetectionPlaceholderIsPublishedBeforeRemoteTranslation() {
        val prepareStart = source.indexOf("private suspend fun preparePageInput(")
        val prepareEnd = source.indexOf("private fun ensureCachedPageFile(", prepareStart)
        assertTrue(prepareStart >= 0)
        assertTrue(prepareEnd > prepareStart)
        val prepareSource = source.substring(prepareStart, prepareEnd)

        assertTrue(prepareSource.contains("onPageUpdated: (AiTranslatedPage) -> Unit"))
        assertTrue(prepareSource.contains("val resumablePage = mergeLocalDetectionPageForRegionResume(localContext, existingPage, mode)"))
        assertTrue(prepareSource.contains("store.upsertPages(bookId, listOf(resumablePage))"))
        assertTrue(prepareSource.contains("onPageUpdated(resumablePage)"))
        assertTrue(prepareSource.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(
            prepareSource.indexOf("store.upsertPages(bookId, listOf(resumablePage))") <
                prepareSource.indexOf("onPageUpdated(resumablePage)")
        )
        assertTrue(
            prepareSource.indexOf("localTextDetector.detect(") <
                prepareSource.indexOf("currentCoroutineContext().ensureActive()")
        )
        assertTrue(
            prepareSource.indexOf("currentCoroutineContext().ensureActive()") <
                prepareSource.indexOf("store.upsertPages(bookId, listOf(resumablePage))")
        )
        assertTrue(prepareSource.indexOf("onPageUpdated(resumablePage)") < prepareSource.indexOf("compressPageContextImageForAi("))
    }

    @Test
    fun dynamicWorkerCountsRespectMemoryCapsAndPendingPageCount() {
        val settings = fail.tiger.komgarot.data.local.AiSettings.defaults().copy(concurrentRequests = 4)

        assertEquals(1, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 192L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 384L * 1024L * 1024L))
        assertEquals(4, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationWorkerCount(settings, pendingCount = 2, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(1, effectiveAiTranslationPreparationWorkerCount())
    }

    @Test
    fun readerRemotePageConcurrencyCapIsAppliedAfterMemoryCap() {
        val settings = fail.tiger.komgarot.data.local.AiSettings.defaults().copy(concurrentRequests = 4)

        assertEquals(
            1,
            effectiveAiTranslationRemoteWorkerCount(
                settings = settings,
                pendingCount = 10,
                concurrencyCap = 1,
                maxMemoryBytes = 768L * 1024L * 1024L
            )
        )
        assertEquals(
            2,
            effectiveAiTranslationRemoteWorkerCount(
                settings = settings,
                pendingCount = 10,
                concurrencyCap = null,
                maxMemoryBytes = 384L * 1024L * 1024L
            )
        )
    }

    @Test
    fun chunkWorkerCountsUseMemoryAndInternalSafetyCaps() {
        val base64Settings = fail.tiger.komgarot.data.local.AiSettings.defaults().copy(
            concurrentRequests = 8,
            imageTransport = AiImageTransport.BASE64
        )
        val imageUrlSettings = base64Settings.copy(imageTransport = AiImageTransport.IMAGE_URL)

        assertEquals(4, effectiveAiTranslationChunkWorkerCount(base64Settings, chunkCount = 8, maxMemoryBytes = 256L * 1024L * 1024L))
        assertEquals(8, effectiveAiTranslationChunkWorkerCount(base64Settings, chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(4, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 8, maxMemoryBytes = 256L * 1024L * 1024L))
        assertEquals(8, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(3, effectiveAiTranslationChunkWorkerCount(imageUrlSettings.copy(concurrentRequests = 3), chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(1, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 1, maxMemoryBytes = 768L * 1024L * 1024L))
    }

    @Test
    fun retryDelayHonorsServerValueAndBoundsJitteredBackoff() {
        val rateLimited = AiTranslationRequestResult.Failure(
            category = AiTranslationErrorCategory.RATE_LIMITED,
            summary = "rate limited",
            httpStatusCode = 429,
            retryAfterMs = 2_500L
        )
        val temporary = AiTranslationRequestResult.Failure(
            category = AiTranslationErrorCategory.SERVER_TEMPORARY,
            summary = "temporary"
        )

        assertEquals(2_500L, aiTranslationRetryDelayMs(rateLimited, retryIndex = 0, jitterUnit = 0.0))
        assertEquals(375L, aiTranslationRetryDelayMs(temporary, retryIndex = 0, jitterUnit = 0.0))
        assertEquals(625L, aiTranslationRetryDelayMs(temporary, retryIndex = 0, jitterUnit = 1.0))
        assertEquals(1_000L, aiTranslationRetryDelayMs(temporary, retryIndex = 1, jitterUnit = 0.5))
    }

    @Test
    fun pageContextStrategiesExposeComparableCandidatesAndUse768ByDefault() {
        assertEquals(512, AiPageContextStrategy.FULL_PAGE_512.maxEdge)
        assertEquals(768, AiPageContextStrategy.FULL_PAGE_768.maxEdge)
        assertEquals(1024, AiPageContextStrategy.FULL_PAGE_1024.maxEdge)
        assertEquals(768, AiPageContextStrategy.LOCAL_PANEL_768.maxEdge)
        assertTrue(source.contains("pageContextStrategy: AiPageContextStrategy = AiPageContextStrategy.FULL_PAGE_768"))
        assertTrue(source.contains("compressLocalPanelContextImageForAi(file, regions, strategy.maxEdge)"))
        assertTrue(source.contains("withOpaquePageContextPixelMasks("))
    }

    @Test
    fun contextBenchmarkRecordKeepsTimingUsageAndManualQuality() {
        val timing = AiTranslationPageTiming(
            pageIndex = 4,
            totalMs = 2_800L,
            steps = emptyList(),
            requestStats = AiTranslationRequestStats(
                regionCount = 6,
                requestCount = 6,
                retryCount = 1,
                firstRegionVisibleMs = 720L,
                pageCompletedMs = 2_800L,
                usage = AiTranslationUsage(promptTokens = 400L, completionTokens = 90L, totalTokens = 490L),
                pageContextStrategy = AiPageContextStrategy.FULL_PAGE_768.storedValue,
                pageContextBytes = 82_000,
                configuredConcurrency = 8,
                peakConcurrency = 7,
                concurrencyDownshiftCount = 1
            )
        )

        val record = timing.toContextBenchmarkRecord(manualQualityScore = 6)

        assertEquals("full_page_768", record.strategy)
        assertEquals(720L, record.firstRegionVisibleMs)
        assertEquals(490L, record.usage.totalTokens)
        assertEquals(8, record.configuredConcurrency)
        assertEquals(7, record.peakConcurrency)
        assertEquals(1, record.concurrencyDownshiftCount)
        assertEquals(5, record.manualQualityScore)
    }

    @Test
    fun fatalRequestFailureStopsWaitingRegionsAfterPermitAcquisition() {
        assertTrue(source.contains("private class AiTranslationRequestControl"))
        assertTrue(source.contains("requestControl.stoppingFailure()?.let { return it }"))
        assertTrue(source.contains("requestControl.stop(chunkResult)"))
        assertTrue(source.contains("val requestControl = AiTranslationRequestControl("))
        assertTrue(source.contains("scheduler = AiTranslationWindowScheduler("))
    }

    @Test
    fun localRegionChunksUseOneRegionImagePerAiRequestForAllProfiles() {
        val japaneseSettings = AiSettings.defaults().copy(
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA,
            maxImagesPerRequest = 20
        )
        val koreanSettings = japaneseSettings.copy(sourceTextProfile = AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON)
        val horizontalSettings = japaneseSettings.copy(sourceTextProfile = AiSourceTextProfile.HORIZONTAL_COMIC)
        val autoSettings = japaneseSettings.copy(sourceTextProfile = AiSourceTextProfile.AUTO)

        assertEquals(1, regionImagesPerRequest(japaneseSettings))
        assertEquals(1, regionImagesPerRequest(koreanSettings))
        assertEquals(1, regionImagesPerRequest(horizontalSettings))
        assertEquals(1, regionImagesPerRequest(autoSettings))
    }

    @Test
    fun regionCropPreparationYieldsBetweenRegions() {
        val cropStart = source.indexOf("private class AiRegionImageInputProvider(")
        val cropEnd = source.indexOf("private fun imageInputFromBytes(", cropStart)
        assertTrue(cropStart >= 0)
        assertTrue(cropEnd > cropStart)
        val cropSource = source.substring(cropStart, cropEnd)

        assertTrue(cropSource.contains("yield()"))
        assertTrue(cropSource.contains("private val decoderMutex = Mutex()"))
        assertTrue(cropSource.contains("decoderMutex.withLock"))
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
        val testStart = source.indexOf("suspend fun testPageTranslationConfiguration(")
        val testEnd = source.indexOf("private suspend fun translatePages(", testStart)
        val testSource = source.substring(testStart, testEnd)
        assertTrue(testSource.contains("deletePageTranslation(book.id, pageIndex)"))
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
        assertTrue(source.contains("mergeLocalDetectionPageForRegionResume("))
        assertTrue(source.contains("store.upsertPages(bookId, listOf(resumablePage))"))
    }

    @Test
    fun pagePreparationUsesSchedulerPriorityAndCompletionOrder() {
        assertTrue(source.contains("claimNextPageForPreparation()"))
        assertTrue(source.contains("Channel<PreparedAiPageResult>"))
        assertTrue(source.contains("preparedResults.receive()"))
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
        assertEquals(AiTranslationRegionStatus.PENDING, page.blocks.single().regionStatus)
        assertEquals(1200, page.imageWidth)
        assertEquals(1800, page.imageHeight)
        assertEquals(AiTranslationMode.LOCAL_DETECTION.storedValue, page.mode)
        assertEquals(1, page.blocks.size)
        assertEquals("p7-r1", page.blocks.single().localRegionId)
        assertEquals(context.regions.single().effectiveSourceMaskBounds(), page.blocks.single().rect)
        assertEquals(context.regions.single().rect, page.blocks.single().translationRect)
        assertEquals(AiTranslationTextDirection.VERTICAL, page.blocks.single().textDirection)
        assertEquals(emptyList<String>(), page.blocks.single().translatedLines)
    }

    @Test
    fun resumablePageKeepsDoneRegionAndResetsRemainingRegion() {
        val regions = listOf(
            AiTranslationLocalTextRegion(
                id = "p1-r1",
                rect = AiTranslationRect(x = 0.10f, y = 0.10f, width = 0.20f, height = 0.10f),
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1f
            ),
            AiTranslationLocalTextRegion(
                id = "p1-r2",
                rect = AiTranslationRect(x = 0.10f, y = 0.30f, width = 0.20f, height = 0.10f),
                textDirection = AiTranslationTextDirection.HORIZONTAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.9f,
                estimatedFontScale = 1f
            )
        )
        val completed = AiTranslationBlock(
            localRegionId = "p1-r1",
            regionStatus = AiTranslationRegionStatus.DONE,
            translatedLines = listOf("完成")
        )
        val failed = AiTranslationBlock(
            localRegionId = "p1-r2",
            regionStatus = AiTranslationRegionStatus.FAILED
        )

        val page = mergeLocalDetectionPageForRegionResume(
            localContext = AiTranslationLocalPageContext(1, 1000, 1400, regions),
            existingPage = AiTranslatedPage(
                pageIndex = 1,
                status = AiTranslationPageStatus.FAILED,
                blocks = listOf(completed, failed)
            ),
            mode = AiTranslationMode.LOCAL_DETECTION
        )

        assertEquals(AiTranslationPageStatus.RUNNING, page.status)
        assertEquals(listOf("p1-r1", "p1-r2"), page.blocks.map { it.localRegionId })
        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.PENDING),
            page.blocks.map { it.regionStatus }
        )
        assertEquals(listOf("完成"), page.blocks.first().translatedLines)
    }

    @Test
    fun localDetectionPlaceholderPageUsesEffectiveMaskAndRenderBounds() {
        val textBounds = AiTranslationRect(x = 0.22f, y = 0.32f, width = 0.05f, height = 0.12f)
        val renderBounds = AiTranslationRect(x = 0.20f, y = 0.30f, width = 0.08f, height = 0.16f)
        val context = AiTranslationLocalPageContext(
            pageIndex = 7,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p7-r1",
                    rect = AiTranslationRect(x = 0.18f, y = 0.28f, width = 0.14f, height = 0.22f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.84f,
                    estimatedFontScale = 0.92f,
                    textBounds = textBounds,
                    renderBounds = renderBounds
                )
            )
        )

        val block = localDetectionPlaceholderPage(context, AiTranslationMode.LOCAL_DETECTION).blocks.single()

        assertEquals(textBounds, block.rect)
        assertEquals(renderBounds, block.translationRect)
    }

    @Test
    fun appInjectsLocalTextDetectorWithPaddleDetector() {
        val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()

        assertTrue(appSource.contains("import fail.tiger.komgarot.data.repository.AiLocalTextDetector"))
        assertTrue(appSource.contains("import fail.tiger.komgarot.data.repository.AiPaddleTextDetector"))
        assertTrue(appSource.contains("aiLocalModelRepository = AiLocalModelRepository(filesDir)"))
        assertTrue(appSource.contains("if (BuildConfig.AI_TRANSLATION_AVAILABLE)"))
        assertTrue(appSource.contains("aiTranslationRepositoryOrNull"))
        assertTrue(appSource.contains("aiPaddleTextDetector = AiPaddleTextDetector(applicationContext, aiLocalModelRepository)"))
        assertTrue(appSource.contains("paddleTextDetector = aiPaddleTextDetector"))
        assertTrue(appSource.contains("aiPaddleTextDetector?.close()"))
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
        val inputStart = source.indexOf("private suspend fun preparePageInput(")
        val inputEnd = source.indexOf("private fun updateTask(", inputStart)
        val inputSource = source.substring(inputStart, inputEnd)
        assertTrue(inputSource.contains("ensureCachedPageFile(book.seriesId, bookId, url)"))
        assertTrue(inputSource.contains("localTextDetector.detect("))
        assertTrue(inputSource.contains("onTimingStep = timingRecorder::add"))
        assertTrue(inputSource.contains("val resumablePage = mergeLocalDetectionPageForRegionResume(localContext, existingPage, mode)"))
        assertTrue(inputSource.contains("store.upsertPages(bookId, listOf(resumablePage))"))
    }

    @Test
    fun repositoryUsesReaderPageCacheBeforeAiProcessing() {
        assertTrue(source.contains("import fail.tiger.komgarot.data.local.ReaderPageCache"))
        assertTrue(source.contains("private fun ensureCachedPageFile("))
        assertTrue(source.contains("ReaderPageCache.cachedFile(context, seriesId, bookId, url)"))
        assertTrue(source.contains("ReaderPageCache.entry(context, seriesId, bookId, url)"))
        assertTrue(source.contains("ReaderPageCache.commit(context, entry, prefs.readerCacheSizeBytesBlocking)"))
        val inputStart = source.indexOf("private suspend fun preparePageInput(")
        val inputEnd = source.indexOf("private fun ensureCachedPageFile(", inputStart)
        val inputSource = source.substring(inputStart, inputEnd)
        assertTrue(inputSource.contains("file = cachedPageFile"))
        assertTrue(inputSource.contains("compressPageContextImageForAi("))
        assertTrue(inputSource.contains("strategy = pageContextStrategy"))
        assertTrue(!source.contains("File.createTempFile(\"ai-page-\", \".img\", context.cacheDir)"))
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
                      "sourceText": "Sample source text",
                      "translatedLines": ["示例译文"]
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
        assertEquals("Sample source text", block.sourceText)
        assertEquals(listOf("示例译文"), block.translatedLines)
        assertEquals(context.regions.single().effectiveSourceMaskBounds(), block.rect)
        assertTrue(block.translationRect.width > context.regions.single().rect.width)
        assertTrue(block.translationRect.height >= context.regions.single().rect.height)
        assertEquals(AiTranslationTextDirection.VERTICAL, block.textDirection)
        assertEquals("#111111", block.textColor)
        assertEquals("#FFFFFF", block.maskColor)
        assertEquals(0.82f, block.maskAlpha)
        assertEquals(0.96f, block.fontScale)
    }

    @Test
    fun singleRegionResponseWithoutLocalRegionIdIsBoundToOnlyLocalRegion() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 3,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p3-r1",
                    rect = AiTranslationRect(x = 0.15f, y = 0.20f, width = 0.12f, height = 0.22f),
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
              "pageIndex": 3,
              "translations": [
                {
                  "sourceText": "Sample source text",
                  "translatedLines": ["示例译文"],
                  "kind": "dialogue"
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

        assertEquals("p3-r1", page.blocks.single().localRegionId)
        assertEquals("Sample source text", page.blocks.single().sourceText)
        assertEquals(listOf("示例译文"), page.blocks.single().translatedLines)
    }

    @Test
    fun singleRegionResponseWithHallucinatedLocalRegionIdIsBoundToOnlyLocalRegion() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 3,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p3-r1",
                    rect = AiTranslationRect(x = 0.15f, y = 0.20f, width = 0.12f, height = 0.22f),
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
              "pageIndex": 3,
              "translations": [
                {
                  "localRegionId": "p3-r99",
                  "sourceText": "Sample source text",
                  "translatedLines": ["示例译文"],
                  "kind": "dialogue"
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

        assertEquals("p3-r1", page.blocks.single().localRegionId)
        assertEquals(context.regions.single().effectiveSourceMaskBounds(), page.blocks.single().rect)
        assertEquals("Sample source text", page.blocks.single().sourceText)
        assertEquals(listOf("示例译文"), page.blocks.single().translatedLines)
    }

    @Test
    fun emptyRegionTranslationMarksLocalRegionDoneWithoutRenderedText() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 3,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p3-r1",
                    rect = AiTranslationRect(x = 0.15f, y = 0.20f, width = 0.12f, height = 0.22f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.91f,
                    estimatedFontScale = 0.96f
                )
            )
        )

        val page = emptyTranslatedRegionPage(context, AiTranslationMode.LOCAL_DETECTION)
        val block = page.blocks.single()

        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals("p3-r1", block.localRegionId)
        assertEquals(AiTranslationRegionStatus.DONE, block.regionStatus)
        assertTrue(block.translatedLines.isEmpty())
    }

    @Test
    fun singleRegionResponseWithMultipleValidTranslationsBindsFirstResultToOnlyRegion() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 3,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p3-r1",
                    rect = AiTranslationRect(x = 0.15f, y = 0.20f, width = 0.12f, height = 0.22f),
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
              "pageIndex": 3,
              "translations": [
                {
                  "sourceText": "Sample source text",
                  "translatedLines": ["当前区域译文"],
                  "kind": "dialogue"
                },
                {
                  "sourceText": "Other page text",
                  "translatedLines": ["其它区域译文"],
                  "kind": "dialogue"
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

        assertEquals("p3-r1", page.blocks.single().localRegionId)
        assertEquals("Sample source text", page.blocks.single().sourceText)
        assertEquals(listOf("当前区域译文"), page.blocks.single().translatedLines)
    }

    @Test
    fun localCaptionTranslationKeepsRegionRotationAndUsesSignKind() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 4,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p4-r1",
                    rect = AiTranslationRect(x = 0.12f, y = 0.16f, width = 0.28f, height = 0.08f),
                    textDirection = AiTranslationTextDirection.HORIZONTAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.91f,
                    estimatedFontScale = 1.0f,
                    rotationDegrees = -7f
                )
            )
        )
        val json = """
            {
              "pageIndex": 4,
              "translations": [
                {
                  "localRegionId": "p4-r1",
                  "sourceText": "案内板",
                  "translatedLines": ["导览牌"],
                  "kind": "caption"
                }
              ]
            }
        """.trimIndent()

        val page = translatedPagesFromLocalRegionResponse(
            normalizedJson = json,
            fallbackPageIndexes = listOf(4),
            localPageContexts = listOf(context),
            mode = AiTranslationMode.LOCAL_DETECTION
        ).single()
        val block = page.blocks.single()

        assertEquals(AiTranslationBlockKind.SIGN, block.kind)
        assertEquals(AiTranslationTextDirection.HORIZONTAL, block.textDirection)
        assertEquals(context.regions.single().effectiveSourceMaskBounds(), block.rect)
        assertEquals(
            context.regions.single().effectiveRenderBoundsForKind(AiTranslationBlockKind.SIGN),
            block.translationRect
        )
        assertEquals(-7f, block.rotationDegrees)
    }

    @Test
    fun sourceLanguageEvidenceUsesNormalizedEligibleRegionResponses() {
        val parsed = parseLocalRegionTranslationResponse(
            """
            {
              "pageIndex": 1,
              "translations": [
                {
                  "sourceText": "안녕하세요",
                  "translatedLines": ["你好"],
                  "kind": "dialogue",
                  "detectedSourceLanguage": "KOR"
                }
              ]
            }
            """.trimIndent()
        ).single().translations.single()

        assertEquals("ko", parsed.detectedSourceLanguage)
        assertTrue(isEligibleAiSourceLanguageEvidence(parsed))
        assertTrue(
            !isEligibleAiSourceLanguageEvidence(
                parsed.copy(kind = AiTranslationBlockKind.SFX)
            )
        )
        assertTrue(
            !isEligibleAiSourceLanguageEvidence(
                parsed.copy(sourceText = "2048")
            )
        )
        assertTrue(
            !isEligibleAiSourceLanguageEvidence(
                parsed.copy(sourceText = "")
            )
        )
        assertTrue(
            !isEligibleAiSourceLanguageEvidence(
                parsed.copy(detectedSourceLanguage = "")
            )
        )
    }

    @Test
    fun pureNumberRegionsAreSkippedFromTranslatedOverlayBlocks() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 2,
            imageWidth = 1000,
            imageHeight = 1400,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p2-r1",
                    rect = AiTranslationRect(x = 0.40f, y = 0.20f, width = 0.08f, height = 0.08f),
                    textDirection = AiTranslationTextDirection.HORIZONTAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.90f,
                    estimatedFontScale = 1.0f
                )
            )
        )
        val json = """
            {
              "pages": [
                {
                  "pageIndex": 2,
                  "translations": [
                    {
                      "localRegionId": "p2-r1",
                      "sourceText": "12345",
                      "translatedLines": ["12345"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val pages = translatedPagesFromLocalRegionResponse(
            normalizedJson = json,
            fallbackPageIndexes = listOf(2),
            localPageContexts = listOf(context),
            mode = AiTranslationMode.LOCAL_DETECTION
        )

        assertTrue(pages.isEmpty())
    }
}
