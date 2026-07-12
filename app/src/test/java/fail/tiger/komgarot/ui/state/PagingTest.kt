package fail.tiger.komgarot.ui.state

import fail.tiger.komgarot.data.remote.dto.PagedDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PagingTest {
    @Test
    fun addAllUniqueByKeepsExistingOrderAndSkipsDuplicateKeys() {
        val items = mutableListOf(Item("a"), Item("b"))

        items.addAllUniqueBy(listOf(Item("b"), Item("c"), Item("c"))) { it.id }

        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun pagedListStateLoadsUniqueItemsAndUpdatesHasMore() = runBlocking {
        val state = PagedListState<Item, String>({ it.id }, "failed")

        state.loadMore { page ->
            assertEquals(0, page)
            PagedDto(
                content = listOf(Item("a"), Item("b")),
                totalPages = 2,
                totalElements = 3,
                number = 0,
                size = 2
            )
        }
        state.loadMore { page ->
            assertEquals(1, page)
            PagedDto(
                content = listOf(Item("b"), Item("c")),
                totalPages = 2,
                totalElements = 3,
                number = 1,
                size = 2
            )
        }

        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun pagedListStateKeepsItemsWhenLoadFails() = runBlocking {
        val state = PagedListState<Item, String>({ it.id }, "failed")
        state.loadMore {
            PagedDto(listOf(Item("a")), totalPages = 2, totalElements = 2, number = 0, size = 1)
        }

        state.loadMore { error("") }

        assertEquals(listOf("a"), state.items.map { it.id })
        assertEquals("failed", state.error)
    }

    @Test
    fun pagedListStateRefreshReplacesItemsOnlyAfterSuccess() = runBlocking {
        val state = PagedListState<Item, String>({ it.id }, "failed")
        state.loadMore {
            PagedDto(listOf(Item("old")), totalPages = 1, totalElements = 1, number = 0, size = 1)
        }

        state.refresh {
            assertEquals(listOf("old"), state.items.map { it.id })
            PagedDto(listOf(Item("new")), totalPages = 2, totalElements = 2, number = 0, size = 1)
        }

        assertEquals(listOf("new"), state.items.map { it.id })
        assertTrue(state.hasMore)
    }

    @Test
    fun pagedListStateRefreshFailureKeepsExistingItems() = runBlocking {
        val state = PagedListState<Item, String>({ it.id }, "failed")
        state.loadMore {
            PagedDto(listOf(Item("old")), totalPages = 1, totalElements = 1, number = 0, size = 1)
        }

        state.refresh { error("") }

        assertEquals(listOf("old"), state.items.map { it.id })
        assertEquals("failed", state.error)
    }

    @Test
    fun pagedListStateMarksEmptyFirstPageAsLoaded() = runBlocking {
        val state = PagedListState<Item, String>({ it.id }, "failed")
        assertFalse(state.hasLoadedOnce)

        state.loadMore {
            PagedDto(emptyList(), totalPages = 0, totalElements = 0, number = 0, size = 0)
        }

        assertTrue(state.hasLoadedOnce)
        assertTrue(state.items.isEmpty())
    }

    private data class Item(val id: String)
}
