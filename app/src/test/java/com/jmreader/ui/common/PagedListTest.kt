package com.jmreader.ui.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedListTest {

    private fun paged(
        pages: Map<Int, List<String>>,
        totals: Map<Int, Int> = emptyMap(),
        failOn: Int? = null,
    ) = PagedList<String>(
        keyOf = { it },
        fetch = { page ->
            if (page == failOn) throw IllegalStateException("boom")
            PageResult(pages[page].orEmpty(), totals[page] ?: Int.MAX_VALUE)
        },
    )

    @Test
    fun `refresh loads first page`() = runTest {
        val list = paged(mapOf(1 to listOf("a", "b")), mapOf(1 to 5))
        list.refresh()

        assertEquals(listOf("a", "b"), list.items)
        assertEquals(5, list.total)
        assertTrue(list.hasMore)
        assertTrue(list.loaded)
        assertNull(list.error)
    }

    @Test
    fun `loadMore appends without duplicates`() = runTest {
        val list = paged(
            mapOf(1 to listOf("a", "b"), 2 to listOf("b", "c")),
            mapOf(1 to 4, 2 to 4),
        )
        list.refresh()
        list.loadMore()

        assertEquals(listOf("a", "b", "c"), list.items)
        assertTrue(list.hasMore)
    }

    @Test
    fun `duplicate-only page stops pagination`() = runTest {
        val list = paged(
            mapOf(1 to listOf("a", "b"), 2 to listOf("a", "b")),
            mapOf(1 to 100, 2 to 100),
        )
        list.refresh()
        list.loadMore()

        assertEquals(listOf("a", "b"), list.items)
        assertFalse(list.hasMore)
    }

    @Test
    fun `reaching total stops pagination`() = runTest {
        val list = paged(mapOf(1 to listOf("a", "b")), mapOf(1 to 2))
        list.refresh()
        assertFalse(list.hasMore)
    }

    @Test
    fun `error keeps existing items`() = runTest {
        val list = paged(mapOf(1 to listOf("a")), mapOf(1 to 10), failOn = 2)
        list.refresh()
        list.loadMore()

        assertEquals(listOf("a"), list.items)
        assertTrue(list.error != null)
        assertFalse(list.loading)
    }

    @Test
    fun `first page error reports and stays empty`() = runTest {
        val list = paged(emptyMap(), failOn = 1)
        list.refresh()

        assertTrue(list.items.isEmpty())
        assertTrue(list.error != null)
        assertFalse(list.loaded)
    }

    @Test
    fun `reset clears state`() = runTest {
        val list = paged(mapOf(1 to listOf("a")), mapOf(1 to 3))
        list.refresh()
        list.reset()

        assertTrue(list.items.isEmpty())
        assertEquals(0, list.total)
        assertTrue(list.hasMore)
        assertFalse(list.loaded)
    }

    @Test
    fun `refresh can interrupt while loading`() = runTest {
        // refresh 不检查 loading，第二次调用直接覆盖结果
        val list = paged(mapOf(1 to listOf("a")), mapOf(1 to 1))
        list.refresh()
        list.refresh()

        assertEquals(listOf("a"), list.items)
        assertFalse(list.loading)
    }
}
