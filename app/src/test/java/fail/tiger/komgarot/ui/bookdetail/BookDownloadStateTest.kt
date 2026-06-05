package fail.tiger.komgarot.ui.bookdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDownloadStateTest {
    @Test
    fun downloadingStateExposesProgress() {
        val state = BookDownloadState.Downloading(completedPages = 2, totalPages = 5)

        assertEquals(2, state.completedPages)
        assertEquals(5, state.totalPages)
        assertTrue(state.isRunning)
    }

    @Test
    fun partialCacheStateExposesProgress() {
        val state = bookDownloadStateForCachedPages(completedPages = 2, totalPages = 5)

        assertEquals(BookDownloadState.Partial(completedPages = 2, totalPages = 5), state)
        assertFalse(state.isRunning)
    }

    @Test
    fun cachedStateIsComplete() {
        val state = BookDownloadState.Cached(totalPages = 5)

        assertEquals(5, state.totalPages)
        assertFalse(state.isRunning)
    }

    @Test
    fun completeCachedPagesBecomeCachedState() {
        val state = bookDownloadStateForCachedPages(completedPages = 5, totalPages = 5)

        assertEquals(BookDownloadState.Cached(totalPages = 5), state)
    }

    @Test
    fun missingCachedPagesRemainIdle() {
        val state = bookDownloadStateForCachedPages(completedPages = 0, totalPages = 5)

        assertEquals(BookDownloadState.Idle, state)
    }
}
