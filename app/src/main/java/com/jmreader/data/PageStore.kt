package com.jmreader.data

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 阅读器页缓存：内存 LRU + 同一 URL 只下载一次 + 预取。
 * 48MB 大约能放 6~8 页 1080 宽的位图。
 */
class PageStore(
    private val repo: JmRepository,
    private val cid: Long,
    private val aid: Long,
    private val scrambleId: Long,
    private val targetWidth: Int,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inflight = mutableMapOf<String, Deferred<Bitmap?>>()

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String, page: Int): Bitmap? {
        cache.get(url)?.let { return it }

        val job = mutex.withLock {
            cache.get(url)?.let { return it }
            inflight[url] ?: scope.async {
                runCatching {
                    repo.loadPage(
                        cid = cid,
                        page = page,
                        url = url,
                        aid = aid,
                        scrambleId = scrambleId,
                        targetWidth = targetWidth,
                    )
                }.getOrNull()
            }.also { inflight[url] = it }
        }

        val bitmap = job.await()
        mutex.withLock { inflight.remove(url) }
        if (bitmap != null) cache.put(url, bitmap)
        return bitmap
    }

    fun prefetch(pages: List<Pair<String, Int>>) {
        for ((url, page) in pages) {
            if (cache.get(url) != null) continue
            scope.launch { runCatching { load(url, page) } }
        }
    }

    fun close() {
        scope.cancel()
        cache.evictAll()
    }

    private companion object {
        const val CACHE_BYTES = 48 * 1024 * 1024
    }
}
