package com.jmreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrambleParamsTest {

    @Test
    fun `segment count matches live album pages`() {
        // 数据来自 aid=1489023 的真实章节，见 docs/API.md §6.4
        assertEquals(2, ScrambleParams.segmentCount(1489023L, "00001"))
        assertEquals(8, ScrambleParams.segmentCount(1489023L, "00002"))
        assertEquals(10, ScrambleParams.segmentCount(1489023L, "00003"))
        assertEquals(12, ScrambleParams.segmentCount(1489023L, "00004"))
        assertEquals(10, ScrambleParams.segmentCount(1489023L, "00005"))
    }

    @Test
    fun `old album always uses ten segments`() {
        // aid < 268850 时末位字符是 a-f/0-9，charCode 不会落在 0..9，取默认 10
        assertEquals(10, ScrambleParams.segmentCount(1000L, "00001"))
        assertEquals(10, ScrambleParams.segmentCount(220980L, "00001"))
        assertEquals(10, ScrambleParams.segmentCount(268849L, "00001"))
    }

    @Test
    fun `segment count on aid boundaries`() {
        assertEquals(6, ScrambleParams.segmentCount(268850L, "00001"))
        assertEquals(16, ScrambleParams.segmentCount(300000L, "00001"))
        assertEquals(18, ScrambleParams.segmentCount(421925L, "00001"))
        assertEquals(14, ScrambleParams.segmentCount(421926L, "00001"))
        assertEquals(12, ScrambleParams.segmentCount(500000L, "00001"))
    }

    @Test
    fun `segment count is always even and within table`() {
        for (aid in listOf(1L, 999L, 268850L, 350000L, 421925L, 421926L, 999999L)) {
            for (key in listOf("00001", "00002", "01234", "abcdef")) {
                val count = ScrambleParams.segmentCount(aid, key)
                assertTrue("aid=$aid key=$key count=$count", count in 2..20 && count % 2 == 0)
            }
        }
    }
}
