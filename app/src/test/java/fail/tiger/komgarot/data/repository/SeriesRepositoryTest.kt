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
    fun authorSearchWithRoleUsesStringEqualityCondition() {
        val search = buildSeriesSearch(libraryId = null, search = "author:Sean Murphy,writer")

        assertNull(search.fullTextSearch)
        val condition = search.condition.orEmpty()
        assertEquals("SERIES", condition["operator"])
        val author = condition["author"].asMap()
        assertEquals("IS", author["operator"])
        assertEquals("Sean Murphy,writer", author["value"])
    }

    @Test
    fun regularSearchUsesFullTextSearch() {
        val search = buildSeriesSearch(libraryId = null, search = "Batman")

        assertNull(search.condition)
        assertEquals("Batman", search.fullTextSearch)
    }

    @Test
    fun libraryAndRoleAuthorSearchAreCombinedWithAllOf() {
        val search = buildSeriesSearch(libraryId = "library-1", search = "author:Sean Murphy,writer")

        assertNull(search.fullTextSearch)
        val condition = search.condition.orEmpty()
        assertEquals("SERIES", condition["operator"])
        val allOf = condition["allOf"].asList()
        assertEquals(2, allOf.size)

        assertTrue(allOf.any { item ->
            val library = item["libraryId"] as? Map<*, *>
            library?.get("operator") == "IS" && library["value"] == "library-1"
        })
        assertTrue(allOf.any { item ->
            val author = item["author"] as? Map<*, *>
            author?.get("operator") == "IS" && author["value"] == "Sean Murphy,writer"
        })
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

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asList(): List<Map<String, Any?>> =
        this as List<Map<String, Any?>>
}
