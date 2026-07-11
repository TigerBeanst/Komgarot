package fail.tiger.komgarot.data.repository

import java.io.File
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationFailureCategory
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
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
    fun bookTranslationRunsThroughGlobalQueueAndUpdatesProgressAfterEachPage() {
        assertTrue(source.contains("private val bookTranslationQueue = Semaphore(1)"))
        assertTrue(source.contains("bookTranslationQueue.withPermit"))
        assertTrue(source.contains("AiTranslationTaskStatus.QUEUED"))
        assertTrue(source.contains("updateTask(book, AiTranslationTaskStatus.RUNNING)"))
        assertTrue(source.contains("onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }"))
    }

    @Test
    fun taskStateSkipsBooksWithoutTranslatedPages() {
        val updateStart = source.indexOf("private fun updateTask(")
        val updateEnd = source.indexOf("private suspend fun awaitAiTranslationTaskResumed", updateStart)
        assertTrue(updateStart >= 0)
        assertTrue(updateEnd > updateStart)
        val updateSource = source.substring(updateStart, updateEnd)

        assertTrue(updateSource.contains("val completedPages = pages.count { it.status == AiTranslationPageStatus.DONE }"))
        assertTrue(updateSource.contains("if (completedPages == 0)"))
        assertTrue(updateSource.contains("store.saveTaskState(state.copy(tasks = state.tasks.filterNot { it.bookId == book.id }))"))
        assertTrue(updateSource.contains("completedPages = completedPages"))
    }

    @Test
    fun repositoryCanPurgeMissingCachedTranslationBooks() {
        assertTrue(source.contains("suspend fun purgeMissingBookTranslations()"))
        assertTrue(source.contains("store.listBookIds()"))
        assertTrue(source.contains("bookRepository.getBookById(bookId).isFailure"))
        assertTrue(source.contains("clearBook(bookId)"))
    }

    @Test
    fun fullBookTranslationStartsPagesInPageIndexOrderAcrossConcurrentWorkers() {
        assertTrue(source.contains("AtomicInteger"))
        assertTrue(source.contains("translatePendingPagesInPageOrder("))
        assertTrue(source.contains("pending.distinct()"))
        assertTrue(source.contains("val nextPrepareOffset = AtomicInteger(0)"))
        assertTrue(source.contains("val pageIndex = orderedPending[offset]"))
        assertTrue(source.contains("effectiveAiTranslationRemoteWorkerCount("))
        assertTrue(source.contains("effectiveAiTranslationPreparationWorkerCount(settings, orderedPending.size)"))
        assertTrue(source.contains("remoteSemaphore.withPermit"))
        assertTrue(source.contains("translateBatch("))
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
        assertTrue(source.contains("\"local-v13\""))
        assertTrue(source.contains("readRegionCrop("))
        assertTrue(source.contains("saveRegionCrop("))
        assertTrue(source.contains("aiRegionCropCacheKey("))
        assertTrue(source.contains("\"region-v2\""))
    }

    @Test
    fun repositoryKeepsRuntimePageTimingStatsOutsideTranslationJson() {
        assertTrue(source.contains("data class AiTranslationPageTiming("))
        assertTrue(source.contains("data class AiTranslationTimingStep("))
        assertTrue(source.contains("private val pageTimingStats = ConcurrentHashMap<String, AiTranslationPageTiming>()"))
        assertTrue(source.contains("fun readPageTiming(bookId: String, pageIndex: Int): AiTranslationPageTiming?"))
        assertTrue(source.contains("recordAiTranslationTiming("))
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
        assertTrue(source.contains("compressPageContextImageForAi(cachedPageFile, localContext.regions)"))
        assertTrue(source.contains("compressPageImageForAi("))
        assertTrue(source.contains("AI_PAGE_CONTEXT_MAX_EDGE"))
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
        assertTrue(source.contains("buildTextRegionImageInputs("))
        assertTrue(source.contains("BitmapRegionDecoder"))
        assertTrue(source.contains("region.effectiveAiCropBounds().toAiCropRect("))
        assertTrue(source.contains("regionImagesPerRequest(settings)"))
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

        assertTrue(translateSource.contains("val regionChunks = regionsWithImages.chunked(regionImagesPerRequest(settings))"))
        assertTrue(translateSource.contains("translatePreparedRegionChunks("))
        assertTrue(source.contains("val chunkWorkerCount = effectiveAiTranslationChunkWorkerCount(settings, regionChunks.size)"))
        assertTrue(source.contains("val chunkSemaphore = Semaphore(chunkWorkerCount)"))
        assertTrue(source.contains("chunkSemaphore.withPermit"))
        assertTrue(source.contains("async {"))
        assertTrue(translateSource.contains("savePartialTranslatedPageFragment("))
        assertTrue(source.contains("blocks = localContext.regions.map(::localDetectionPlaceholderBlock)"))
        assertTrue(source.contains("blocksByRegion[region.id] ?: if (status == AiTranslationPageStatus.RUNNING)"))
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
        assertTrue(source.contains("effectiveAiTranslationChunkWorkerCount(settings, regionChunks.size)"))
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

        val batchStart = source.indexOf("private suspend fun translateBatch(")
        val batchEnd = source.indexOf("return translatePreparedPage(book, settings, apiKey, prepared)", batchStart)
        assertTrue(batchStart >= 0)
        assertTrue(batchEnd > batchStart)
        val batchSource = source.substring(batchStart, batchEnd)
        assertTrue(batchSource.contains("if (throwable is CancellationException) throw throwable"))
    }

    @Test
    fun emptyChunkTranslationsAreSkippedInsteadOfFailingWholePage() {
        val chunkStart = source.indexOf("private suspend fun translatePreparedRegionChunk(")
        val chunkEnd = source.indexOf("private suspend fun translateRegionChunkImages(", chunkStart)
        assertTrue(chunkStart >= 0)
        assertTrue(chunkEnd > chunkStart)
        val chunkSource = source.substring(chunkStart, chunkEnd)

        assertTrue(source.contains("data object Empty : PreparedRegionChunkResult"))
        assertTrue(chunkSource.contains("PreparedRegionChunkResult.Empty"))
        assertTrue(source.contains("?: emptyTranslatedPage(preparedPage.localContext, runMode)"))
        assertTrue(source.contains("if (chunkResult is PreparedRegionChunkResult.Success)"))
        assertTrue(source.contains("chunkResults.filterIsInstance<PreparedRegionChunkResult.Failed>()"))
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
        assertTrue(source.contains("delay(AI_TRANSLATION_CHUNK_RETRY_DELAY_MS)"))
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
        assertTrue(pendingSource.indexOf("awaitAiTranslationTaskResumed()", pendingSource.indexOf("remoteSemaphore.withPermit")) > 0)

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
        assertTrue(source.contains("buildTextRegionImageInputs("))
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
        assertTrue(pendingSource.contains("CompletableDeferred<PreparedAiPageResult>"))
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
        assertTrue(prepareSource.contains("val placeholderPage = localDetectionPlaceholderPage(localContext, mode)"))
        assertTrue(prepareSource.contains("store.upsertPages(bookId, listOf(placeholderPage))"))
        assertTrue(prepareSource.contains("onPageUpdated(placeholderPage)"))
        assertTrue(prepareSource.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(
            prepareSource.indexOf("store.upsertPages(bookId, listOf(placeholderPage))") <
                prepareSource.indexOf("onPageUpdated(placeholderPage)")
        )
        assertTrue(
            prepareSource.indexOf("localTextDetector.detect(") <
                prepareSource.indexOf("currentCoroutineContext().ensureActive()")
        )
        assertTrue(
            prepareSource.indexOf("currentCoroutineContext().ensureActive()") <
                prepareSource.indexOf("store.upsertPages(bookId, listOf(placeholderPage))")
        )
        assertTrue(prepareSource.indexOf("onPageUpdated(placeholderPage)") < prepareSource.indexOf("compressPageContextImageForAi("))
    }

    @Test
    fun dynamicWorkerCountsRespectMemoryCapsAndPendingPageCount() {
        val settings = fail.tiger.komgarot.data.local.AiSettings.defaults().copy(concurrentRequests = 4)

        assertEquals(1, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 192L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 384L * 1024L * 1024L))
        assertEquals(4, effectiveAiTranslationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationWorkerCount(settings, pendingCount = 2, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(1, effectiveAiTranslationPreparationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 192L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationPreparationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 384L * 1024L * 1024L))
        assertEquals(2, effectiveAiTranslationPreparationWorkerCount(settings, pendingCount = 10, maxMemoryBytes = 768L * 1024L * 1024L))
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
    fun chunkWorkerCountsUseTransportCaps() {
        val base64Settings = fail.tiger.komgarot.data.local.AiSettings.defaults().copy(
            concurrentRequests = 8,
            imageTransport = AiImageTransport.BASE64
        )
        val imageUrlSettings = base64Settings.copy(imageTransport = AiImageTransport.IMAGE_URL)

        assertEquals(4, effectiveAiTranslationChunkWorkerCount(base64Settings, chunkCount = 8, maxMemoryBytes = 256L * 1024L * 1024L))
        assertEquals(4, effectiveAiTranslationChunkWorkerCount(base64Settings, chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(8, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 8, maxMemoryBytes = 256L * 1024L * 1024L))
        assertEquals(8, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(3, effectiveAiTranslationChunkWorkerCount(imageUrlSettings.copy(concurrentRequests = 3), chunkCount = 8, maxMemoryBytes = 768L * 1024L * 1024L))
        assertEquals(1, effectiveAiTranslationChunkWorkerCount(imageUrlSettings, chunkCount = 1, maxMemoryBytes = 768L * 1024L * 1024L))
    }

    @Test
    fun localRegionChunksUseOneRegionImagePerAiRequestForAllProfiles() {
        val japaneseSettings = AiSettings.defaults().copy(
            sourceTextProfile = AiSourceTextProfile.JAPANESE_MANGA,
            maxImagesPerRequest = 20
        )
        val koreanSettings = japaneseSettings.copy(sourceTextProfile = AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON)
        val autoSettings = japaneseSettings.copy(sourceTextProfile = AiSourceTextProfile.AUTO)

        assertEquals(1, regionImagesPerRequest(japaneseSettings))
        assertEquals(1, regionImagesPerRequest(koreanSettings))
        assertEquals(1, regionImagesPerRequest(autoSettings))
    }

    @Test
    fun regionCropPreparationYieldsBetweenRegions() {
        val cropStart = source.indexOf("private suspend fun buildTextRegionImageInputs(")
        val cropEnd = source.indexOf("private fun imageInputFromBytes(", cropStart)
        assertTrue(cropStart >= 0)
        assertTrue(cropEnd > cropStart)
        val cropSource = source.substring(cropStart, cropEnd)

        assertTrue(cropSource.contains("yield()"))
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
        assertTrue(source.contains("val placeholderPage = localDetectionPlaceholderPage(localContext, mode)"))
        assertTrue(source.contains("store.upsertPages(bookId, listOf(placeholderPage))"))
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
        assertEquals(context.regions.single().effectiveSourceMaskBounds(), page.blocks.single().rect)
        assertEquals(context.regions.single().rect, page.blocks.single().translationRect)
        assertEquals(AiTranslationTextDirection.VERTICAL, page.blocks.single().textDirection)
        assertEquals(emptyList<String>(), page.blocks.single().translatedLines)
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
        val inputStart = source.indexOf("private suspend fun preparePageInput(")
        val inputEnd = source.indexOf("private fun updateTask(", inputStart)
        val inputSource = source.substring(inputStart, inputEnd)
        assertTrue(inputSource.contains("ensureCachedPageFile(book.seriesId, bookId, url)"))
        assertTrue(inputSource.contains("localTextDetector.detect("))
        assertTrue(inputSource.contains("onTimingStep = timingRecorder::add"))
        assertTrue(inputSource.contains("val placeholderPage = localDetectionPlaceholderPage(localContext, mode)"))
        assertTrue(inputSource.contains("store.upsertPages(bookId, listOf(placeholderPage))"))
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
        assertTrue(inputSource.contains("compressPageContextImageForAi(cachedPageFile, localContext.regions)"))
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
