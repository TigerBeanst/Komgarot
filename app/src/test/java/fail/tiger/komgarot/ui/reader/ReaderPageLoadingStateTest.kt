package fail.tiger.komgarot.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageLoadingStateTest {
    @Test
    fun uncachedPageShowsLoadingPlaceholderWithoutPreviousPainter() {
        assertTrue(
            shouldShowReaderPageLoadingPlaceholder(
                isLocalCacheHit = false,
                hasPreviousPainter = false
            )
        )
    }

    @Test
    fun localCachedPageHidesLoadingPlaceholderWhileDecoding() {
        assertFalse(
            shouldShowReaderPageLoadingPlaceholder(
                isLocalCacheHit = true,
                hasPreviousPainter = false
            )
        )
    }

    @Test
    fun previousPainterIsKeptDuringLoading() {
        assertFalse(
            shouldShowReaderPageLoadingPlaceholder(
                isLocalCacheHit = false,
                hasPreviousPainter = true
            )
        )
    }
}
