package com.jmreader.ui.detail

import com.jmreader.data.local.LastRead
import com.jmreader.data.model.ChapterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeResolverTest {

    private val albumId = 100L
    private val chapters = listOf(
        ChapterEntry(1L, "第 1 話"),
        ChapterEntry(2L, "第 2 話"),
    )

    @Test
    fun `history 命中时优先于 last`() {
        val history = listOf(LastRead(albumId, 1L, "第 1 話", page = 3))
        val last = LastRead(albumId, 2L, "第 2 話", page = 5)

        val resume = ResumeResolver.resolve(history, last, chapters, albumId)

        assertEquals(1L, resume?.cid)
    }

    @Test
    fun `history 为空时回退到 last`() {
        val last = LastRead(albumId, 2L, "第 2 話", page = 5)

        val resume = ResumeResolver.resolve(emptyList(), last, chapters, albumId)

        assertEquals(2L, resume?.cid)
    }

    @Test
    fun `last 属于别的作品时不使用`() {
        val last = LastRead(999L, 2L, "別人的第 2 話", page = 5)

        assertNull(ResumeResolver.resolve(emptyList(), last, chapters, albumId))
    }

    @Test
    fun `记录章节已不在目录时降级为 null`() {
        val history = listOf(LastRead(albumId, 404L, "已下架", page = 1))

        assertNull(ResumeResolver.resolve(history, null, chapters, albumId))
    }

    @Test
    fun `目录为空时也不启用续读`() {
        val history = listOf(LastRead(albumId, 2L, "第 2 話", page = 5))

        assertNull(ResumeResolver.resolve(history, null, emptyList(), albumId))
    }

    @Test
    fun `indexOf 返回目录中的位置`() {
        assertEquals(1, ResumeResolver.indexOf(chapters, LastRead(albumId, 2L, "第 2 話", 0)))
        assertEquals(-1, ResumeResolver.indexOf(chapters, LastRead(albumId, 404L, "已下架", 0)))
        assertEquals(-1, ResumeResolver.indexOf(chapters, null))
    }
}
