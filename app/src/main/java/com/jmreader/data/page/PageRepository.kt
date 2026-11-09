package com.jmreader.data.page

import com.jmreader.data.model.ComicRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import android.graphics.Bitmap
import com.jmreader.data.JmRepository

/**
 * 阅读页相关的仓库逻辑：原始字节下载、字节缓存占用/清理、下一话预取、单页解码。
 * 由 [com.jmreader.data.JmRepository] 持有并委托，行为与拆分前一致。
 */
internal class PageRepository(
    private val pageCache: PageCache,
    private val pageLoader: PageLoader,
    private val http: OkHttpClient,
    private val fetchBytes: suspend (String) -> ByteArray,
    private val comicRead: suspend (Long) -> ComicRead,
) {

    /** 下载原始字节（不做任何处理），供离线下载使用。 */
    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) { fetchBytes(url) }

    /** 磁盘缓存占用（阅读页缓存 + HTTP 缓存），给「清除快取」显示用。 */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        pageCache.sizeBytes() + (http.cache?.size() ?: 0L)
    }

    /** 清空阅读页字节缓存和 HTTP 缓存（Coil 的图片缓存由 UI 层清）。 */
    suspend fun clearCaches() = withContext(Dispatchers.IO) {
        pageCache.clear()
        runCatching { http.cache?.evictAll() }
    }

    /** 预取下一话的前 3 页到磁盘缓存，切话时首屏不用等。 */
    suspend fun prefetchChapter(cid: Long) = withContext(Dispatchers.IO) {
        val comic = runCatching { comicRead(cid) }.getOrNull() ?: return@withContext
        comic.images.take(3).forEach { page ->
            if (pageCache.read(cid, page.page) == null) {
                runCatching { pageCache.write(cid, page.page, fetchBytes(page.image)) }
            }
        }
    }

    /** 下载并还原一页。优先读本地下载文件，其次读页面缓存，最后走网络。 */
    suspend fun loadPage(
        cid: Long,
        page: Int,
        url: String,
        aid: Long,
        scrambleId: Long,
        targetWidth: Int,
    ): Bitmap = pageLoader.load(cid, page, url, aid, scrambleId, targetWidth)
}
