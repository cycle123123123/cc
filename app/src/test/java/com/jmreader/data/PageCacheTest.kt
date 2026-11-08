package com.jmreader.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCacheTest {

    private val root = Files.createTempDirectory("page-cache-test").toFile()
    private var clock = 1_000_000L
    private val cache = PageCache(root, maxBytes = 1_000L, now = { clock })

    @Test
    fun `write then read returns the same bytes`() {
        assertTrue(cache.write(cid = 11L, page = 1, bytes = ByteArray(400) { 7 }))
        val bytes = cache.read(11L, 1)
        assertNotNull(bytes)
        assertEquals(400, bytes!!.size)
        assertEquals(7.toByte(), bytes[0])
    }

    @Test
    fun `temporary file from an interrupted write is ignored`() {
        val dir = root.resolve("11").apply { mkdirs() }
        dir.resolve("2.tmp").writeBytes(ByteArray(500))

        assertNull(cache.read(11L, 2))
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `read touches lastModified so hot pages are kept`() {
        cache.write(1L, 1, ByteArray(100))
        val file = cache.file(1L, 1)
        file.setLastModified(10L)

        clock = 999_999L
        assertNotNull(cache.read(1L, 1))
        assertEquals(999_999L, file.lastModified())
    }

    @Test
    fun `prune removes the oldest pages until under the cap`() {
        cache.write(1L, 1, ByteArray(400))
        cache.write(1L, 2, ByteArray(400))
        cache.write(2L, 1, ByteArray(400))
        assertEquals(1_200L, cache.sizeBytes())

        cache.file(1L, 1).setLastModified(1L)
        cache.file(1L, 2).setLastModified(2L)
        cache.file(2L, 1).setLastModified(3L)

        cache.prune(force = true)

        assertFalse(cache.file(1L, 1).exists())
        assertTrue(cache.file(1L, 2).exists())
        assertTrue(cache.file(2L, 1).exists())
        assertEquals(800L, cache.sizeBytes())
        assertTrue(cache.sizeBytes() <= 1_000L)
    }

    @Test
    fun `prune keeps everything when under the cap`() {
        cache.write(1L, 1, ByteArray(100))
        cache.write(1L, 2, ByteArray(100))
        cache.prune(force = true)

        assertTrue(cache.file(1L, 1).exists())
        assertTrue(cache.file(1L, 2).exists())
    }

    @Test
    fun `clear removes every cached page`() {
        cache.write(1L, 1, ByteArray(100))
        cache.write(2L, 1, ByteArray(100))

        cache.clear()

        assertEquals(0L, cache.sizeBytes())
        assertNull(cache.read(1L, 1))
    }
}
