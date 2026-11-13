package com.jmreader.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {

    private val ttl = RefreshPolicy.ListTtlMs

    @Test
    fun `never loaded is stale`() {
        assertTrue(RefreshPolicy.isStale(0L, 1_000_000L, ttl))
        assertTrue(RefreshPolicy.isStale(-1L, 1_000_000L, ttl))
    }

    @Test
    fun `fresh data is reused`() {
        val now = 10_000_000L
        assertFalse(RefreshPolicy.isStale(now, now, ttl))
        assertFalse(RefreshPolicy.isStale(now - ttl + 1, now, ttl))
    }

    @Test
    fun `ttl boundary counts as stale`() {
        val now = 10_000_000L
        assertTrue(RefreshPolicy.isStale(now - ttl, now, ttl))
        assertTrue(RefreshPolicy.isStale(now - ttl - 5_000, now, ttl))
    }

    @Test
    fun `library ttl is shorter than list ttl`() {
        assertTrue(RefreshPolicy.LibraryTtlMs < RefreshPolicy.ListTtlMs)
    }
}
