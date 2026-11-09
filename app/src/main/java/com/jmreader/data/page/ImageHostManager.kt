package com.jmreader.data.page

import android.content.SharedPreferences
import com.jmreader.core.JmConfig
import com.jmreader.core.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.jmreader.data.JmRepository

/**
 * 图源（CDN host）：手动选择、测速最优、图片 URL 重写。
 * 由 [com.jmreader.data.JmRepository] 持有并委托，prefs key 与取值规则不变。
 */
internal class ImageHostManager(
    private val prefs: SharedPreferences,
    private val http: OkHttpClient,
) {

    /** 图源：0 = 跟随接口返回，1..4 = 指定 CDN host，5 = 测速最优。 */
    var imageShard: Int
        get() = prefs.getInt(KEY_IMG_SHARD, 0)
        set(value) = prefs.edit().putInt(KEY_IMG_SHARD, value.coerceIn(0, 5)).apply()

    val autoImageHost: String
        get() = prefs.getString(KEY_AUTO_HOST, JmConfig.IMAGE_SHARDS.first())
            ?: JmConfig.IMAGE_SHARDS.first()

    /**
     * 首次进阅读器时后台自动测一次图源，之后 24 小时内不再测。
     * 不改变界面状态，后续翻页会直接走更快的 CDN。
     */
    suspend fun maybeAutoBenchmark(sampleUrl: String) = withContext(Dispatchers.IO) {
        if (imageShard != 0) return@withContext
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_AUTO_BENCH_AT, 0) < AUTO_BENCH_TTL_MS) return@withContext
        benchmarkImageHosts(sampleUrl)
        prefs.edit().putLong(KEY_AUTO_BENCH_AT, now).apply()
    }

    /**
     * 对 4 个 CDN host 并发 HEAD 测速，选最快的并切到「测速最优」。
     * @return (图源序号 1..4, 耗时毫秒)，全部失败返回 null
     */
    suspend fun benchmarkImageHosts(sampleUrl: String): Pair<Int, Long>? = withContext(Dispatchers.IO) {
        if (sampleUrl.isBlank()) return@withContext null

        val results = coroutineScope {
            JmConfig.IMAGE_SHARDS.mapIndexed { index, host ->
                index + 1 to async {
                    val url = replaceHost(sampleUrl, host)
                    val started = System.currentTimeMillis()
                    val ok = runCatching {
                        val request = Request.Builder()
                            .url(url)
                            .header("Referer", JmConfig.WEB_REFERER)
                            .header("User-Agent", JmConfig.USER_AGENT)
                            .head()
                            .build()
                        http.newCall(request).awaitResponse().use { it.isSuccessful }
                    }.getOrDefault(false)
                    if (ok) System.currentTimeMillis() - started else Long.MAX_VALUE
                }
            }.map { (index, deferred) -> index to deferred.await() }
        }

        val best = results.filter { it.second != Long.MAX_VALUE }.minByOrNull { it.second }
            ?: return@withContext null
        prefs.edit()
            .putString(KEY_AUTO_HOST, JmConfig.IMAGE_SHARDS[best.first - 1])
            .putInt(KEY_IMG_SHARD, 5)
            .apply()
        best
    }

    /** 图源切换：只替换 host，路径和参数保持接口返回的原样。 */
    fun rewrite(url: String): String = when (val index = imageShard) {
        0 -> url
        5 -> replaceHost(url, autoImageHost)
        else -> JmConfig.IMAGE_SHARDS.getOrNull(index - 1)?.let { replaceHost(url, it) } ?: url
    }

    private fun replaceHost(url: String, host: String): String = runCatching {
        val uri = java.net.URI(url)
        java.net.URI(uri.scheme, uri.userInfo, host, uri.port, uri.path, uri.query, uri.fragment).toString()
    }.getOrDefault(url)

    companion object {
        private const val KEY_IMG_SHARD = "img_shard"
        private const val KEY_AUTO_HOST = "auto_host"
        private const val KEY_AUTO_BENCH_AT = "auto_bench_at"
        private const val AUTO_BENCH_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
