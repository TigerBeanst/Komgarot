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
        assertTrue(viewModelSource.contains("pageIndexes = pageIndexes"))
    }

    @Test
    fun currentAndPreloadedAiTranslationUsesBatchRepositoryCall() {
        val repositorySource = File("src/main/java/fail/tiger/komgarot/data/repository/AiTranslationRepository.kt").readText()
        val start = viewModelSource.indexOf("private fun startCurrentAndPreloadedAiTranslation")
        val end = viewModelSource.indexOf("fun retryCurrentAiTranslationPage()", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val startSource = viewModelSource.substring(start, end)

        assertTrue(startSource.contains("repository.retryPagesTranslation("))
        assertTrue(startSource.contains("pageIndexes = pageIndexes"))
        assertFalse(startSource.contains("for (pageIndex in pageIndexes)"))
        assertFalse(startSource.contains("repository.retryPageTranslation(loaded, currentServerUrl, pageIndex, currentPages)"))
        assertTrue(repositorySource.contains("suspend fun retryPagesTranslation("))
        assertTrue(repositorySource.contains("translatePages("))
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
        assertTrue(viewModelSource.contains("isAiTranslationWorkRunning()"))
        assertTrue(viewModelSource.contains("currentAiTranslatedPage(currentPage)?.status"))
        assertTrue(viewModelSource.contains("AiTranslationPageStatus.DONE, AiTranslationPageStatus.RUNNING -> return"))
        assertTrue(viewModelSource.contains("startCurrentAndPreloadedAiTranslation(preloadPages)"))
        assertTrue(screenSource.contains("vm.translateCurrentAiPageIfDisplayEnabled(memoryAwarePreloadPages)"))
    }

    @Test
    fun aiButtonStopsRunningTranslationAndHidesOverlayResult() {
        assertTrue(viewModelSource.contains("private var aiTranslationJob: Job? = null"))
        assertTrue(viewModelSource.contains("private fun isAiTranslationWorkRunning(): Boolean"))
        assertTrue(viewModelSource.contains("stopAiTranslationWork()"))
        assertTrue(viewModelSource.contains("aiTranslationJob?.cancel()"))
        assertTrue(viewModelSource.contains("currentAiTranslationDisplayMode = AiTranslationDisplayMode.OFF"))
        assertTrue(viewModelSource.contains("prefs.setAiTranslationDisplayMode(AiTranslationDisplayMode.OFF.storedValue)"))
        assertTrue(viewModelSource.contains("clearRunningAiTranslationState()"))
        assertTrue(viewModelSource.contains("status = AiTranslationPageStatus.PENDING"))
        assertTrue(viewModelSource.contains("blocks = emptyList()"))
    }

    @Test
    fun automaticTranslationFailuresPublishRetryFeedback() {
        assertTrue(viewModelSource.contains("publishAiTranslationFailureMessage(loaded, pageIndex, updatedPage, result)"))
        assertTrue(viewModelSource.contains("private fun publishAiTranslationFailureMessage("))
        assertTrue(viewModelSource.contains("R.string.reader_ai_retry_failed,"))
        assertTrue(viewModelSource.contains("pageIndex: Int = currentPage"))
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
        assertTrue(screenSource.contains("aiTestModeEnabled = aiTranslationAvailable && aiTestModeEnabled"))
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
}
