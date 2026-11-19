package com.jmreader.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchIdsTest {

    @Test
    fun `plain digits are a car id`() {
        assertEquals(1490953L, parseJmId("1490953"))
        assertEquals(1234L, parseJmId(" 1234 "))
    }

    @Test
    fun `jm prefix is accepted case insensitive`() {
        assertEquals(123456L, parseJmId("JM123456"))
        assertEquals(123456L, parseJmId("jm123456"))
        assertEquals(123456L, parseJmId("Jm 123456"))
    }

    @Test
    fun `out of range or mixed text is not an id`() {
        assertNull(parseJmId("123"))          // 只有 3 位
        assertNull(parseJmId("12345678"))     // 8 位
        assertNull(parseJmId("abc123456"))
        assertNull(parseJmId(""))
        assertNull(parseJmId("JM"))
    }

    @Test
    fun `clipboard text extracts prefixed id`() {
        assertEquals(1490953L, extractJmId("https://18comic.vip/album/1490953/"))
        assertEquals(123456L, extractJmId("分享 JM123456 給你"))
        assertEquals(654321L, extractJmId("654321"))
    }

    @Test
    fun `clipboard text without id returns null`() {
        assertNull(extractJmId("hello world"))
        assertNull(extractJmId("123"))
        assertNull(extractJmId(""))
    }
}
