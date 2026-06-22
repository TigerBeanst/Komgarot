package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fail.tiger.komgarot.data.local.AiImageMaxEdge
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiLocalModelSource
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiBookTranslationMetadata
import fail.tiger.komgarot.data.local.AiTranslationBlockKind
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AiTranslationTaskStatus
import fail.tiger.komgarot.data.local.AiTranslationTaskSummary
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.local.SecureAiSettingsStore
import fail.tiger.komgarot.data.remote.AiTranslationClient
import fail.tiger.komgarot.data.remote.AiTranslationImageInput
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import fail.tiger.komgarot.data.remote.AiTranslationRequestResult
import fail.tiger.komgarot.data.remote.aiTranslationSystemPrompt
import fail.tiger.komgarot.data.remote.aiTranslationUserPrompt
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto
import fail.tiger.komgarot.ui.reader.readerPageUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AiTranslationRepository(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val prefs: AuthPreferences,
    private val secureAiSettingsStore: SecureAiSettingsStore,
    private val store: AiTranslationStore,
    private val komgaHttpClient: OkHttpClient = OkHttpClient(),
    private val imageUploadHttpClient: OkHttpClient = OkHttpClient(),
    private val localTextDetector: AiLocalTextDetector = AiLocalTextDetector(),
    private val aiClient: AiTranslationClient = AiTranslationClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bookTranslationQueue = Semaphore(1)

    fun readBookState(bookId: String) = store.readBook(bookId)

    fun clearBook(bookId: String) {
        store.clearBook(bookId)
    }

    fun preferredModeForBook(bookId: String): AiTranslationMode = AiTranslationMode.LOCAL_DETECTION

    fun setPreferredMode(book: BookDto, mode: AiTranslationMode) {
        val existing = store.readBook(book.id)
        if (existing == null) {
            ensureBookFile(book, book.media.pagesCount, AiTranslationMode.LOCAL_DETECTION)
            return
        }
        store.saveBookNow(
            existing.copy(
                translation = existing.translation.copy(
                    mode = AiTranslationMode.LOCAL_DETECTION.storedValue
                )
            )
        )
    }

    fun setTranslationDisplayEnabled(enabled: Boolean) {
        scope.launch {
            prefs.setAiTranslationDisplayMode(if (enabled) "on" else "off")
        }
    }

    fun startBookTranslation(book: BookDto, serverUrl: String) {
        scope.launch {
            updateTask(book, AiTranslationTaskStatus.QUEUED)
            bookTranslationQueue.withPermit {
                val pages = bookRepository.getPages(book.id)
                ensureBookFile(book, pages.size, AiTranslationMode.LOCAL_DETECTION)
                val result = translatePages(
                    book,
                    serverUrl,
                    pages.indices.toList(),
                    force = false,
                    knownPages = pages,
                    onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
                )
                updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED)
            }
        }
    }

    fun retryIncompleteBookTranslation(book: BookDto, serverUrl: String) {
        scope.launch {
            updateTask(book, AiTranslationTaskStatus.QUEUED)
            bookTranslationQueue.withPermit {
                val pages = bookRepository.getPages(book.id)
                ensureBookFile(book, pages.size, AiTranslationMode.LOCAL_DETECTION)
                val currentPages = store.readBook(book.id)?.pages.orEmpty()
                val completed = currentPages
                    .filter { it.status == AiTranslationPageStatus.DONE }
                    .map { it.pageIndex }
                    .toSet()
                val pending = pages.indices.filterNot { it in completed }
                val result = translatePages(
                    book,
                    serverUrl,
                    pending,
                    force = true,
                    knownPages = pages,
                    cachedPages = pages,
                    onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
                )
                updateTask(book, if (result.ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED)
            }
        }
    }

    fun retryIncompleteBookTranslation(bookId: String, serverUrl: String) {
        scope.launch {
            val book = bookRepository.getBookById(bookId).getOrNull() ?: return@launch
            retryIncompleteBookTranslation(book, serverUrl)
        }
    }

    suspend fun retryPageTranslation(
        book: BookDto,
        serverUrl: String,
        pageIndex: Int,
        cachedPages: List<PageDto> = emptyList()
    ): AiTranslationPageActionResult = retryPagesTranslation(
        book = book,
        serverUrl = serverUrl,
        pageIndexes = listOf(pageIndex),
        cachedPages = cachedPages
    )

    suspend fun retryPagesTranslation(
        book: BookDto,
        serverUrl: String,
        pageIndexes: List<Int>,
        cachedPages: List<PageDto> = emptyList()
    ): AiTranslationPageActionResult {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        ensureBookFile(book, book.media.pagesCount, runMode)
        val result = bookTranslationQueue.withPermit {
            translatePages(
                book,
                serverUrl,
                pageIndexes,
                force = true,
                requireEnabled = false,
                knownPages = cachedPages,
                cachedPages = cachedPages,
                onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
            )
        }
        return AiTranslationPageActionResult(
            ok = result.ok,
            summary = result.summary.ifBlank { "AI translation retry failed: book=${book.id}, pages=${pageIndexes.joinToString(",")}, no repository diagnostic summary." }
        )
    }

    fun deletePageTranslation(bookId: String, pageIndex: Int) {
        store.deletePage(bookId, pageIndex)
    }

    suspend fun testPageTranslationConfiguration(
        book: BookDto,
        serverUrl: String,
        pageIndex: Int,
        cachedPages: List<PageDto> = emptyList()
    ): Boolean {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        ensureBookFile(book, book.media.pagesCount, runMode)
        val ok = bookTranslationQueue.withPermit {
            translatePages(
                book,
                serverUrl,
                listOf(pageIndex),
                force = true,
                requireEnabled = false,
                knownPages = cachedPages,
                cachedPages = cachedPages,
                onPageTranslated = { updateTask(book, AiTranslationTaskStatus.RUNNING) }
            )
        }.ok
        if (ok) {
            prefs.setAiConfigurationTestPassed(true)
            prefs.setAiTestModeEnabled(false)
        }
        return ok
    }

    private suspend fun translatePages(
        book: BookDto,
        serverUrl: String,
        pageIndexes: List<Int>,
        force: Boolean,
        requireEnabled: Boolean = true,
        knownPages: List<PageDto> = emptyList(),
        cachedPages: List<PageDto> = emptyList(),
        onPageTranslated: () -> Unit = {}
    ): AiTranslationRunResult = withContext(Dispatchers.IO) {
        val secure = secureAiSettingsStore.read()
        val settings = AiSettings.defaults(
            targetLocale = prefs.aiTargetLocale.first(),
            targetLanguageName = prefs.aiTargetLanguageName.first()
        ).copy(
            enabled = prefs.aiTranslationEnabled.first(),
            baseUrl = prefs.aiBaseUrl.first(),
            modelName = prefs.aiModelName.first(),
            preferredMode = AiTranslationMode.LOCAL_DETECTION,
            localModelSource = prefs.aiLocalModelSource.first(),
            modelCollectionId = prefs.aiModelCollectionId.first(),
            modelRevision = prefs.aiModelRevision.first(),
            downloadLatestModel = prefs.aiDownloadLatestModel.first(),
            autoSelectDeviceTier = prefs.aiAutoSelectDeviceTier.first(),
            imageTransport = prefs.aiImageTransport.first(),
            pagesPerRequest = prefs.aiPagesPerRequest.first(),
            concurrentRequests = prefs.aiConcurrentRequests.first(),
            maxImagesPerRequest = prefs.aiMaxImagesPerRequest.first(),
            timeoutSeconds = prefs.aiTimeoutSeconds.first(),
            imageMaxEdge = prefs.aiImageMaxEdge.first(),
            customInstructions = prefs.aiCustomInstructions.first(),
            testModeEnabled = prefs.aiTestModeEnabled.first(),
            configurationTestPassed = prefs.aiConfigurationTestPassed.first()
        )
        if (requireEnabled && !settings.enabled) {
            return@withContext failRun(book.id, pageIndexes, "AI translation is disabled in settings.")
        }
        if (!settings.hasCompleteModelConfiguration(secure.apiKey)) {
            return@withContext failRun(book.id, pageIndexes, "AI model configuration is incomplete.")
        }

        val allPages = knownPages.takeIf { it.isNotEmpty() }
            ?: cachedPages.takeIf { it.isNotEmpty() }
            ?: runCatching { bookRepository.getPages(book.id) }.getOrElse { throwable ->
                return@withContext failRun(book.id, pageIndexes, "Failed to load page list: ${throwable.message.orEmpty()}")
            }
        val runMode = AiTranslationMode.LOCAL_DETECTION
        ensureBookFile(book, allPages.size, runMode)
        val existing = store.readBook(book.id)
        val pending = pageIndexes
            .filter { it in allPages.indices }
            .filter { index ->
                force || existing?.pages?.firstOrNull { it.pageIndex == index }?.status != AiTranslationPageStatus.DONE
            }
        if (pending.isEmpty()) {
            return@withContext if (force) {
                failRun(book.id, pageIndexes, "No page was queued for AI translation.")
            } else {
                AiTranslationRunResult(ok = pageIndexes.isNotEmpty())
            }
        }

        updateTask(book, AiTranslationTaskStatus.RUNNING)
        store.upsertPages(
            book.id,
            pending.map {
                AiTranslatedPage(
                    pageIndex = it,
                    status = AiTranslationPageStatus.RUNNING,
                    mode = runMode.storedValue
                )
            }
        )

        val results = translatePendingPagesInPageOrder(
            book = book,
            serverUrl = serverUrl,
            settings = settings,
            apiKey = secure.apiKey,
            s3Uploader = secure.s3ImageUrlConfigOrNull()?.let { AiS3ImageUploader(imageUploadHttpClient, it) },
            pending = pending,
            allPages = allPages,
            onPageTranslated = onPageTranslated
        )
        val ok = results.all { it.ok }
        val summary = summarizeAiTranslationResults(pending, results)
        updateTask(book, if (ok) AiTranslationTaskStatus.DONE else AiTranslationTaskStatus.FAILED)
        AiTranslationRunResult(ok = ok, summary = summary)
    }

    private suspend fun translatePendingPagesInPageOrder(
        book: BookDto,
        serverUrl: String,
        settings: AiSettings,
        apiKey: String,
        s3Uploader: AiS3ImageUploader?,
        pending: List<Int>,
        allPages: List<PageDto>,
        onPageTranslated: () -> Unit = {}
    ): List<AiTranslationRunResult> = coroutineScope {
        val orderedPending = pending.distinct()
        val remoteWorkerCount = effectiveAiTranslationWorkerCount(settings, orderedPending.size)
        val preparationWorkerCount = effectiveAiTranslationPreparationWorkerCount(settings, orderedPending.size)
        val remoteSemaphore = Semaphore(remoteWorkerCount)
        val preparedPages = orderedPending.map { CompletableDeferred<PreparedAiPageResult>() }
        val nextPrepareOffset = AtomicInteger(0)
        val prepareJobs = (0 until preparationWorkerCount).map {
            async {
                while (true) {
                    val offset = nextPrepareOffset.getAndIncrement()
                    if (offset >= orderedPending.size) break
                    val pageIndex = orderedPending[offset]
                    val prepared = try {
                        PreparedAiPageResult.Prepared(
                            preparePageInput(
                                book = book,
                                serverUrl = serverUrl,
                                settings = settings,
                                s3Uploader = s3Uploader,
                                pageIndex = pageIndex,
                                pages = allPages
                            )
                        )
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        PreparedAiPageResult.Failed(
                            failRun(
                                book.id,
                                listOf(pageIndex),
                                "Failed to build page image input: ${throwable.message.orEmpty()}"
                            )
                        )
                    }
                    preparedPages[offset].complete(prepared)
                }
            }
        }
        val remoteJobs = mutableListOf<kotlinx.coroutines.Deferred<AiTranslationRunResult>>()
        orderedPending.indices.forEach { offset ->
            when (val prepared = preparedPages[offset].await()) {
                is PreparedAiPageResult.Failed -> {
                    remoteJobs += async {
                        onPageTranslated()
                        prepared.result
                    }
                }
                is PreparedAiPageResult.Prepared -> {
                    remoteJobs += async {
                        val result = remoteSemaphore.withPermit {
                            translatePreparedPage(
                                book = book,
                                settings = settings,
                                apiKey = apiKey,
                                prepared = listOf(prepared.input)
                            )
                        }
                        onPageTranslated()
                        result
                    }
                }
            }
        }
        prepareJobs.awaitAll()
        remoteJobs.awaitAll()
    }

    private fun ensureBookFile(book: BookDto, pageCount: Int, mode: AiTranslationMode) {
        val existing = store.readBook(book.id)
        if (existing != null) {
            if (existing.translation.mode == mode.storedValue) return
            store.saveBookNow(existing.copy(translation = existing.translation.copy(mode = mode.storedValue)))
            return
        }
        store.saveBookNow(
            AiTranslatedBook(
                bookId = book.id,
                seriesId = book.seriesId,
                title = book.metadata.title,
                pageCount = pageCount,
                translation = AiBookTranslationMetadata(
                    targetLocale = "",
                    targetLanguageName = "",
                    model = "",
                    mode = mode.storedValue
                ),
                pages = (0 until pageCount.coerceAtLeast(0)).map {
                    AiTranslatedPage(
                        pageIndex = it,
                        status = AiTranslationPageStatus.PENDING,
                        mode = mode.storedValue
                    )
                }
            )
        )
    }

    private suspend fun translateBatch(
        book: BookDto,
        serverUrl: String,
        settings: AiSettings,
        apiKey: String,
        s3Uploader: AiS3ImageUploader?,
        pageIndexes: List<Int>,
        pages: List<PageDto>
    ): AiTranslationRunResult {
        val prepared = runCatching {
            pageIndexes.mapNotNull { index ->
                preparePageInput(
                    book = book,
                    serverUrl = serverUrl,
                    settings = settings,
                    s3Uploader = s3Uploader,
                    pageIndex = index,
                    pages = pages
                )
            }
        }.getOrElse { throwable ->
            return failRun(book.id, pageIndexes, "Failed to build page image input: ${throwable.message.orEmpty()}")
        }
        return translatePreparedPage(book, settings, apiKey, prepared)
    }

    private suspend fun translatePreparedPage(
        book: BookDto,
        settings: AiSettings,
        apiKey: String,
        prepared: List<PreparedAiPageInput>
    ): AiTranslationRunResult {
        val runMode = AiTranslationMode.LOCAL_DETECTION
        val pageIndexes = prepared.map { it.localContext.pageIndex }
        if (prepared.isEmpty()) {
            return failRun(book.id, pageIndexes, "No page image input was built.")
        }
        val localPageContexts = prepared.map { it.localContext }
        val detectedRegionCount = localPageContexts.sumOf { it.regions.size }
        if (detectedRegionCount <= 0) {
            return failRun(book.id, pageIndexes, localDetectionEmptyTextMessage(localPageContexts), runMode)
        }

        val translatedPages = mutableListOf<AiTranslatedPage>()
        prepared.forEach { preparedPage ->
            val pageFragments = mutableListOf<AiTranslatedPage>()
            val regionImagesById = preparedPage.regionImageInputs.associateBy { it.localRegionId }
            val regionsWithImages = preparedPage.localContext.regions.filter { it.id in regionImagesById }
            if (regionsWithImages.isEmpty()) {
                return failRun(book.id, pageIndexes, "Failed to build text-region crop images for page=${preparedPage.localContext.pageIndex}.", runMode)
            }
            regionsWithImages
                .chunked(regionImagesPerRequest(settings.maxImagesPerRequest))
                .forEach { regionChunk ->
                    val regionImages = regionChunk.mapNotNull { region -> regionImagesById[region.id] }
                    val chunkContext = preparedPage.localContext.copy(regions = regionChunk)
                    val chunkImages = listOf(preparedPage.pageImageInput) + regionImages
                    val result = aiClient.translate(
                        baseUrl = settings.baseUrl,
                        apiKey = apiKey,
                        model = settings.modelName,
                        systemPrompt = aiTranslationSystemPrompt(),
                        userPrompt = aiTranslationUserPrompt(
                            bookId = book.id,
                            targetLocale = settings.targetLocale,
                            targetLanguageName = settings.targetLanguageName,
                            translationMode = runMode,
                            localPageContexts = listOf(chunkContext),
                            customInstructions = settings.customInstructions
                        ),
                        images = chunkImages,
                        timeoutSeconds = settings.timeoutSeconds
                    )
                    when (result) {
                        is AiTranslationRequestResult.Success -> {
                            val parsedResponsePages = parseLocalRegionTranslationResponse(result.normalizedJson)
                            if (parsedResponsePages.isEmpty()) {
                                return failRun(book.id, pageIndexes, "AI response did not contain parsable page translation JSON for page=${preparedPage.localContext.pageIndex}.", runMode)
                            }
                            val pageFragment = translatedPagesFromLocalRegionResponse(
                                normalizedJson = result.normalizedJson,
                                fallbackPageIndexes = listOf(preparedPage.localContext.pageIndex),
                                localPageContexts = listOf(chunkContext),
                                mode = runMode
                            ).firstOrNull()
                            if (pageFragment != null) pageFragments += pageFragment
                        }
                        is AiTranslationRequestResult.Failure -> {
                            return failRun(book.id, pageIndexes, "page=${preparedPage.localContext.pageIndex}: ${result.summary}", runMode)
                        }
                    }
                }
            val mergedPage = mergeTranslatedPageFragments(preparedPage.localContext, pageFragments, runMode)
                ?: return failRun(book.id, pageIndexes, "AI response did not contain any translated text-region result for page=${preparedPage.localContext.pageIndex}.", runMode)
            translatedPages += mergedPage
        }
        store.upsertPages(book.id, translatedPages)
        return verifySavedTranslatedPages(book.id, translatedPages.map { it.pageIndex })
    }

    private fun localDetectionEmptyTextMessage(localPageContexts: List<AiTranslationLocalPageContext>): String {
        val pageIndexes = localPageContexts.joinToString(",") { it.pageIndex.toString() }.ifBlank { "empty" }
        return "Local text detection found zero text boxes for pages=$pageIndexes. Download or re-download local AI models in settings, then retry this page."
    }

    private fun saveTranslatedPages(
        bookId: String,
        normalizedJson: String,
        fallbackPageIndexes: List<Int>,
        mode: AiTranslationMode,
        localPageContexts: List<AiTranslationLocalPageContext> = emptyList()
    ): AiTranslationRunResult {
        val returnedPages = translatedPagesFromLocalRegionResponse(
            normalizedJson = normalizedJson,
            fallbackPageIndexes = fallbackPageIndexes,
            localPageContexts = localPageContexts,
            mode = mode
        )
        if (returnedPages.isEmpty()) {
            return failRun(bookId, fallbackPageIndexes, "AI response did not contain parsable page translation JSON.", mode)
        }
        store.upsertPages(bookId, returnedPages)
        return verifySavedTranslatedPages(bookId, fallbackPageIndexes)
    }

    private fun verifySavedTranslatedPages(bookId: String, pageIndexes: List<Int>): AiTranslationRunResult {
        val savedBook = store.readBook(bookId)
        val savedPages = savedBook?.pages.orEmpty()
        val failedPage = pageIndexes.firstOrNull { pageIndex ->
            savedPages.firstOrNull { it.pageIndex == pageIndex }?.status != AiTranslationPageStatus.DONE
        }
        if (failedPage == null) return AiTranslationRunResult(ok = true)

        val savedStatus = savedPages.firstOrNull { it.pageIndex == failedPage }?.status?.name ?: "missing"
        val file = store.bookFile(bookId)
        val rawPagesCount = store.rawBookPageCount(bookId)?.toString() ?: "unreadable"
        val rawBookState = store.rawBookState(bookId)
        val savedPageSummary = savedPages.joinToString(",") { "${it.pageIndex}:${it.status.name}" }.ifBlank { "empty" }
        return failRun(
            bookId,
            listOf(failedPage),
            "AI translation save verification failed: page=$failedPage, savedStatus=$savedStatus, book=$bookId, requestedPages=${pageIndexes.joinToString(",")}, savedPages=$savedPageSummary, rawPagesCount=$rawPagesCount, rawBookState=$rawBookState, fileExists=${file.isFile}, file=${file.absolutePath}",
            preferredModeForBook(bookId)
        )
    }

    private fun failRun(
        bookId: String,
        pageIndexes: List<Int>,
        summary: String,
        mode: AiTranslationMode = preferredModeForBook(bookId)
    ): AiTranslationRunResult {
        val safeSummary = summary.ifBlank { "AI translation failed without a detailed error." }
        markPagesFailed(bookId, pageIndexes, safeSummary, mode)
        return AiTranslationRunResult(ok = false, summary = safeSummary.take(1200))
    }

    private fun summarizeAiTranslationResults(pageIndexes: List<Int>, results: List<AiTranslationRunResult>): String {
        val failed = results.filterNot { it.ok }
        val firstSummary = failed.firstOrNull()
            ?.summary
            ?.takeIf { it.isNotBlank() }
        return firstSummary ?: if (failed.isEmpty()) {
            ""
        } else {
            "AI translation batch failed: pages=${pageIndexes.joinToString(",")}, failedBatches=${failed.size}, totalBatches=${results.size}"
        }
    }

    private fun markPagesFailed(bookId: String, pageIndexes: List<Int>, summary: String, mode: AiTranslationMode) {
        store.upsertPages(
            bookId,
            pageIndexes.map {
                AiTranslatedPage(
                    pageIndex = it,
                    status = AiTranslationPageStatus.FAILED,
                    errorSummary = summary.take(1200),
                    mode = mode.storedValue
                )
            }
        )
    }

    private fun preparePageInput(
        book: BookDto,
        serverUrl: String,
        settings: AiSettings,
        s3Uploader: AiS3ImageUploader?,
        pageIndex: Int,
        pages: List<PageDto>
    ): PreparedAiPageInput {
        val mode = AiTranslationMode.LOCAL_DETECTION
        val bookId = book.id
        val page = pages.getOrNull(pageIndex) ?: error("page index out of bounds: $pageIndex")
        val url = readerPageUrl(serverUrl, bookId, page)
        val cachedPageFile = ensureCachedPageFile(book.seriesId, bookId, url)
        val localContextCacheKey = aiLocalContextCacheKey(cachedPageFile, settings)
        val localContext = store.readLocalPageContext(bookId, pageIndex, localContextCacheKey)
            ?: localTextDetector.detect(cachedPageFile, pageIndex, settings).also { detected ->
                store.saveLocalPageContext(bookId, pageIndex, localContextCacheKey, detected)
            }
        if (localContext.regions.isNotEmpty()) {
            store.upsertPages(bookId, listOf(localDetectionPlaceholderPage(localContext, mode)))
        }
        val compressed = compressPageImageForAi(cachedPageFile, settings.imageMaxEdge)
        val pageImageInput = imageInputFromBytes(
            bytes = compressed.bytes,
            pageIndex = pageIndex,
            mimeType = compressed.mimeType.takeIf { it.isNotBlank() } ?: page.mediaType,
            localRegionId = "",
            objectKey = s3Uploader?.objectKey(bookId, pageIndex, "page", "jpg"),
            imageTransport = settings.imageTransport,
            s3Uploader = s3Uploader
        )
        val regionImageInputs = buildTextRegionImageInputs(
            bookId = bookId,
            store = store,
            file = cachedPageFile,
            pageIndex = pageIndex,
            regions = localContext.regions,
            imageTransport = settings.imageTransport,
            s3Uploader = s3Uploader
        )
        return PreparedAiPageInput(
            pageImageInput = pageImageInput,
            localContext = localContext,
            regionImageInputs = regionImageInputs
        )
    }

    private fun ensureCachedPageFile(seriesId: String, bookId: String, url: String): File {
        ReaderPageCache.cachedFile(context, seriesId, bookId, url)?.let { return it }
        val entry = ReaderPageCache.entry(context, seriesId, bookId, url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/*,*/*;q=0.8")
            .build()
        try {
            komgaHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(response.message)
                val body = response.body ?: error("empty image body")
                entry.tempFile.parentFile?.mkdirs()
                entry.tempFile.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            if (!ReaderPageCache.commit(context, entry, prefs.readerCacheSizeBytesBlocking)) {
                error("failed to cache page image")
            }
            return ReaderPageCache.cachedFile(context, seriesId, bookId, url)
                ?: entry.file.takeIf { it.isFile && it.length() > 0L }
                ?: error("cached page image is missing")
        } catch (throwable: Throwable) {
            ReaderPageCache.discard(entry)
            throw throwable
        }
    }

    private fun updateTask(book: BookDto, status: AiTranslationTaskStatus) {
        val pages = store.readBook(book.id)?.pages.orEmpty()
        val state = store.readTaskState()
        val summary = AiTranslationTaskSummary(
            bookId = book.id,
            title = book.metadata.title,
            pageCount = book.media.pagesCount,
            completedPages = pages.count { it.status == AiTranslationPageStatus.DONE },
            failedPages = pages.count { it.status == AiTranslationPageStatus.FAILED },
            status = status,
            updatedAt = System.currentTimeMillis()
        )
        store.saveTaskState(state.copy(tasks = state.tasks.filterNot { it.bookId == book.id } + summary))
    }

    private data class PreparedAiPageInput(
        val pageImageInput: AiTranslationImageInput,
        val localContext: AiTranslationLocalPageContext,
        val regionImageInputs: List<AiTranslationImageInput>
    )

    private sealed interface PreparedAiPageResult {
        data class Prepared(val input: PreparedAiPageInput) : PreparedAiPageResult
        data class Failed(val result: AiTranslationRunResult) : PreparedAiPageResult
    }
}

data class AiTranslationPageActionResult(
    val ok: Boolean,
    val summary: String = ""
)

internal fun localDetectionPlaceholderPage(
    localContext: AiTranslationLocalPageContext,
    mode: AiTranslationMode
): AiTranslatedPage = AiTranslatedPage(
    pageIndex = localContext.pageIndex,
    status = AiTranslationPageStatus.RUNNING,
    updatedAt = System.currentTimeMillis(),
    imageWidth = localContext.imageWidth,
    imageHeight = localContext.imageHeight,
    mode = mode.storedValue,
    blocks = localContext.regions.map { region ->
        AiTranslationBlock(
            localRegionId = region.id,
            kind = AiTranslationBlockKind.OTHER,
            sourceText = "",
            translatedLines = emptyList(),
            rect = region.rect,
            translationRect = region.rect,
            textColor = ensureReadableAiTextColor(region.textColor, region.backgroundColor),
            maskColor = region.backgroundColor,
            maskAlpha = 0.55f,
            fontScale = region.estimatedFontScale,
            confidence = region.confidence,
            textDirection = region.textDirection
        )
    }
)

internal fun translatedPagesFromLocalRegionResponse(
    normalizedJson: String,
    fallbackPageIndexes: List<Int>,
    localPageContexts: List<AiTranslationLocalPageContext>,
    mode: AiTranslationMode
): List<AiTranslatedPage> {
    val responsePages = parseLocalRegionTranslationResponse(normalizedJson)
    val translationsByPage = alignLocalRegionTranslationPagesToRequestedIndexes(responsePages, fallbackPageIndexes)
        .associateBy { it.pageIndex }
    return localPageContexts.mapNotNull { context ->
        val responsePage = translationsByPage[context.pageIndex] ?: return@mapNotNull null
        buildTranslatedPageFromLocalContext(context, responsePage.translations, mode)
    }
}

internal fun buildTranslatedPageFromLocalContext(
    localContext: AiTranslationLocalPageContext,
    translations: List<AiLocalRegionTranslation>,
    mode: AiTranslationMode
): AiTranslatedPage? {
    val translationsByRegion = translations
        .filter {
            it.localRegionId.isNotBlank() &&
                it.translatedLines.any { line -> line.isNotBlank() } &&
                !isPureNumberAiTranslationSource(it.sourceText)
        }
        .associateBy { it.localRegionId }
    val blocks = localContext.regions.mapNotNull { region ->
        val translation = translationsByRegion[region.id] ?: return@mapNotNull null
        AiTranslationBlock(
            localRegionId = region.id,
            kind = translation.kind,
            sourceText = translation.sourceText,
            translatedLines = translation.translatedLines.map { it.trim() }.filter { it.isNotBlank() },
            rect = region.rect,
            translationRect = region.rect,
            textColor = ensureReadableAiTextColor(region.textColor, region.backgroundColor),
            maskColor = region.backgroundColor,
            maskAlpha = 0.82f,
            cornerRadius = 0.04f,
            rotationDegrees = 0f,
            fontScale = region.estimatedFontScale,
            confidence = region.confidence,
            textDirection = region.textDirection
        ).withReadableColors()
    }
    if (blocks.isEmpty()) return null
    return AiTranslatedPage(
        pageIndex = localContext.pageIndex,
        status = AiTranslationPageStatus.DONE,
        updatedAt = System.currentTimeMillis(),
        imageWidth = localContext.imageWidth,
        imageHeight = localContext.imageHeight,
        blocks = blocks,
        mode = mode.storedValue
    )
}

internal fun isPureNumberAiTranslationSource(value: String): Boolean {
    val compact = value.trim()
        .replace(" ", "")
        .replace("\n", "")
        .replace("\t", "")
    if (compact.isBlank()) return false
    return compact.all { char ->
        char.isPureNumberAllowedCharacter()
    } && compact.any { it.isNumericValueCharacter() }
}

private fun Char.isPureNumberAllowedCharacter(): Boolean =
    isNumericValueCharacter() || code in PURE_NUMBER_SEPARATOR_CODE_POINTS

private fun Char.isNumericValueCharacter(): Boolean =
    isDigit() || code in 0xFF10..0xFF19 || code in CJK_NUMERIC_IDEOGRAPH_CODE_POINTS

private val CJK_NUMERIC_IDEOGRAPH_CODE_POINTS = setOf(
    0x3007,
    0x96F6,
    0x4E00,
    0x4E8C,
    0x4E09,
    0x56DB,
    0x4E94,
    0x516D,
    0x4E03,
    0x516B,
    0x4E5D,
    0x5341,
    0x767E,
    0x5343,
    0x4E07
)

private val PURE_NUMBER_SEPARATOR_CODE_POINTS = setOf(
    0x002C,
    0xFF0C,
    0x002E,
    0xFF0E,
    0x30FB,
    0x002F,
    0xFF0F,
    0x002D,
    0xFF0D,
    0x2014,
    0x2013,
    0x007E,
    0x301C
)

internal data class AiLocalRegionTranslationPage(
    val pageIndex: Int,
    val translations: List<AiLocalRegionTranslation>
)

internal data class AiLocalRegionTranslation(
    val localRegionId: String,
    val sourceText: String,
    val translatedLines: List<String>,
    val kind: AiTranslationBlockKind = AiTranslationBlockKind.DIALOGUE
)

private fun parseLocalRegionTranslationResponse(text: String): List<AiLocalRegionTranslationPage> = runCatching {
    val root = JsonParser.parseString(text).asJsonObjectOrNull() ?: return@runCatching emptyList()
    val pages = root.jsonArrayOrNull("pages")?.toList()
        ?: root.takeIf { it.has("pageIndex") }?.let { listOf(it) }
        ?: emptyList()
    pages.mapNotNull { pageElement ->
        val page = pageElement.asJsonObjectOrNull() ?: return@mapNotNull null
        AiLocalRegionTranslationPage(
            pageIndex = page.intOrNull("pageIndex") ?: 0,
            translations = page.jsonArrayOrNull("translations")
                ?.mapNotNull(::parseLocalRegionTranslationElement)
                .orEmpty()
        )
    }
}.getOrDefault(emptyList())

private fun parseLocalRegionTranslationElement(element: JsonElement): AiLocalRegionTranslation? {
    val obj = element.asJsonObjectOrNull() ?: return null
    val localRegionId = obj.stringOrNull("localRegionId") ?: obj.stringOrNull("id") ?: return null
    val translatedLines = obj.jsonArrayOrNull("translatedLines")
        ?.mapNotNull { it.asStringOrNull() }
        ?.filter { it.isNotBlank() }
        ?: obj.stringOrNull("translatedText")
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(it) }
        ?: emptyList()
    return AiLocalRegionTranslation(
        localRegionId = localRegionId,
        sourceText = obj.stringOrNull("sourceText").orEmpty(),
        translatedLines = translatedLines,
        kind = parseLocalRegionTranslationKind(obj.stringOrNull("kind"))
    )
}

