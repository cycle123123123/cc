package com.jmreader.data.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jmreader.data.download.DownloadRepository
import com.jmreader.data.model.JmException
import com.jmreader.core.ImageDescrambler
import com.jmreader.data.JmRepository

/**
 * 阅读页加载：下载目录 → 页缓存 → 网络 → 采样解码 → 分条还原。
 *
 * 从 [JmRepository] 拆出来，和「接口调用/线路选择」分开。
 */
internal class PageLoader(
    private val appContext: Context,
    private val pageCache: PageCache,
    private val fetchBytes: suspend (String) -> ByteArray,
) {

    suspend fun load(
        cid: Long,
        page: Int,
        url: String,
        aid: Long,
        scrambleId: Long,
        targetWidth: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val local = DownloadRepository.of(appContext).pageFile(cid, page)
        val bytes = local?.readBytes()
            ?: pageCache.read(cid, page)
            ?: fetchBytes(url).also { pageCache.write(cid, page, it) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        val srcWidth = bounds.outWidth.coerceAtLeast(1)
        val srcHeight = bounds.outHeight.coerceAtLeast(1)
        while (true) {
            val width = srcWidth / sample
            val height = srcHeight / sample
            val withinWidth = width / 2 < targetWidth
            val withinPixels = width.toLong() * height <= MAX_DECODE_PIXELS
            if (withinWidth && withinPixels) break
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw JmException.Parse(IOException("图片解码失败"))

        val cleanUrl = url.substringBefore('?')
        if (cleanUrl.endsWith(".gif", ignoreCase = true)) {
            bitmap
        } else {
            val key = cleanUrl.substringAfterLast('/').substringBeforeLast('.')
            ImageDescrambler.decode(bitmap, aid, scrambleId, key)
        }
    }

    private companion object {
        const val MAX_DECODE_PIXELS = 6_000_000L
    }
}
