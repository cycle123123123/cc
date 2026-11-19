package com.jmreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteWatchTest {

    @Test
    fun `tagged serial is watched`() {
        assertTrue(FavoriteWatch.isSerial(listOf("韓漫", "連載"), chapterCount = 1))
    }

    @Test
    fun `multi chapter counts as serial even without tag`() {
        assertTrue(FavoriteWatch.isSerial(emptyList(), chapterCount = 2))
    }

    @Test
    fun `single chapter without tag is ignored`() {
        assertFalse(FavoriteWatch.isSerial(listOf("韓漫", "單篇"), chapterCount = 1))
        assertFalse(FavoriteWatch.isSerial(emptyList(), chapterCount = 0))
    }
}
