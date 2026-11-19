package com.jmreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatsTest {

    @Test
    fun `streak counts today backwards`() {
        val today = 20_000
        assertEquals(3, ReadingStats.streak(today, setOf(today, today - 1, today - 2)))
    }

    @Test
    fun `streak falls back to yesterday when today is idle`() {
        val today = 20_000
        assertEquals(2, ReadingStats.streak(today, setOf(today - 1, today - 2)))
    }

    @Test
    fun `gap breaks the streak`() {
        val today = 20_000
        assertEquals(1, ReadingStats.streak(today, setOf(today, today - 2, today - 3)))
    }

    @Test
    fun `no activity means zero`() {
        assertEquals(0, ReadingStats.streak(20_000, emptySet()))
        assertEquals(0, ReadingStats.streak(20_000, setOf(19_000)))
    }

    @Test
    fun `todayKey is stable within the same millisecond`() {
        val now = 1_763_500_000_000L
        assertEquals(ReadingStats.todayKey(now), ReadingStats.todayKey(now))
    }
}
