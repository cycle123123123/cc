package com.jmreader.core

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writeBytes 落盘后没有残留 tmp`() {
        val target = File(tmp.root, "a.bin")
        AtomicFile.writeBytes(target, byteArrayOf(1, 2, 3))

        assertTrue(target.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(File(tmp.root, "a.bin.tmp").exists())
    }

    @Test
    fun `覆盖已有文件时内容完全替换`() {
        val target = File(tmp.root, "a.bin").apply { writeBytes(ByteArray(100) { 7 }) }
        AtomicFile.writeBytes(target, byteArrayOf(9))

        assertArrayEquals(byteArrayOf(9), target.readBytes())
        assertFalse(File(tmp.root, "a.bin.tmp").exists())
    }

    @Test
    fun `写入失败时保留旧的完整文件`() {
        val target = File(tmp.root, "meta.json").apply { writeText("""{"ok":true}""") }
        val boom = IOException("disk full")

        runCatching { AtomicFile.write(target) { throw boom } }

        assertEquals("""{"ok":true}""", target.readText())
        assertFalse(File(tmp.root, "meta.json.tmp").exists())
    }

    @Test
    fun `自动创建父目录`() {
        val target = File(tmp.root, "x/y/z.bin")
        AtomicFile.writeBytes(target, byteArrayOf(4))

        assertTrue(target.exists())
    }

    @Test
    fun `cleanupTemp 只删 tmp`() {
        val dir = File(tmp.root, "d").apply { mkdirs() }
        File(dir, "1.bin").writeBytes(byteArrayOf(1))
        File(dir, "1.bin.tmp").writeBytes(byteArrayOf(2))
        File(dir, "meta.json.tmp").writeText("{}")

        AtomicFile.cleanupTemp(dir)

        assertTrue(File(dir, "1.bin").exists())
        assertFalse(File(dir, "1.bin.tmp").exists())
        assertFalse(File(dir, "meta.json.tmp").exists())
    }
}
