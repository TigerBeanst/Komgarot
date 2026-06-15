package fail.tiger.komgarot.ui.reader

enum class ReaderTapPageAction {
    PreviousPage,
    NextPage,
    ToggleControls
}

fun readerTapPageAction(
    tapX: Float,
    width: Float,
    tapPageTurnEnabled: Boolean,
    einkMode: Boolean,
    readingDirection: String
): ReaderTapPageAction {
    if (!tapPageTurnEnabled && !einkMode) return ReaderTapPageAction.ToggleControls
    if (width <= 0f) return ReaderTapPageAction.ToggleControls

    val centerStart = width * 0.42f
    val centerEnd = width * 0.58f
    if (tapX in centerStart..centerEnd) return ReaderTapPageAction.ToggleControls

    val tapLeft = tapX < centerStart
    val rtl = readingDirection == "RTL"
    return when {
        tapLeft && rtl -> ReaderTapPageAction.NextPage
        tapLeft -> ReaderTapPageAction.PreviousPage
        rtl -> ReaderTapPageAction.PreviousPage
        else -> ReaderTapPageAction.NextPage
    }
}

fun readerPageIndicatorBottomPadding(showControls: Boolean): Int =
    if (showControls) 128 else 56
