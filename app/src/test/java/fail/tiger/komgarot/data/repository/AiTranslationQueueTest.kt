package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationQueueTest {
    @Test
    fun singlePageRetryIsInsertedBeforeNormalBookPages() {
        val queue = AiTranslationQueueState()
            .enqueueBook(bookId = "book-1", pages = listOf(0, 1, 2), pagesPerRequest = 1)
            .enqueueSinglePage(bookId = "book-1", pageIndex = 8, highPriority = true)

        val next = queue.nextBatch(maxPages = 1)

        assertEquals(listOf(AiTranslationQueuedPage("book-1", 8)), next.pages)
    }

    @Test
    fun bookPagesAreBatchedByPagesPerRequest() {
        val queue = AiTranslationQueueState()
            .enqueueBook(bookId = "book-1", pages = listOf(0, 1, 2), pagesPerRequest = 2)

        val next = queue.nextBatch(maxPages = 2)

        assertEquals(listOf(AiTranslationQueuedPage("book-1", 0), AiTranslationQueuedPage("book-1", 1)), next.pages)
    }

    @Test
    fun pausedQueueDoesNotReturnWork() {
        val queue = AiTranslationQueueState(paused = true)
            .enqueueBook(bookId = "book-1", pages = listOf(0), pagesPerRequest = 1)

        assertTrue(queue.nextBatch(maxPages = 1).pages.isEmpty())
    }
}
