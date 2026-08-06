package fail.tiger.komgarot.ui.series

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesScrollStateStructureTest {
    private val screenSource = File("src/main/java/fail/tiger/komgarot/ui/series/SeriesScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/series/SeriesViewModel.kt").readText()

    @Test
    fun seriesScrollAnchorSurvivesDataAndNavigationRestoration() {
        assertTrue(viewModelSource.contains("private val savedStateHandle: SavedStateHandle"))
        assertTrue(viewModelSource.contains("scrollRestorationPending && series.size <= savedScrollIndex && hasMore"))
        assertTrue(screenSource.contains("initialFirstVisibleItemIndex = vm.savedScrollIndex"))
        assertTrue(screenSource.contains("listState.scrollToItem(position.index, position.offset)"))
        assertTrue(screenSource.contains("vm.updateScrollPosition(index, offset)"))
    }

    @Test
    fun seriesResumeRestartsInterruptedInitialLoad() {
        assertTrue(screenSource.contains("vm.resumeAfterBackground()"))
        assertTrue(viewModelSource.contains("series.isEmpty() && !hasLoadedOnce"))
        assertTrue(viewModelSource.contains("resetPaging()\n            loadMore()"))
        assertTrue(viewModelSource.contains("refreshVisibleOneShotTitles()"))
    }
}