private fun parseLocalRegionTranslationKind(value: String?): AiTranslationBlockKind =
    when (value?.uppercase()) {
        "NARRATION" -> AiTranslationBlockKind.NARRATION
        "SFX", "SOUND_EFFECT", "SOUND" -> AiTranslationBlockKind.SFX
        "SIGN" -> AiTranslationBlockKind.SIGN
        "OTHER" -> AiTranslationBlockKind.OTHER
        else -> AiTranslationBlockKind.DIALOGUE
    }

private fun alignLocalRegionTranslationPagesToRequestedIndexes(
    pages: List<AiLocalRegionTranslationPage>,
    requestedPageIndexes: List<Int>
): List<AiLocalRegionTranslationPage> =
    if (pages.size == requestedPageIndexes.size) {
        pages.mapIndexed { index, page -> page.copy(pageIndex = requestedPageIndexes[index]) }
    } else {
        pages
    }

private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement.asStringOrNull(): String? =
    runCatching { takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

private fun JsonObject.jsonArrayOrNull(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.asStringOrNull()

private fun JsonObject.intOrNull(name: String): Int? =
    runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

internal fun correctBlocksWithLocalRegions(
    blocks: List<AiTranslationBlock>,
    regions: List<AiTranslationLocalTextRegion>
): List<AiTranslationBlock> {
    if (blocks.isEmpty() || regions.isEmpty()) return blocks
    val regionsById = regions.associateBy { it.id }
    val corrected = mutableMapOf<Int, AiTranslationBlock>()
    val correctedIndexes = mutableSetOf<Int>()
    val usedRegionIds = mutableSetOf<String>()

    blocks.forEachIndexed { index, block ->
        val region = regionsById[block.localRegionId]
        if (region != null) {
            corrected[index] = block.correctWithLocalRegion(region)
            correctedIndexes += index
            usedRegionIds += region.id
        }
    }

    val remainingRegions = regions
        .filterNot { it.id in usedRegionIds }
        .sortedWith(localRegionReadingOrder())
    val remainingIndexes = blocks.indices.filterNot { it in correctedIndexes }
    if (remainingIndexes.isEmpty() || remainingRegions.isEmpty()) return blocks.correctedBlocks(corrected)

    val orderedRegions = regions.filterNot { it.id in usedRegionIds }.sortedWith(localRegionReadingOrder())
    val orderedIndexes = blocks.indices
        .filterNot { it in correctedIndexes }
    if (orderedIndexes.isEmpty() || orderedRegions.isEmpty()) return blocks.correctedBlocks(corrected)

    if (orderedIndexes.size <= orderedRegions.size) {
        orderedIndexes.zip(orderedRegions).forEach { (index, region) ->
            corrected[index] = blocks[index].correctWithLocalRegion(region)
        }
    } else {
        orderedIndexes.zip(orderedRegions).forEach { (index, region) ->
            corrected[index] = blocks[index].correctWithLocalRegion(region)
        }
    }
    return blocks.correctedBlocks(corrected)
}

private fun List<AiTranslationBlock>.correctedBlocks(corrected: Map<Int, AiTranslationBlock>): List<AiTranslationBlock> =
    indices.mapNotNull { corrected[it] }

internal fun correctPageWithLocalContext(
    page: AiTranslatedPage,
    mode: AiTranslationMode,
    localContext: AiTranslationLocalPageContext?
): AiTranslatedPage {
    val correctedBlocks = correctBlocksWithLocalRegions(page.blocks, localContext?.regions.orEmpty())
        .map { it.withReadableColors() }
    val localImageWidth = localContext?.imageWidth?.takeIf { it > 0 }
    val localImageHeight = localContext?.imageHeight?.takeIf { it > 0 }
    return page.copy(
        mode = mode.storedValue,
        imageWidth = localImageWidth ?: page.imageWidth.takeIf { it > 0 } ?: 0,
        imageHeight = localImageHeight ?: page.imageHeight.takeIf { it > 0 } ?: 0,
        blocks = correctedBlocks
    )
}

private fun mergeTranslatedPageFragments(
    localContext: AiTranslationLocalPageContext,
    fragments: List<AiTranslatedPage>,
    mode: AiTranslationMode
): AiTranslatedPage? {
    val blocksByRegion = fragments
        .flatMap { it.blocks }
        .filter { it.localRegionId.isNotBlank() }
        .associateBy { it.localRegionId }
    val orderedBlocks = localContext.regions.mapNotNull { region -> blocksByRegion[region.id] }
    if (orderedBlocks.isEmpty()) return null
    return AiTranslatedPage(
        pageIndex = localContext.pageIndex,
        status = AiTranslationPageStatus.DONE,
        updatedAt = System.currentTimeMillis(),
        imageWidth = localContext.imageWidth,
        imageHeight = localContext.imageHeight,
        blocks = orderedBlocks,
        mode = mode.storedValue
    )
}

@Suppress("DEPRECATION")
private fun buildTextRegionImageInputs(
    bookId: String,
    store: AiTranslationStore,
    file: File,
    pageIndex: Int,
    regions: List<AiTranslationLocalTextRegion>,
    imageTransport: AiImageTransport,
    s3Uploader: AiS3ImageUploader?
): List<AiTranslationImageInput> {
    if (regions.isEmpty()) return emptyList()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val imageWidth = bounds.outWidth
    val imageHeight = bounds.outHeight
    if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
    var decoder: BitmapRegionDecoder? = null
    return try {
        regions.mapNotNull { region ->
            val cropRect = region.rect.toAiCropRect(imageWidth, imageHeight) ?: return@mapNotNull null
            val cropCacheKey = aiRegionCropCacheKey(file, cropRect)
            val bytes = store.readRegionCrop(bookId, pageIndex, region.id, cropCacheKey)
                ?: run {
                    val activeDecoder = decoder ?: BitmapRegionDecoder.newInstance(file.absolutePath, false)?.also { decoder = it }
                        ?: return@mapNotNull null
                    val bitmap = activeDecoder.decodeRegion(
                        cropRect,
                        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    ) ?: return@mapNotNull null
                    compressTextRegionCropBitmap(bitmap).also { compressed ->
                        store.saveRegionCrop(bookId, pageIndex, region.id, cropCacheKey, compressed)
                    }
                }
            imageInputFromBytes(
                bytes = bytes,
                pageIndex = pageIndex,
                mimeType = "image/jpeg",
                localRegionId = region.id,
                objectKey = s3Uploader?.objectKey(bookId, pageIndex, region.id, "jpg"),
                imageTransport = imageTransport,
                s3Uploader = s3Uploader
            )
        }
    } finally {
        decoder?.recycle()
    }
}

private fun imageInputFromBytes(
    bytes: ByteArray,
    pageIndex: Int,
    mimeType: String,
    localRegionId: String,
    objectKey: String?,
    imageTransport: AiImageTransport,
    s3Uploader: AiS3ImageUploader?
): AiTranslationImageInput {
    val imageUrl = if (imageTransport == AiImageTransport.IMAGE_URL && objectKey != null) {
        runCatching { s3Uploader?.uploadImage(bytes, mimeType, objectKey) }.getOrNull()
    } else {
        null
    }
    return if (imageUrl != null) {
        AiTranslationImageInput(
            pageIndex = pageIndex,
            transport = AiImageTransport.IMAGE_URL,
            mimeType = mimeType,
            base64 = "",
            imageUrl = imageUrl,
            localRegionId = localRegionId
        )
    } else {
        fallbackBase64Input(bytes, pageIndex, mimeType, localRegionId)
    }
}

private fun fallbackBase64Input(
    bytes: ByteArray,
    pageIndex: Int,
    mimeType: String,
    localRegionId: String = ""
): AiTranslationImageInput = AiTranslationImageInput(
    pageIndex = pageIndex,
    transport = AiImageTransport.BASE64,
    mimeType = mimeType,
    base64 = Base64.getEncoder().encodeToString(bytes),
    imageUrl = "",
    localRegionId = localRegionId
)

private fun AiTranslationRect.toAiCropRect(imageWidth: Int, imageHeight: Int): Rect? {
    if (imageWidth <= 0 || imageHeight <= 0 || width <= 0f || height <= 0f) return null
    val left = (x * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val top = (y * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
    val right = ((x + width) * imageWidth).roundToInt().coerceIn(left + 1, imageWidth)
    val bottom = ((y + height) * imageHeight).roundToInt().coerceIn(top + 1, imageHeight)
    val base = Rect(left, top, right, bottom)
    val pad = (min(base.width(), base.height()) * AI_REGION_CROP_PADDING_RATIO)
        .roundToInt()
        .coerceIn(AI_REGION_CROP_MIN_PADDING_PX, AI_REGION_CROP_MAX_PADDING_PX)
    return Rect(
        (base.left - pad).coerceAtLeast(0),
        (base.top - pad).coerceAtLeast(0),
        (base.right + pad).coerceAtMost(imageWidth),
        (base.bottom + pad).coerceAtMost(imageHeight)
    )
}

private fun compressTextRegionCropBitmap(bitmap: Bitmap): ByteArray {
    val scaled = bitmap.scaledForAiTextRegionCrop()
    return try {
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, AI_REGION_CROP_JPEG_QUALITY, output)
            output.toByteArray()
        }
    } finally {
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }
}

private fun Bitmap.scaledForAiTextRegionCrop(): Bitmap {
    val longest = max(width, height).coerceAtLeast(1)
    val shortest = min(width, height).coerceAtLeast(1)
    val minScale = AI_REGION_CROP_MIN_SHORT_EDGE / shortest.toFloat()
    val maxScale = AI_REGION_CROP_MAX_LONG_EDGE / longest.toFloat()
    val scale = min(max(minScale, 1f), maxScale).coerceIn(0.25f, 4f)
    if (scale in 0.98f..1.02f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

private fun regionImagesPerRequest(maxImagesPerRequest: Int): Int =
    AiSettings.normalizeMaxImagesPerRequest(maxImagesPerRequest) - 1

internal fun effectiveAiTranslationWorkerCount(settings: AiSettings, pendingCount: Int, maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Int {
    val configured = settings.concurrentRequests.coerceAtLeast(1)
    val memoryCap = when {
        maxMemoryBytes < 256L * 1024L * 1024L -> 1
        maxMemoryBytes < 512L * 1024L * 1024L -> 2
        else -> configured
    }
    return min(configured, memoryCap).coerceAtMost(pendingCount.coerceAtLeast(1)).coerceAtLeast(1)
}

internal fun effectiveAiTranslationPreparationWorkerCount(settings: AiSettings, pendingCount: Int, maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Int {
    val configured = settings.concurrentRequests.coerceAtLeast(1)
    val memoryCap = when {
        maxMemoryBytes < 256L * 1024L * 1024L -> 1
        maxMemoryBytes < 512L * 1024L * 1024L -> 2
        else -> min(configured + 1, 4)
    }
    return min(configured, memoryCap).coerceAtMost(pendingCount.coerceAtLeast(1)).coerceAtLeast(1)
}

private fun aiLocalContextCacheKey(file: File, settings: AiSettings): String =
    listOf(
        "local-v1",
        file.length(),
        file.lastModified(),
        settings.localModelSource.storedValue,
        settings.modelCollectionId,
        settings.modelRevision,
        settings.autoSelectDeviceTier
    ).joinToString(":")

private fun aiRegionCropCacheKey(file: File, rect: Rect): String =
    listOf(
        "region-v1",
        file.length(),
        file.lastModified(),
        rect.left,
        rect.top,
        rect.right,
        rect.bottom,
        AI_REGION_CROP_JPEG_QUALITY
    ).joinToString(":")

private fun localRegionReadingOrder(): Comparator<AiTranslationLocalTextRegion> =
    Comparator { left, right ->
        if (left.textDirection == AiTranslationTextDirection.VERTICAL || right.textDirection == AiTranslationTextDirection.VERTICAL) {
            compareValuesBy(left, right, { -it.rect.x }, { it.rect.y })
        } else {
            compareValuesBy(left, right, { it.rect.y }, { it.rect.x })
        }
    }

private const val AI_IMAGE_JPEG_QUALITY = 82
private const val AI_REGION_CROP_JPEG_QUALITY = 88
private const val AI_REGION_CROP_PADDING_RATIO = 0.08f
private const val AI_REGION_CROP_MIN_PADDING_PX = 2
private const val AI_REGION_CROP_MAX_PADDING_PX = 48
private const val AI_REGION_CROP_MIN_SHORT_EDGE = 180
private const val AI_REGION_CROP_MAX_LONG_EDGE = 1024

private data class CompressedAiImage(
    val mimeType: String,
    val bytes: ByteArray
)

private fun compressPageImageForAi(file: File, maxEdge: AiImageMaxEdge): CompressedAiImage {
    val targetMaxEdge = maxEdge.pixels
    if (targetMaxEdge == null) {
        return CompressedAiImage(
            mimeType = "image/jpeg",
            bytes = compressDecodedPageImage(file, sampleSize = 1)
        )
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sampleSize = aiImageSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxEdge = targetMaxEdge
    )
    return CompressedAiImage(
        mimeType = "image/jpeg",
        bytes = compressDecodedPageImage(file, sampleSize)
    )
}

private fun compressDecodedPageImage(file: File, sampleSize: Int): ByteArray {
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("failed to decode page image")
    return try {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AI_IMAGE_JPEG_QUALITY, output)
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

private fun aiImageSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = maxOf(width, height)
    if (longest <= 0 || longest <= maxEdge) return 1
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}

private data class AiTranslationRunResult(
    val ok: Boolean,
    val summary: String = ""
)

internal fun alignReturnedPagesToRequestedIndexes(
    returnedPages: List<AiTranslatedPage>,
    requestedPageIndexes: List<Int>
): List<AiTranslatedPage> =
    if (returnedPages.size == requestedPageIndexes.size) {
        returnedPages.mapIndexed { index, page -> page.copy(pageIndex = requestedPageIndexes[index]) }
    } else {
        returnedPages
    }

class AiTranslationQueueRunner(
    private val repository: AiTranslationRepository,
    private val store: AiTranslationStore,
    private val prefs: AuthPreferences
) {
    fun restoreRunningTasks() {
        val state = store.readTaskState()
        store.saveTaskState(state.copy(paused = state.paused))
    }
}
