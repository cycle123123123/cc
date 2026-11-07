package com.jmreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jmreader.core.ImageDescrambler
import com.jmreader.core.JmConfig
import com.jmreader.core.JmCrypto
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/** 所有网络访问的入口。无第三方 DI，直接构造。 */
class JmRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val prefs = context.applicationContext.getSharedPreferences("jmreader", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "http"), HTTP_CACHE_BYTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var apiBase: String? = null

    @Volatile
    var imgBase: String = JmConfig.IMG_HOST_FALLBACK
        private set

    // ------------------------------------------------------------ public API

    suspend fun latest(page: Int): List<AlbumBrief> =
        get("latest", mapOf("page" to page.toString()), ListSerializer(AlbumBrief.serializer()))

    suspend fun album(id: Long): AlbumDetail =
        get("album", mapOf("id" to id.toString()), AlbumDetail.serializer())

    suspend fun chapter(id: Long): ChapterInfo =
        get("chapter", mapOf("id" to id.toString()), ChapterInfo.serializer())

    suspend fun comicRead(id: Long): ComicRead =
        get("comic_read", mapOf("id" to id.toString()), ComicRead.serializer())

    fun coverUrl(albumId: String, tall: Boolean = false): String {
        val suffix = if (tall) "_3x4" else ""
        return "$imgBase/media/albums/$albumId$suffix.jpg"
    }

    /** 下载并还原一页。targetWidth 用于计算解码采样率。 */
    suspend fun loadPage(url: String, aid: Long, scrambleId: Long, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val bytes = fetchBytes(url)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sample = 1
            var width = bounds.outWidth.coerceAtLeast(1)
            while (width / (sample * 2) >= targetWidth) sample *= 2

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

    /** 从 /album 的 series 字段提取目录；单本作品返回空列表。 */
    fun seriesChapters(detail: AlbumDetail): List<ChapterEntry> = SeriesParser.parse(detail.series)

    // ------------------------------------------------------------ internals

    private suspend fun <T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = request(base, path, params)
        try {
            json.decodeFromString(deserializer, body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
    }

    @Synchronized
    private fun ensureReady() {
        if (apiBase != null) return

        val candidates = LinkedHashSet<String>()
        prefs.getString(KEY_API_BASE, null)?.let { candidates.add(it) }
        collectNetworkHosts(candidates)
        JmConfig.BUILTIN_HOSTS.forEach { candidates.add("https://$it/") }

        for (base in candidates) {
            val setting = runCatching { requestSetting(base) }.getOrNull() ?: continue
            apiBase = base
            imgBase = setting.imgHost.ifBlank { JmConfig.IMG_HOST_FALLBACK }
            prefs.edit().putString(KEY_API_BASE, base).apply()
            return
        }
        throw JmException.Network("没有可用线路")
    }

    private fun requestSetting(base: String): AppSetting =
        json.decodeFromString(AppSetting.serializer(), request(base, "setting", emptyMap()))

    private fun request(base: String, path: String, params: Map<String, String>): String {
        val ts = System.currentTimeMillis() / 1000
        val query = (params + ("lang" to JmConfig.DEFAULT_LANG)).entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        val request = Request.Builder()
            .url(base + path + "?" + query)
            .header("Token", JmCrypto.token(ts))
            .header("Tokenparam", JmCrypto.tokenParam(ts))
            .header("Accept", "application/json")
            .header("Referer", JmConfig.WEB_REFERER)
            .header("User-Agent", JmConfig.USER_AGENT)
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")

            val body = response.body?.string().orEmpty()
            val envelope = try {
                json.parseToJsonElement(body).jsonObject
            } catch (t: Throwable) {
                throw JmException.Parse(t)
            }
            val code = envelope["code"]?.jsonPrimitive?.intOrNull ?: -1
            if (code != 200) throw JmException.Api(code, "接口返回 code=$code")
            val data = envelope["data"]?.jsonPrimitive?.contentOrNull
                ?: throw JmException.Api(code, "接口返回缺少 data")
            return try {
                JmCrypto.decryptPayload(data, ts)
            } catch (t: Throwable) {
                throw JmException.Decrypt(t)
            }
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Referer", JmConfig.WEB_REFERER)
            .header("User-Agent", JmConfig.USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")
            return response.body?.bytes() ?: throw JmException.Network("响应为空")
        }
    }

    /** 拉取并解密线路清单。失败不影响后续兜底逻辑。 */
    private fun collectNetworkHosts(target: MutableSet<String>) {
        for (url in JmConfig.SERVER_LIST_URLS) {
            try {
                val request = Request.Builder().url(url).build()
                val raw = http.newCall(request).execute().use { it.body?.string().orEmpty() }
                if (raw.isBlank()) continue

                val root = json.parseToJsonElement(JmCrypto.decryptHostList(raw)).jsonObject
                val list = root["jm3_Server"]?.jsonArray
                    ?: runCatching { root["Server"]?.jsonArray }.getOrNull()
                    ?: continue

                for (element in list) {
                    val host = when (element) {
                        is JsonArray -> element.firstOrNull()?.jsonPrimitive?.contentOrNull
                        else -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                    }
                    if (!host.isNullOrBlank()) target.add("https://$host/")
                }
                if (target.isNotEmpty()) return
            } catch (_: Throwable) {
                // 换下一个下发地址
            }
        }
    }

    private companion object {
        const val KEY_API_BASE = "api_base"
        const val HTTP_CACHE_BYTES = 256L * 1024 * 1024
    }
}
