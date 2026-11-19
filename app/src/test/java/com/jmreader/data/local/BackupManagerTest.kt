package com.jmreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private fun sample() = BackupData(
        version = 1,
        exportedAt = 1_700_000_000_000L,
        history = listOf(
            LastRead(albumId = 101, cid = 201, title = "測試作品", page = 5, updatedAt = 1_700_000_000_000L),
        ),
        blocks = BlockRules(
            keywords = listOf("NTR"),
            authors = listOf("某人"),
            albums = listOf("999"),
        ),
        stats = ReadingStatsSnapshot(
            totalSeconds = 3600,
            totalPages = 120,
            albumIds = setOf(101L),
            days = listOf(DayStat(day = 20_000, seconds = 600, pages = 30)),
        ),
        searchHistory = listOf("海賊王", "JM123456"),
        progress = mapOf("201" to 7),
        bookmarks = mapOf("201" to 3),
    )

    @Test
    fun `json round trip keeps every field`() {
        val encoded = BackupManager.encode(sample())
        val decoded = BackupManager.decode(encoded)
        assertEquals(1, decoded.version)
        assertEquals(1, decoded.history.size)
        assertEquals(101L, decoded.history.first().albumId)
        assertEquals(listOf("NTR"), decoded.blocks.keywords)
        assertEquals(listOf("某人"), decoded.blocks.authors)
        assertEquals(3600, decoded.stats.totalSeconds)
        assertEquals(120, decoded.stats.totalPages)
        assertEquals(setOf(101L), decoded.stats.albumIds)
        assertEquals(30, decoded.stats.days.first().pages)
        assertEquals(listOf("海賊王", "JM123456"), decoded.searchHistory)
        assertEquals(7, decoded.progress["201"])
        assertEquals(3, decoded.bookmarks["201"])
    }

    @Test
    fun `unknown json keys are ignored for forward compatibility`() {
        val raw = """
            {
              "version": 1,
              "exportedAt": 1,
              "searchHistory": ["a"],
              "futureField": {"x": 1}
            }
        """.trimIndent()
        val decoded = BackupManager.decode(raw)
        assertEquals(listOf("a"), decoded.searchHistory)
        assertTrue(decoded.history.isEmpty())
    }

    @Test
    fun `backup never contains account credentials`() {
        val encoded = BackupManager.encode(sample())
        // 注意不能拿 "auth" 當關鍵字：屏蔽規則裡的 authors 是正常字段
        listOf("jwttoken", "password", "cookie", "AVS", "bearer").forEach { secret ->
            assertTrue("encoded backup should not contain $secret", !encoded.contains(secret, ignoreCase = true))
        }
    }
}
