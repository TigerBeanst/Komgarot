package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiTranslationStateTest {
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderViewModel.kt").readText()

    @Test
    fun refreshKeepsLocalRunningPageWhenStoreStillHasPendingState() {
        val local = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 2,
            pages = listOf(
                AiTranslatedPage(
                    pageIndex = 1,
                    status = AiTranslationPageStatus.RUNNING,
                    blocks = listOf(AiTranslationBlock(localRegionId = "p1-r1"))
                )
            )
        )
        val staleStore = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 2,
            pages = listOf(AiTranslatedPage(pageIndex = 1, status = AiTranslationPageStatus.PENDING))
        )

        val merged = mergeAiTranslationRefresh(local, staleStore)
        val page = merged?.pages?.single()

        assertEquals(AiTranslationPageStatus.RUNNING, page?.status)
        assertEquals(listOf("p1-r1"), page?.blocks?.map { it.localRegionId })
    }

    @Test
    fun refreshUsesFinishedStorePageOverLocalRunningState() {
        val local = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 1,
            pages = listOf(AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.RUNNING))
        )
        val done = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 1,
            pages = listOf(
                AiTranslatedPage(
                    pageIndex = 0,
                    status = AiTranslationPageStatus.DONE,
                    blocks = listOf(AiTranslationBlock(localRegionId = "p0-r1"))
                )
            )
        )

        val merged = mergeAiTranslationRefresh(local, done)
        val page = merged?.pages?.single()

        assertEquals(AiTranslationPageStatus.DONE, page?.status)
        assertEquals(listOf("p0-r1"), page?.blocks?.map { it.localRegionId })
    }

    @Test
    fun partialPageUpdateMergesIntoCurrentAiTranslationState() {
        val current = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 2,
            pages = listOf(AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.RUNNING))
        )
        val partial = AiTranslatedPage(
            pageIndex = 0,
            status = AiTranslationPageStatus.RUNNING,
            blocks = listOf(AiTranslationBlock(localRegionId = "p0-r1"))
        )

        val merged = mergeAiTranslationPageUpdate(current, partial)
        val page = merged?.pages?.single()

        assertEquals(AiTranslationPageStatus.RUNNING, page?.status)
        assertEquals(listOf("p0-r1"), page?.blocks?.map { it.localRegionId })
    }

    @Test
    fun progressTextCountsTranslatedBlocksDuringRunningPage() {
        val page = AiTranslatedPage(
            pageIndex = 0,
            status = AiTranslationPageStatus.RUNNING,
            blocks = listOf(
                AiTranslationBlock(localRegionId = "p0-r1", translatedLines = listOf("已完成")),
                AiTranslationBlock(localRegionId = "p0-r2"),
                AiTranslationBlock(localRegionId = "p0-r3", translatedLines = listOf("done"))
            )
        )

        assertEquals("2/3", readerAiTranslationProgressText(page))
    }

    @Test
    fun runningPartialUpdateKeepsFinishedPageState() {
        val current = AiTranslatedBook(
            bookId = "book-1",
            pageCount = 2,
            pages = listOf(
                AiTranslatedPage(
                    pageIndex = 0,
                    status = AiTranslationPageStatus.DONE,
                    blocks = listOf(AiTranslationBlock(localRegionId = "p0-final"))
                )
            )
        )
        val latePartial = AiTranslatedPage(
            pageIndex = 0,
            status = AiTranslationPageStatus.RUNNING,
            blocks = listOf(AiTranslationBlock(localRegionId = "p0-partial"))
        )

        val merged = mergeAiTranslationPageUpdate(current, latePartial)
        val page = merged?.pages?.single()

        assertEquals(AiTranslationPageStatus.DONE, page?.status)
        assertEquals(listOf("p0-final"), page?.blocks?.map { it.localRegionId })
    }

    @Test
    fun readerReceivesPartialAiTranslationPageUpdatesDuringRetry() {
        assertTrue(viewModelSource.contains("onPageUpdated = { page ->"))
        assertTrue(viewModelSource.contains("mergeAiTranslationPageUpdate(aiTranslatedBook, page)"))
    }

    @Test
    fun readerLoadsAndPersistsAiTranslationDisplayPreference() {
        assertTrue(viewModelSource.contains("prefs.aiTranslationDisplayMode.first()"))
        assertTrue(viewModelSource.contains("AiTranslationDisplayMode.fromStoredValue"))
        assertTrue(viewModelSource.contains("prefs.setAiTranslationDisplayMode(currentAiTranslationDisplayMode.storedValue)"))
        assertTrue(viewModelSource.contains("prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.ON.storedValue)"))
    }

    @Test
    fun readerViewModelExposesLoadedPageDimensionsForRenderModeSelection() {
        assertTrue(viewModelSource.contains("fun pageInfo(pageIndex: Int): PageDto?"))
        assertTrue(viewModelSource.contains("currentPages.getOrNull(pageIndex)"))
    }

    @Test
    fun aiButtonStartsCurrentAndPreloadedPagesWhenDisplayIsOff() {
        assertEquals(listOf(4, 5, 6), readerAiTranslationPageRange(currentPage = 4, pageCount = 10, preloadPages = 2))
        assertEquals(listOf(8, 9), readerAiTranslationPageRange(currentPage = 8, pageCount = 10, preloadPages = 5))
        assertTrue(viewModelSource.contains("fun handleAiTranslationButtonClick(preloadPages: Int)"))
        assertTrue(viewModelSource.contains("if (isAiTranslationWorkRunning())"))
        assertTrue(viewModelSource.contains("showCachedCurrentAiTranslationIfAvailable()"))
        assertTrue(viewModelSource.contains("startCurrentAndPreloadedAiTranslation(preloadPages)"))
        assertTrue(viewModelSource.contains("readerAiTranslationPageRange("))
        assertTrue(viewModelSource.contains("?.status != AiTranslationPageStatus.DONE"))
        assertTrue(viewModelSource.contains("if (pageIndexes.isEmpty())"))
        assertTrue(viewModelSource.contains("repository.retryPagesTranslation("))
        assertTrue(viewModelSource.contains("pageIndexes = batchPageIndexes"))
    }

    @Test
    fun activeDisplayModeExtendsAiTranslationWindowWhileWorkIsRunning() {
        val translateStart = viewModelSource.indexOf("fun translateCurrentAiPageIfDisplayEnabled(preloadPages: Int)")
        val translateEnd = viewModelSource.indexOf("private fun isAiTranslationWorkRunning()", translateStart)
        assertTrue(translateStart >= 0)
        assertTrue(translateEnd > translateStart)
        val translateSource = viewModelSource.substring(translateStart, translateEnd)

        assertTrue(viewModelSource.contains("startOrExtendCurrentAndPreloadedAiTranslation("))
        assertTrue(viewModelSource.contains("prioritizeAiTranslationPageIndexes(pageIndexes)"))
        assertTrue(translateSource.contains("startOrExtendCurrentAndPreloadedAiTranslation("))
        assertTrue(translateSource.contains("publishStartedMessage = false"))
        assertTrue(translateSource.contains("includeFailedPages = false"))
        assertFalse(translateSource.contains("if (isAiTranslationWorkRunning()) return"))
    }

    @Test
    fun currentAndPreloadedAiTranslationUsesPagePreparationPipelineForWholeWindow() {
        val repositorySource = File("src/main/java/fail/tiger/komgarot/data/repository/AiTranslationRepository.kt").readText()
        val start = viewModelSource.indexOf("private fun startCurrentAndPreloadedAiTranslation")
        val end = viewModelSource.indexOf("fun retryCurrentAiTranslationPage()", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val startSource = viewModelSource.substring(start, end)

        assertTrue(startSource.contains("prioritizeAiTranslationPageIndexes(pageIndexes)"))
        assertTrue(startSource.contains("nextAiTranslationPageBatch("))
        assertTrue(startSource.contains("repository.retryPagesTranslation("))
        assertTrue(startSource.contains("pageIndexes = batchPageIndexes"))
        assertTrue(startSource.contains("remotePageConcurrencyCap = 1"))
        assertTrue(startSource.contains("batchPageIndexes.forEach { pageIndex ->"))
        assertTrue(startSource.contains("onPageUpdated = { page ->\n                            launch { applyAiTranslationPageUpdate(page) }"))
        assertFalse(startSource.contains("repository.retryPageTranslation("))
        assertTrue(repositorySource.contains("suspend fun retryPagesTranslation("))
        assertTrue(repositorySource.contains("translatePages("))
    }

    @Test
    fun aiTranslationBatchSkipsProcessedPagesAndKeepsNewPriorityOrder() {
        assertEquals(
            listOf(7, 6, 8),
            nextAiTranslationPageBatch(
                pageIndexes = listOf(7, 6, 8, 5),
                processedPageIndexes = setOf(5)
            )
        )
        assertEquals(
            listOf(9, 7, 6, 8),
            nextAiTranslationPageBatch(
                pageIndexes = listOf(9, 7, 6, 8, 5),
                processedPageIndexes = setOf(5)
            )
        )
    }

    @Test
    fun cachedCurrentPageShowsTranslationWithoutStartedToast() {
        assertTrue(viewModelSource.contains("private fun showCachedCurrentAiTranslationIfAvailable(): Boolean"))
        assertTrue(viewModelSource.contains("repository.readBookState(loaded.id)"))
        assertTrue(viewModelSource.contains("cachedPage?.status == AiTranslationPageStatus.DONE"))
        assertTrue(viewModelSource.contains("currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON"))
        assertTrue(viewModelSource.contains("return true"))
    }

    @Test
    fun activeDisplayModeAutoTranslatesUntranslatedPageAfterJump() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()

        assertTrue(viewModelSource.contains("fun translateCurrentAiPageIfDisplayEnabled(preloadPages: Int)"))
        assertTrue(viewModelSource.contains("currentAiTranslationDisplayMode != AiTranslationDisplayMode.ON"))
        assertTrue(viewModelSource.contains("startOrExtendCurrentAndPreloadedAiTranslation("))
        assertTrue(screenSource.contains("vm.translateCurrentAiPageIfDisplayEnabled(memoryAwarePreloadPages)"))
    }

    @Test
    fun aiButtonStopsRunningTranslationAndHidesOverlayResult() {
        assertTrue(viewModelSource.contains("private var aiTranslationJob: Job? = null"))
        assertTrue(viewModelSource.contains("private var aiTranslationPageIndexes: List<Int> = emptyList()"))
        assertTrue(viewModelSource.contains("private fun isAiTranslationWorkRunning(): Boolean"))
        assertTrue(viewModelSource.contains("stopAiTranslationWork()"))
        assertTrue(viewModelSource.contains("aiTranslationJob?.cancel(userPausedAiTranslationCancellation())"))
        assertTrue(viewModelSource.contains("resetRunningAiTranslationStoreState(aiTranslationPageIndexes)"))
        assertTrue(viewModelSource.contains("currentAiTranslationDisplayMode = AiTranslationDisplayMode.OFF"))
        assertTrue(viewModelSource.contains("prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.OFF.storedValue)"))
        assertTrue(viewModelSource.contains("clearRunningAiTranslationState()"))
        assertTrue(viewModelSource.contains("status = AiTranslationPageStatus.PENDING"))
        assertTrue(viewModelSource.contains("blocks = emptyList()"))
    }

    @Test
    fun cachedCurrentPageStillSchedulesUnfinishedPreloadedPages() {
        val handlerStart = viewModelSource.indexOf("fun handleAiTranslationButtonClick(preloadPages: Int)")
        val handlerEnd = viewModelSource.indexOf("private fun showCachedCurrentAiTranslationIfAvailable()", handlerStart)
        assertTrue(handlerStart >= 0)
        assertTrue(handlerEnd > handlerStart)
        val handlerSource = viewModelSource.substring(handlerStart, handlerEnd)

        assertTrue(handlerSource.contains("val showedCachedCurrentPage = showCachedCurrentAiTranslationIfAvailable()"))
        assertTrue(handlerSource.contains("hasPendingAiTranslationPages(preloadPages)"))
        assertTrue(handlerSource.contains("startCurrentAndPreloadedAiTranslation(preloadPages)"))
        assertTrue(viewModelSource.contains("private fun hasPendingAiTranslationPages(preloadPages: Int): Boolean"))
        assertTrue(viewModelSource.contains("readerAiTranslationPageRange(currentPage, pageUrls.size, preloadPages)"))
        assertTrue(viewModelSource.contains("page?.status != AiTranslationPageStatus.DONE"))
    }

    @Test
    fun automaticTranslationFailuresPublishRetryFeedback() {
        assertTrue(viewModelSource.contains("publishAiTranslationFailureMessage(loaded, pageIndex, updatedPage, result)"))
        assertTrue(viewModelSource.contains("private fun publishAiTranslationFailureMessage("))
        assertTrue(viewModelSource.contains("R.string.reader_ai_retry_failed,"))
        assertTrue(viewModelSource.contains("pageIndex: Int = currentPage"))
    }

    @Test
    fun aiTranslationFailureToastPointsToLongPressFailureReason() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val defaultStrings = File("src/main/res/values/strings.xml").readText()
        val zhStrings = File("src/main/res/values-zh-rCN/strings.xml").readText()
        val errorMessageStart = screenSource.indexOf("private fun isAiTranslationErrorMessage(")
        val errorMessageEnd = screenSource.indexOf("private fun readerAiTimingStepLabel(", errorMessageStart)
        assertTrue(errorMessageStart >= 0)
        assertTrue(errorMessageEnd > errorMessageStart)
        val errorMessageSource = screenSource.substring(errorMessageStart, errorMessageEnd)

        assertTrue(defaultStrings.contains("AI translation failed. Long-press the translate button to view the failure reason."))
        assertTrue(zhStrings.contains("AI 翻译失败，可在长按翻译菜单查看失败原因。"))
        assertTrue(!errorMessageSource.contains("R.string.reader_ai_retry_failed"))
    }

    @Test
    fun longPressAiTranslationMenuShowsCurrentPageFailureReason() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val defaultStrings = File("src/main/res/values/strings.xml").readText()
        val zhStrings = File("src/main/res/values-zh-rCN/strings.xml").readText()

        assertTrue(screenSource.contains("var readerAiFailureDialog by remember { mutableStateOf<String?>(null) }"))
        assertTrue(screenSource.contains("val currentAiFailureSummary = vm.currentAiTranslatedPage(vm.currentPage)?.errorSummary?.takeIf { it.isNotBlank() }"))
        assertTrue(screenSource.contains("readerAiFailureDialog = currentAiFailureSummary ?: context.getString(R.string.reader_ai_failure_reason_empty)"))
        assertTrue(screenSource.contains("Text(stringResource(R.string.reader_ai_failure_reason))"))
        assertTrue(screenSource.contains("title = { Text(stringResource(R.string.reader_ai_failure_reason_title)) }"))
        assertTrue(defaultStrings.contains("<string name=\"reader_ai_failure_reason\">Failure reason</string>"))
        assertTrue(defaultStrings.contains("<string name=\"reader_ai_failure_reason_empty\">No saved failure reason for this page.</string>"))
        assertTrue(zhStrings.contains("<string name=\"reader_ai_failure_reason\">失败原因</string>"))
        assertTrue(zhStrings.contains("<string name=\"reader_ai_failure_reason_empty\">本页暂无已保存的失败原因。</string>"))
    }

    @Test
    fun readerCanClearCurrentBookAiTranslationFromLongPressMenu() {
        assertTrue(viewModelSource.contains("fun clearCurrentBookAiTranslation()"))
        assertTrue(viewModelSource.contains("aiTranslationRepository?.clearBook(loaded.id)"))
        assertTrue(viewModelSource.contains("aiTranslatedBook = aiTranslationRepository?.readBookState(loaded.id)"))
    }

    @Test
    fun readerScreenHidesAiTranslationControlsWhenFeatureIsUnavailable() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()

        assertTrue(screenSource.contains("aiTranslationAvailable: Boolean"))
        assertTrue(screenSource.contains("if (aiTranslationAvailable && aiTranslationEnabled)"))
        assertTrue(screenSource.contains("AiTranslationFloatingButton"))
        assertTrue(screenSource.contains("readerAiStatusLabel"))
        assertTrue(!screenSource.contains("aiTestModeEnabled ="))
        assertTrue(!screenSource.contains("R.string.reader_ai_test_current_page"))
        assertTrue(screenSource.contains("onClick = { vm.handleAiTranslationButtonClick(memoryAwarePreloadPages) }"))
    }

    @Test
    fun longPressTranslationMenuShowsRetryAndClearAllWithTwoStepConfirmation() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val menuStart = screenSource.indexOf("if (aiTranslationAvailable && aiTranslationEnabled && vm.showAiTranslationPageActions)")
        assertTrue(menuStart >= 0)
        val menuSource = screenSource.substring(menuStart)

        assertTrue(menuSource.contains("readerAiDeleteFirstConfirmation"))
        assertTrue(menuSource.contains("readerAiDeleteFinalConfirmation"))
        assertTrue(menuSource.contains("R.string.reader_ai_retry_current_page"))
        assertTrue(menuSource.contains("R.string.reader_ai_page_timing"))
        assertTrue(menuSource.contains("vm.currentAiTranslationTiming()"))
        assertTrue(menuSource.contains("readerAiTimingDialog"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_book_translation"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_title"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_message_first"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_continue"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_title_final"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_message_final"))
        assertTrue(menuSource.contains("R.string.ai_translate_delete_confirm"))
        assertTrue(menuSource.contains("vm.clearCurrentBookAiTranslation()"))
        assertTrue(!menuSource.contains("R.string.reader_ai_translate_current_page"))
        assertTrue(!menuSource.contains("R.string.reader_ai_delete_current_page"))
    }

    @Test
    fun aiButtonVisualStateFollowsReaderDisplayModeAcrossPages() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val buttonStart = screenSource.indexOf("AiTranslationFloatingButton(")
        assertTrue(buttonStart >= 0)
        val buttonSource = screenSource.substring(buttonStart, screenSource.indexOf(")", buttonStart) + 1)

        assertTrue(buttonSource.contains("mode = vm.currentAiTranslationDisplayMode"))
        assertTrue(buttonSource.contains("pageStatus = floatingStatus"))
    }

    @Test
    fun pageJumpKeepsAiDisplayOnByCreatingPendingTargetPage() {
        assertTrue(viewModelSource.contains("fun goToPage(page: Int)"))
        assertTrue(viewModelSource.contains("ensureAiTranslationPageShell(currentPage)"))
        assertTrue(viewModelSource.contains("currentAiTranslationDisplayMode == AiTranslationDisplayMode.ON"))
        assertTrue(viewModelSource.contains("AiTranslatedPage(pageIndex = pageIndex, mode = currentAiTranslationMode.storedValue)"))
    }

    @Test
    fun aiTranslationRequiresInstalledLocalModelBeforeStartingWork() {
        val screenSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
        val handlerStart = viewModelSource.indexOf("fun handleAiTranslationButtonClick(preloadPages: Int)")
        val handlerEnd = viewModelSource.indexOf("private fun showCachedCurrentAiTranslationIfAvailable()", handlerStart)
        assertTrue(handlerStart >= 0)
        assertTrue(handlerEnd > handlerStart)
        val handlerSource = viewModelSource.substring(handlerStart, handlerEnd)

        assertTrue(viewModelSource.contains("var showAiLocalModelRequiredDialog by mutableStateOf(false)"))
        assertTrue(viewModelSource.contains("var aiLocalModelDownloading by mutableStateOf(false)"))
        assertTrue(viewModelSource.contains("private suspend fun canStartAiTranslationWithLocalModel(): Boolean"))
        assertTrue(handlerSource.contains("canStartAiTranslationWithLocalModel()"))
        assertTrue(handlerSource.contains("showAiLocalModelRequiredDialog = true"))
        assertTrue(handlerSource.contains("return@launch"))
        assertTrue(viewModelSource.contains("fun downloadRequiredAiLocalModel()"))
        assertTrue(screenSource.contains("if (aiTranslationAvailable && aiTranslationEnabled && vm.showAiLocalModelRequiredDialog)"))
        assertTrue(screenSource.contains("R.string.reader_ai_local_model_required_title"))
        assertTrue(screenSource.contains("R.string.reader_ai_local_model_download"))
    }
}
