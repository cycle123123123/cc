package com.jmreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripPlanTest {

    @Test
    fun `two strips with remainder go to the top of destination`() {
        val plan = ScrambleParams.stripPlan(height = 905, segments = 2)
        assertEquals(2, plan.size)
        // 目标第一条取源图最后一条（452..904，高 453）
        assertEquals(ScrambleParams.Strip(452, 453), plan[0])
        assertEquals(ScrambleParams.Strip(0, 452), plan[1])
    }

    @Test
    fun `plan covers the whole image without gaps`() {
        for (height in listOf(905, 1791, 1000, 1281, 97)) {
            for (segments in listOf(2, 4, 8, 10, 20)) {
                if (height / segments <= 0) continue
                val plan = ScrambleParams.stripPlan(height, segments)
                assertEquals("height=$height segments=$segments", height, plan.sumOf { it.height })
                val covered = plan.sortedBy { it.srcTop }
                var cursor = 0
                for (strip in covered) {
                    assertEquals(cursor, strip.srcTop)
                    cursor += strip.height
                }
                assertEquals(height, cursor)
            }
        }
    }

    @Test
    fun `destination is exact reverse of source order`() {
        val height = 1791
        val segments = 10
        val plan = ScrambleParams.stripPlan(height, segments)
        val strip = height / segments
        assertEquals((segments - 1 downTo 0).map { it * strip }, plan.map { it.srcTop })
    }

    @Test
    fun `single segment is identity`() {
        val plan = ScrambleParams.stripPlan(height = 1000, segments = 1)
        assertEquals(listOf(ScrambleParams.Strip(0, 1000)), plan)
        assertTrue(ScrambleParams.stripPlan(height = 1000, segments = 0).isNotEmpty())
    }
}
