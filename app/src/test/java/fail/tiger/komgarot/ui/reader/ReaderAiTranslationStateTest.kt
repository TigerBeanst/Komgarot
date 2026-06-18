package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslatedPage
import fail.tiger.komgarot.data.local.AiTranslationBlock
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import java.io.File
import org.junit.Assert.assertEquals
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
}
