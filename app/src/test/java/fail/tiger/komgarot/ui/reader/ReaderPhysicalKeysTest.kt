package fail.tiger.komgarot.ui.reader

import android.view.KeyEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPhysicalKeysTest {
    @Test
    fun einkPageKeysMapToPageTurns() {
        assertEquals(
            ReaderPhysicalKeyAction.NextPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_DOWN, einkMode = true, readingDirection = "LTR")
        )
        assertEquals(
            ReaderPhysicalKeyAction.PreviousPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_UP, einkMode = true, readingDirection = "LTR")
        )
    }

    @Test
    fun einkVolumeKeysMapToPageTurns() {
        assertEquals(
            ReaderPhysicalKeyAction.NextPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN, einkMode = true, readingDirection = "LTR")
        )
        assertEquals(
            ReaderPhysicalKeyAction.PreviousPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_VOLUME_UP, einkMode = true, readingDirection = "LTR")
        )
    }

    @Test
    fun einkDirectionalKeysRespectReadingDirection() {
        assertEquals(
            ReaderPhysicalKeyAction.PreviousPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, einkMode = true, readingDirection = "LTR")
        )
        assertEquals(
            ReaderPhysicalKeyAction.NextPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, einkMode = true, readingDirection = "LTR")
        )
        assertEquals(
            ReaderPhysicalKeyAction.NextPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, einkMode = true, readingDirection = "RTL")
        )
        assertEquals(
            ReaderPhysicalKeyAction.PreviousPage,
            readerPhysicalKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, einkMode = true, readingDirection = "RTL")
        )
    }

    @Test
    fun physicalKeysAreIgnoredOutsideEinkMode() {
        assertNull(readerPhysicalKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN, einkMode = false, readingDirection = "LTR"))
        assertNull(readerPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_DOWN, einkMode = false, readingDirection = "LTR"))
    }

    @Test
    fun scrollReaderUsesPhysicalKeysAsScrollCommands() {
        assertEquals(
            ReaderPhysicalKeyAction.ScrollForward,
            readerScrollPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_DOWN, einkMode = true)
        )
        assertEquals(
            ReaderPhysicalKeyAction.ScrollBackward,
            readerScrollPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_UP, einkMode = true)
        )
        assertNull(readerScrollPhysicalKeyAction(KeyEvent.KEYCODE_PAGE_DOWN, einkMode = false))
    }

    @Test
    fun mainActivityDispatchesReaderPhysicalKeys() {
        val source = File("src/main/java/fail/tiger/komgarot/MainActivity.kt").readText()

        assertTrue(source.contains("override fun dispatchKeyEvent(event: KeyEvent): Boolean"))
        assertTrue(source.contains("ReaderPhysicalKeyDispatcher.dispatch(event)"))
    }
}
