package fail.tiger.komgarot.ui.series

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesViewModelTest {
    @Test
    fun sameRouteWithoutInitialSearchKeepsManualAuthorSearchAfterBack() {
        assertFalse(
            shouldApplySeriesInitialSearch(
                libraryChanged = false,
                initialSearch = null,
                currentSearch = "author:Author Name"
            )
        )
    }

    @Test
    fun libraryChangeClearsManualSearchWhenRouteHasNoInitialSearch() {
        assertTrue(
            shouldApplySeriesInitialSearch(
                libraryChanged = true,
                initialSearch = null,
                currentSearch = "author:Author Name"
            )
        )
    }

    @Test
    fun explicitRouteSearchUpdatesExistingSearch() {
        assertTrue(
            shouldApplySeriesInitialSearch(
                libraryChanged = false,
                initialSearch = "author:Other Name",
                currentSearch = "author:Author Name"
            )
        )
    }
}
