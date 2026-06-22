package fail.tiger.komgarot.ui.reader

import android.view.KeyEvent

enum class ReaderPhysicalKeyAction {
    PreviousPage,
    NextPage,
    ScrollBackward,
    ScrollForward
}

object ReaderPhysicalKeyDispatcher {
    private var handler: ((KeyEvent) -> Boolean)? = null

    fun setHandler(nextHandler: ((KeyEvent) -> Boolean)?) {
        handler = nextHandler
    }

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) == true
}

fun readerPhysicalKeyAction(
    keyCode: Int,
    einkMode: Boolean,
    readingDirection: String
): ReaderPhysicalKeyAction? {
    if (!einkMode) return null
    val rtl = readingDirection == "RTL"
    return when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_DPAD_UP -> ReaderPhysicalKeyAction.PreviousPage
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER -> ReaderPhysicalKeyAction.NextPage
        KeyEvent.KEYCODE_DPAD_LEFT -> if (rtl) ReaderPhysicalKeyAction.NextPage else ReaderPhysicalKeyAction.PreviousPage
        KeyEvent.KEYCODE_DPAD_RIGHT -> if (rtl) ReaderPhysicalKeyAction.PreviousPage else ReaderPhysicalKeyAction.NextPage
        else -> null
    }
}

fun readerScrollPhysicalKeyAction(
    keyCode: Int,
    einkMode: Boolean
): ReaderPhysicalKeyAction? {
    if (!einkMode) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_LEFT -> ReaderPhysicalKeyAction.ScrollBackward
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER -> ReaderPhysicalKeyAction.ScrollForward
        else -> null
    }
}
