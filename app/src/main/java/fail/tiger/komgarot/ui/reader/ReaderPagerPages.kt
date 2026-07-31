package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.local.LandscapePageSplitOrder
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.PageDto

enum class ReaderBoundaryDirection { PREVIOUS, NEXT }

enum class ReaderPageSegment { FULL, LEFT_HALF, RIGHT_HALF }

sealed interface ReaderPagerPage {
    data class Actual(
        val pageIndex: Int,
        val segment: ReaderPageSegment = ReaderPageSegment.FULL
    ) : ReaderPagerPage
    data class Boundary(val direction: ReaderBoundaryDirection, val target: BookDto?) : ReaderPagerPage
    data class Trigger(val direction: ReaderBoundaryDirection, val target: BookDto) : ReaderPagerPage
}

fun buildReaderPagerPages(
    pageCount: Int,
    previousBook: BookDto?,
    nextBook: BookDto?,
    splitLandscapePages: Boolean = false,
    splitOrder: LandscapePageSplitOrder = LandscapePageSplitOrder.RIGHT_FIRST,
    observedPageLandscape: Map<Int, Boolean> = emptyMap(),
    pageInfo: (Int) -> PageDto? = { null }
): List<ReaderPagerPage> = buildList {
    if (previousBook != null) {
        add(ReaderPagerPage.Trigger(ReaderBoundaryDirection.PREVIOUS, previousBook))
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.PREVIOUS, previousBook))
    } else {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.PREVIOUS, null))
    }
    repeat(pageCount) { pageIndex ->
        val page = pageInfo(pageIndex)
        val landscape = observedPageLandscape[pageIndex]
            ?: (page != null && page.width > page.height)
        if (splitLandscapePages && landscape) {
            val segments = when (splitOrder) {
                LandscapePageSplitOrder.RIGHT_FIRST -> listOf(
                    ReaderPageSegment.RIGHT_HALF,
                    ReaderPageSegment.LEFT_HALF
                )
                LandscapePageSplitOrder.LEFT_FIRST -> listOf(
                    ReaderPageSegment.LEFT_HALF,
                    ReaderPageSegment.RIGHT_HALF
                )
            }
            segments.forEach { segment -> add(ReaderPagerPage.Actual(pageIndex, segment)) }
        } else {
            add(ReaderPagerPage.Actual(pageIndex))
        }
    }
    if (nextBook != null) {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.NEXT, nextBook))
        add(ReaderPagerPage.Trigger(ReaderBoundaryDirection.NEXT, nextBook))
    } else {
        add(ReaderPagerPage.Boundary(ReaderBoundaryDirection.NEXT, null))
    }
}

fun ReaderPageSegment.splitPartNumber(splitOrder: LandscapePageSplitOrder): Int? = when (this) {
    ReaderPageSegment.FULL -> null
    ReaderPageSegment.LEFT_HALF -> if (splitOrder == LandscapePageSplitOrder.LEFT_FIRST) 1 else 2
    ReaderPageSegment.RIGHT_HALF -> if (splitOrder == LandscapePageSplitOrder.RIGHT_FIRST) 1 else 2
}

fun ReaderPagerPage.readerPagerStableKey(splitOrder: LandscapePageSplitOrder): String = when (this) {
    is ReaderPagerPage.Actual -> {
        val part = segment.splitPartNumber(splitOrder)
        "page:$pageIndex:${if (part == 2) "secondary" else "primary"}"
    }
    is ReaderPagerPage.Boundary -> "boundary:${direction.name.lowercase()}"
    is ReaderPagerPage.Trigger -> "trigger:${direction.name.lowercase()}:${target.id}"
}

fun readerPagerNeedsProgressSync(
    currentPagerPage: ReaderPagerPage?,
    actualPageIndex: Int
): Boolean =
    currentPagerPage !is ReaderPagerPage.Actual ||
        currentPagerPage.pageIndex != actualPageIndex

fun List<ReaderPagerPage>.pagerIndexForActualPage(pageIndex: Int): Int =
    indexOfFirst { it is ReaderPagerPage.Actual && it.pageIndex == pageIndex }
        .takeIf { it >= 0 }
        ?: indexOfFirst { it is ReaderPagerPage.Actual }.coerceAtLeast(0)
