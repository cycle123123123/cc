package com.jmreader.data.page

import android.graphics.Bitmap
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.jmreader.data.JmRepository

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
    private val prefetchGate = Semaphore(PREFETCH_PARALLELISM)
    private val prefetchJobs = ConcurrentHashMap<String, Job>()

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

        val bitmap = try {
            job.await()
        } finally {
            // 被取消时也要清掉 inflight，否则后续同一 URL 会拿到已取消的 Deferred
            mutex.withLock { inflight.remove(url) }
        }
        if (bitmap != null) cache.put(url, bitmap)
        return bitmap
    }

    fun prefetch(pages: List<Pair<String, Int>>) {
        // 只保留当前窗口内的预取，快速翻页时把已经翻过去的任务取消掉
        val wanted = pages.mapTo(HashSet()) { it.first }
        for ((url, job) in prefetchJobs.toMap()) {
            if (url !in wanted && !job.isCompleted) {
                job.cancel()
                prefetchJobs.remove(url)
            }
        }

        for ((url, page) in pages) {
            if (cache.get(url) != null) continue
            if (prefetchJobs[url]?.isActive == true) continue

            val job = scope.launch {
                prefetchGate.withPermit {
                    runCatching { load(url, page) }
                }
            }
            prefetchJobs[url] = job
            job.invokeOnCompletion { prefetchJobs.remove(url) }
        }
    }

    fun close() {
        scope.cancel()
        prefetchJobs.clear()
        cache.evictAll()
    }

    private companion object {
        /** 内存里同时保留的位图上限（约 3~4 页 1080 宽的漫画页）。 */
        const val CACHE_BYTES = 48 * 1024 * 1024

        /** 预取并发上限：之前是无限制，快速翻页会同时解码几十张 20MB 的位图。 */
        const val PREFETCH_PARALLELISM = 2
    }
}
