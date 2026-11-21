package com.jmreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderGeometryTest {

    @Test
    fun `clampPage 夹紧到 0 到 total-1`() {
        assertEquals(0, ReaderGeometry.clampPage(-1, 5))
        assertEquals(0, ReaderGeometry.clampPage(0, 5))
        assertEquals(4, ReaderGeometry.clampPage(4, 5))
        assertEquals(4, ReaderGeometry.clampPage(9, 5))
    }

    @Test
    fun `没有页时 clampPage 返回 0`() {
        assertEquals(0, ReaderGeometry.clampPage(3, 0))
        assertEquals(0, ReaderGeometry.clampPage(3, -2))
    }

    @Test
    fun `stepPage 在边界处不越界`() {
        assertEquals(1, ReaderGeometry.stepPage(0, 1, 5))
        assertEquals(0, ReaderGeometry.stepPage(0, -1, 5))
        assertEquals(4, ReaderGeometry.stepPage(4, 1, 5))
        assertEquals(4, ReaderGeometry.stepPage(2, 5, 5))
        assertEquals(0, ReaderGeometry.stepPage(2, -5, 5))
    }

    @Test
    fun `没有页时 stepPage 返回 0`() {
        assertEquals(0, ReaderGeometry.stepPage(3, 1, 0))
    }

    @Test
    fun `clampScale 限制在 1x 到 3x`() {
        assertEquals(1f, ReaderGeometry.clampScale(0.5f), 0f)
        assertEquals(1f, ReaderGeometry.clampScale(1f), 0f)
        assertEquals(2.5f, ReaderGeometry.clampScale(2.5f), 0f)
        assertEquals(3f, ReaderGeometry.clampScale(5f), 0f)
    }

    @Test
    fun `clampPanX 按缩放倍数限制平移`() {
        // 视口 100、放大 2 倍 → 每侧最多 50
        assertEquals(50f, ReaderGeometry.clampPanX(999f, 2f, 100), 0.001f)
        assertEquals(-50f, ReaderGeometry.clampPanX(-999f, 2f, 100), 0.001f)
        assertEquals(20f, ReaderGeometry.clampPanX(20f, 2f, 100), 0.001f)
    }

    @Test
    fun `未放大或视口为 0 时不允许平移`() {
        assertEquals(0f, ReaderGeometry.clampPanX(30f, 1f, 100), 0f)
        assertEquals(0f, ReaderGeometry.clampPanX(30f, 2f, 0), 0f)
    }

    @Test
    fun `panForScale 以双击点为中心`() {
        // 双击正中 → 不偏移
        assertEquals(0f, ReaderGeometry.panForScale(2f, 50f, 100), 0.001f)
        // 双击左边缘 → 内容右移 50，使左边缘保持不动
        assertEquals(50f, ReaderGeometry.panForScale(2f, 0f, 100), 0.001f)
        assertEquals(-50f, ReaderGeometry.panForScale(2f, 100f, 100), 0.001f)
    }

    @Test
    fun `缩放到 1x 时偏移归零`() {
        assertEquals(0f, ReaderGeometry.panForScale(1f, 0f, 100), 0f)
        assertEquals(0f, ReaderGeometry.panForScale(1.01f, 0f, 100), 0f)
        assertEquals(0f, ReaderGeometry.panForScale(2f, 0f, 0), 0f)
    }
}
