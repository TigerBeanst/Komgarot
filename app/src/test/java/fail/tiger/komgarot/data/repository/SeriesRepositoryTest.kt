package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesRepositoryTest {
    @Test
    fun authorSearchWithoutRoleUsesFullTextAuthorScope() {
        val search = buildSeriesSearch(libraryId = null, search = "author:Sean Murphy")

        assertNull(search.condition)
        assertEquals("author:(Sean Murphy)", search.fullTextSearch)
    }

    @Test
    fun authorSearchWithRoleUsesFullTextAuthorScope() {
        val search = buildSeriesSearch(libraryId = null, search = "author:Sean Murphy,writer")

        assertNull(search.condition)
        assertEquals("author:(Sean Murphy)", search.fullTextSearch)
    }

    @Test
    fun authorSearchEscapesLuceneSpecialCharacters() {
        val search = buildSeriesSearch(libraryId = null, search = "author:Artist!")

        assertEquals("author:(Artist\\!)", search.fullTextSearch)
    }

    @Test
    fun regularSearchUsesFullTextSearch() {
        val search = buildSeriesSearch(libraryId = null, search = "Batman")

        assertNull(search.condition)
        assertEquals("Batman", search.fullTextSearch)
    }

    @Test
    fun tagFilterCreatesSeriesTagCondition() {
        val search = buildSeriesSearch(libraryId = null, filters = SeriesFilters(tag = "sci fi"))

        val condition = search.condition.orEmpty()
        assertEquals("SERIES", condition["operator"])
        val tag = condition["tag"].asMap()
        assertEquals("IS", tag["operator"])
        assertEquals("sci fi", tag["value"])
    }

    @Test
    fun libraryAndRoleAuthorSearchKeepLibraryConditionAndFullTextAuthorScope() {
        val search = buildSeriesSearch(libraryId = "library-1", search = "author:Sean Murphy,writer")

        assertEquals("author:(Sean Murphy)", search.fullTextSearch)
        val condition = search.condition.orEmpty()
        assertEquals("SERIES", condition["operator"])
        val library = condition["libraryId"].asMap()
        assertEquals("IS", library["operator"])
        assertEquals("library-1", library["value"])
    }

    @Test
    fun emptyAuthorSearchIsIgnored() {
        val search = buildSeriesSearch(libraryId = null, search = "author: ")

        assertNull(search.condition)
        assertNull(search.fullTextSearch)
    }

    @Test
    fun authorSearchWithoutNameIsIgnored() {
        val search = buildSeriesSearch(libraryId = null, search = "author:,writer")

        assertNull(search.condition)
        assertNull(search.fullTextSearch)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> =
        this as Map<String, Any?>

}
