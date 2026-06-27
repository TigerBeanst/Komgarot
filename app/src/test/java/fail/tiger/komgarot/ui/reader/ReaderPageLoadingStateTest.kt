package fail.tiger.komgarot.ui.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.AsyncImagePainter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageLoadingStateTest {
    private object TestPainter : Painter() {
        override val intrinsicSize: Size = Size.Unspecified

        override fun DrawScope.onDraw() = Unit
    }

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

    @Test
    fun transientLoadingStateReusesRetainedPagePainter() {
        val state = readerRetainPainterForTransientState(
            state = AsyncImagePainter.State.Loading(painter = null),
            retainedPainter = TestPainter
        )

        assertSame(TestPainter, state.painter)
    }

    @Test
    fun emptyStateDrawsRetainedPagePainter() {
        assertSame(
            TestPainter,
            readerFallbackPainterForTransientState(
                state = AsyncImagePainter.State.Empty,
                retainedPainter = TestPainter
            )
        )
    }
}
