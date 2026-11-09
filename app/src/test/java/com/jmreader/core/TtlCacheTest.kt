package com.jmreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {

    private var now = 0L

    private fun cache(maxSize: Int = 2, ttlMs: Long = 100L) =
        TtlCache<Int, String>(maxSize, ttlMs) { now }

    @Test
    fun `hits before ttl`() {
        val c = cache()
        c.put(1, "a")
        now = 99L
        assertEquals("a", c.get(1))
    }

    @Test
    fun `misses after ttl and drops entry`() {
        val c = cache()
        c.put(1, "a")
        now = 100L
        assertNull(c.get(1))
        assertEquals(0, c.size())
    }

    @Test
    fun `evicts least recently used`() {
        val c = cache(maxSize = 2)
        c.put(1, "a")
        c.put(2, "b")
        c.put(3, "c")

        assertEquals(2, c.size())
        assertNull(c.get(1))
        assertEquals("b", c.get(2))
        assertEquals("c", c.get(3))
    }

    @Test
    fun `reads refresh recency`() {
        val c = cache(maxSize = 2)
        c.put(1, "a")
        c.put(2, "b")
        // 读 1 之后，2 变成最久未使用
        assertEquals("a", c.get(1))
        c.put(3, "c")

        assertNull(c.get(2))
        assertEquals("a", c.get(1))
    }

    @Test
    fun `clear empties cache`() {
        val c = cache()
        c.put(1, "a")
        c.clear()
        assertEquals(0, c.size())
        assertNull(c.get(1))
    }
}
