package fail.tiger.komgarot.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapPagingTest {
    @Test
    fun tapPageTurnDisabledUsesCenterAction() {
        assertEquals(
            ReaderTapPageAction.ToggleControls,
            readerTapPageAction(
                tapX = 20f,
                width = 100f,
                tapPageTurnEnabled = false,
                einkMode = false,
                readingDirection = "LTR"
            )
        )
    }

    @Test
    fun ltrTapRegionsMapToPreviousNextAndControls() {
        assertEquals(
            ReaderTapPageAction.PreviousPage,
            readerTapPageAction(
                tapX = 20f,
                width = 100f,
                tapPageTurnEnabled = true,
                einkMode = false,
                readingDirection = "LTR"
            )
        )
        assertEquals(
            ReaderTapPageAction.ToggleControls,
            readerTapPageAction(
                tapX = 50f,
                width = 100f,
                tapPageTurnEnabled = true,
                einkMode = false,
                readingDirection = "LTR"
            )
        )
        assertEquals(
            ReaderTapPageAction.NextPage,
            readerTapPageAction(
                tapX = 80f,
                width = 100f,
                tapPageTurnEnabled = true,
                einkMode = false,
                readingDirection = "LTR"
            )
        )
    }

    @Test
    fun rtlTapRegionsReversePreviousAndNext() {
        assertEquals(
            ReaderTapPageAction.NextPage,
            readerTapPageAction(
                tapX = 20f,
                width = 100f,
                tapPageTurnEnabled = true,
                einkMode = false,
                readingDirection = "RTL"
            )
        )
        assertEquals(
            ReaderTapPageAction.PreviousPage,
            readerTapPageAction(
                tapX = 80f,
                width = 100f,
                tapPageTurnEnabled = true,
                einkMode = false,
                readingDirection = "RTL"
            )
        )
    }

    @Test
    fun einkModeEnablesTapPageTurn() {
        assertEquals(
            ReaderTapPageAction.PreviousPage,
            readerTapPageAction(
                tapX = 20f,
                width = 100f,
                tapPageTurnEnabled = false,
                einkMode = true,
                readingDirection = "LTR"
            )
        )
    }

    @Test
    fun pageIndicatorPaddingClearsBottomControls() {
        assertEquals(56, readerPageIndicatorBottomPadding(showControls = false))
        assertEquals(128, readerPageIndicatorBottomPadding(showControls = true))
    }
}
