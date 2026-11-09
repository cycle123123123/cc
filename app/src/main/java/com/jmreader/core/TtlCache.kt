package com.jmreader.core

/**
 * 带 LRU 淘汰和 TTL 的小缓存。
 *
 * 之前 `album` / `comicRead` 各自维护「`LruCache` + 时间戳 map」两份结构，
 * 这里合并成一个，容量与 TTL 由调用方传。
 */
class TtlCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val ttlMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class Entry<V>(val value: V, val at: Long)

    /** accessOrder = true，读取即刷新 LRU 顺序。 */
    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxSize
    }

    /** 命中且未过期返回值，过期则移除并返回 null。 */
    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.at >= ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock())
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
