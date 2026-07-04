package fail.tiger.komgarot.ui.series

import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import org.junit.Assert.assertEquals
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

    @Test
    fun oneShotSeriesUsesBookTitleOverrideInSeriesList() {
        val series = SeriesDto(
            id = "series-1",
            name = "Old Folder Name",
            booksCount = 1,
            oneshot = true,
            metadata = SeriesMetadataDto(title = "Old Series Title")
        )

        assertEquals(
            "Updated Book Title",
            seriesDisplayTitle(series, mapOf("series-1" to "Updated Book Title"))
        )
    }

    @Test
    fun regularSeriesKeepsSeriesMetadataTitleInSeriesList() {
        val series = SeriesDto(
            id = "series-1",
            name = "Folder Name",
            booksCount = 3,
            oneshot = false,
            metadata = SeriesMetadataDto(title = "Series Title")
        )

        assertEquals(
            "Series Title",
            seriesDisplayTitle(series, mapOf("series-1" to "Book Title"))
        )
    }
}
