package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.remote.dto.BookDto

enum class ReaderBoundaryDirection { PREVIOUS, NEXT }

sealed interface ReaderPagerPage {
    data class Actual(val pageIndex: Int) : ReaderPagerPage
    data class Boundary(val direction: ReaderBoundaryDirection, val target: BookDto?) : ReaderPagerPage
    data class Trigger(val direction: ReaderBoundaryDirection, val target: BookDto) : ReaderPagerPage
}

fun buildReaderPagerPages(
    pageCount: Int,
    previousBook: BookDto?,
    nextBook: BookDto?
): List<ReaderPagerPage> = buildList {
    if (previousBook != null) {
        add(ReaderPagerPage.Trigger(ReaderBoundaryDirection.PREVIOUS, previousBook))
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.PREVIOUS, previousBook))
    } else {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.PREVIOUS, null))
    }
    repeat(pageCount) { pageIndex -> add(ReaderPagerPage.Actual(pageIndex)) }
    if (nextBook != null) {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.NEXT, nextBook))
        add(ReaderPagerPage.Trigger(ReaderBoundaryDirection.NEXT, nextBook))
    } else {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.NEXT, null))
    }
}

fun List<ReaderPagerPage>.pagerIndexForActualPage(pageIndex: Int): Int =
    indexOfFirst { it is ReaderPagerPage.Actual && it.pageIndex == pageIndex }
        .takeIf { it >= 0 }
        ?: indexOfFirst { it is ReaderPagerPage.Actual }.coerceAtLeast(0)
