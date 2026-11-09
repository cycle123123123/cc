package com.jmreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.jmreader.data.local.AlbumMeta
import com.jmreader.data.local.metaBadge
import com.jmreader.data.local.metaTopBadge

/** 卡片角标的纯文本拼装规则，不依赖 Android 运行时。 */
class MetaBadgeTest {

    @Test
    fun `null meta renders nothing`() {
        assertNull(metaBadge(null))
        assertNull(metaTopBadge(null))
    }

    @Test
    fun `empty meta renders nothing`() {
        val meta = AlbumMeta()
        assertNull(metaBadge(meta))
        assertNull(metaTopBadge(meta))
    }

    @Test
    fun `pages only goes to top badge`() {
        val meta = AlbumMeta(pages = 296)
        assertEquals("296P", metaTopBadge(meta))
        assertNull(metaBadge(meta))
    }

    @Test
    fun `japanese only goes to top badge`() {
        val meta = AlbumMeta(japanese = true)
        assertEquals("日文", metaTopBadge(meta))
        assertNull(metaBadge(meta))
    }

    @Test
    fun `japanese and pages are joined`() {
        val meta = AlbumMeta(pages = 296, japanese = true)
        assertEquals("日文 · 296P", metaTopBadge(meta))
    }

    @Test
    fun `views only`() {
        assertEquals("👁 6128", metaBadge(AlbumMeta(views = 6128)))
    }

    @Test
    fun `views and likes are joined`() {
        assertEquals("👁 6128 · ♥ 329", metaBadge(AlbumMeta(views = 6128, likes = 329)))
    }

    @Test
    fun `top badge is not repeated in bottom badge`() {
        val meta = AlbumMeta(views = 61_000, likes = 329, pages = 296, japanese = true)
        assertEquals("👁 6.1 萬 · ♥ 329", metaBadge(meta))
        assertEquals("日文 · 296P", metaTopBadge(meta))
    }
}
