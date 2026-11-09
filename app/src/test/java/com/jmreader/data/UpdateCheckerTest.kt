package com.jmreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `patch bump is newer`() {
        assertTrue(UpdateChecker.isNewer("0.2.2", "0.2.1"))
        assertFalse(UpdateChecker.isNewer("0.2.1", "0.2.1"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.1"))
    }

    @Test
    fun `numeric compare, not lexical`() {
        assertTrue(UpdateChecker.isNewer("0.2.10", "0.2.9"))
        assertFalse(UpdateChecker.isNewer("0.2.9", "0.2.10"))
    }

    @Test
    fun `minor and major bumps`() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun `garbage never claims newer`() {
        assertFalse(UpdateChecker.isNewer("", "0.2.1"))
        assertFalse(UpdateChecker.isNewer("abc", "0.2.1"))
    }

    @Test
    fun `double digit patch can cross`() {
        assertTrue(UpdateChecker.isNewer("0.4.10", "0.4.9"))
        assertFalse(UpdateChecker.isNewer("0.4.9", "0.4.10"))
    }

    @Test
    fun `partial numeric segments are ignored`() {
        // "1.x.2" 里的 x 解析不出来，被丢弃 → 退化成 1.2
        assertTrue(UpdateChecker.isNewer("1.2", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.x.2", "1.2"))
    }
}
