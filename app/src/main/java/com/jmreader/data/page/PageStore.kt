package com.jmreader.data.page

import android.graphics.Bitmap
import android.util.LruCache

import com.jmreader.data.JmRepository

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

    fun cached(page: Int): Bitmap? = cache.get(pageKey(page))

    suspend fun load(url: String, page: Int): Bitmap? {
        // 緩存 key 必須用頁碼：離線閱讀時 url 全是空串，用 url 當 key 會讓整話都顯示第 1 頁
        val key = pageKey(page)
        cache.get(key)?.let { return it }

        val job = mutex.withLock {
            cache.get(key)?.let { return it }
            inflight[key] ?: scope.async {
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
            }.also { inflight[key] = it }
        }

        val bitmap = try {
            job.await()
        } finally {
            // 被取消时也要清掉 inflight，否则后续同一页会拿到已取消的 Deferred
            mutex.withLock { inflight.remove(key) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    fun prefetch(pages: List<Pair<String, Int>>) {
        // 只保留当前窗口内的预取，快速翻页时把已经翻过去的任务取消掉
        val wanted = pages.mapTo(HashSet()) { pageKey(it.second) }
        for ((key, job) in prefetchJobs.toMap()) {
            if (key !in wanted && !job.isCompleted) {
                job.cancel()
                prefetchJobs.remove(key)
            }
        }

        for ((url, page) in pages) {
            val key = pageKey(page)
            if (cache.get(key) != null) continue
            if (prefetchJobs[key]?.isActive == true) continue

            val job = scope.launch {
                prefetchGate.withPermit {
                    runCatching { load(url, page) }
                }
            }
            prefetchJobs[key] = job
            job.invokeOnCompletion { prefetchJobs.remove(key) }
        }
    }

    private fun pageKey(page: Int): String = "p$page"

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
