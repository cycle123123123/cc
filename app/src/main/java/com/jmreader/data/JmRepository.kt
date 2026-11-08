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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** 所有网络访问的入口。无第三方 DI，直接构造。 */
class JmRepository(context: Context) {

    val auth = AuthStore(context)

    private val appContext = context.applicationContext

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

    // ------------------------------------------------------------ 浏览

    suspend fun latest(page: Int): List<AlbumBrief> =
        get("latest", mapOf("page" to page.toString()), ListSerializer(AlbumBrief.serializer()))

    suspend fun album(id: Long): AlbumDetail =
        get("album", mapOf("id" to id.toString()), AlbumDetail.serializer())

    suspend fun chapter(id: Long): ChapterInfo =
        get("chapter", mapOf("id" to id.toString()), ChapterInfo.serializer())

    suspend fun comicRead(id: Long): ComicRead =
        get("comic_read", mapOf("id" to id.toString()), ComicRead.serializer())

    suspend fun search(query: String, page: Int): SearchPage =
        get(
            "search",
            mapOf("search_query" to query, "page" to page.toString()),
            SearchPage.serializer(),
        )

    suspend fun week(): WeekOptions = get("week", emptyMap(), WeekOptions.serializer())

    suspend fun weekList(type: String, category: String, page: Int): WeekPage {
        val params = mutableMapOf("page" to page.toString())
        if (type.isNotBlank()) params["type"] = type
        if (category.isNotBlank()) params["category"] = category
        return get("week/filter", params, WeekPage.serializer())
    }

    suspend fun categories(): CategoryResponse = get("categories", emptyMap(), CategoryResponse.serializer())

    /** 分类浏览：c = 分类 slug，o = 排序（""/mv/mp/tf/old），page 从 1 开始。 */
    suspend fun categoryList(slug: String, order: String, page: Int): SearchPage {
        val params = mutableMapOf("c" to slug, "page" to page.toString())
        if (order.isNotBlank()) params["o"] = order
        return get("categories/filter", params, SearchPage.serializer())
    }

    /** /setting 里的版本信息，用于协议更新提示。缓存 10 分钟。 */
    @Volatile
    private var cachedSetting: AppSetting? = null

    @Volatile
    private var cachedSettingAt: Long = 0L

    suspend fun settingInfo(force: Boolean = false): AppSetting = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedSetting
        if (!force && cached != null && now - cachedSettingAt < SETTING_TTL_MS) return@withContext cached

        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val setting = json.decodeFromString(AppSetting.serializer(), request(base, "setting", emptyMap()))
        cachedSetting = setting
        cachedSettingAt = now
        setting
    }

    suspend fun forum(aid: Long, page: Int): ForumPage =
        get("forum", mapOf("aid" to aid.toString(), "page" to page.toString()), ForumPage.serializer())

    fun coverUrl(albumId: String, tall: Boolean = false): String {
        val suffix = if (tall) "_3x4" else ""
        return "$imgBase/media/albums/$albumId$suffix.jpg"
    }

    // ------------------------------------------------------------ 账号

    suspend fun login(username: String, password: String): Member = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = formPost(
            base,
            "login",
            mapOf("username" to username, "password" to password),
        )
        val member = try {
            json.decodeFromString(Member.serializer(), body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
        auth.save(member)
        member
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady()
            apiBase?.let { formPost(it, "logout", emptyMap()) }
        }
        auth.clear()
    }

    suspend fun favorites(page: Int): FavoritePage =
        get("favorite", mapOf("page" to page.toString()), FavoritePage.serializer())

    suspend fun history(page: Int): HistoryPage =
        get("watch_list", mapOf("page" to page.toString()), HistoryPage.serializer())

    /** 收藏 / 取消收藏。接口为 POST favorite，form 里带 aid。 */
    suspend fun addFavorite(aid: Long): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPost(base, "favorite", mapOf("aid" to aid.toString()))
        true
    }

    /** 点赞（切换）。接口为 POST like，form 里带 id。 */
    suspend fun toggleLike(aid: Long): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPost(base, "like", mapOf("id" to aid.toString()))
        true
    }

    /** 发表评论（需要登录）。接口为 POST comment。 */
    suspend fun postComment(aid: Long, content: String): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPost(base, "comment", mapOf("aid" to aid.toString(), "content" to content))
        true
    }

    /** 评论点赞（需要登录），返回体可能为空，只看 HTTP 是否成功。 */
    suspend fun voteComment(cid: String): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPostRaw(base, "comment_vote", mapOf("id" to cid))
    }

    /** 图源：0 = 跟随接口返回，1..4 = 指定 CDN host，5 = 测速最优。 */
    var imageShard: Int
        get() = prefs.getInt(KEY_IMG_SHARD, 0)
        set(value) = prefs.edit().putInt(KEY_IMG_SHARD, value.coerceIn(0, 5)).apply()

    val autoImageHost: String
        get() = prefs.getString(KEY_AUTO_HOST, JmConfig.IMAGE_SHARDS.first())
            ?: JmConfig.IMAGE_SHARDS.first()

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
                        http.newCall(request).execute().use { it.isSuccessful }
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

    // ------------------------------------------------------------ 图片

    /** 下载原始字节（不做任何处理），供离线下载使用。 */
    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) { fetchBytes(url) }

    /** 下载并还原一页。优先读本地下载文件，缺失时回退网络。 */
    suspend fun loadPage(
        cid: Long,
        page: Int,
        url: String,
        aid: Long,
        scrambleId: Long,
        targetWidth: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
            val local = DownloadRepository.of(appContext).pageFile(cid, page)
            val bytes = local?.readBytes() ?: fetchBytes(url)

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
            .get()
            .withCommonHeaders(ts)
            .build()
        return execute(request, ts)
    }

    private fun formPost(base: String, path: String, form: Map<String, String>): String {
        val ts = System.currentTimeMillis() / 1000
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply { form.forEach { (key, value) -> addFormDataPart(key, value) } }
            .build()
        val request = Request.Builder()
            .url(base + path + "?lang=" + JmConfig.DEFAULT_LANG)
            .post(body)
            .withCommonHeaders(ts)
            .build()
        return execute(request, ts)
    }

    /** 有些接口返回空 body（例如 comment_vote），只判断 HTTP 状态。 */
    private fun formPostRaw(base: String, path: String, form: Map<String, String>): Boolean {
        val ts = System.currentTimeMillis() / 1000
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply { form.forEach { (key, value) -> addFormDataPart(key, value) } }
            .build()
        val request = Request.Builder()
            .url(base + path + "?lang=" + JmConfig.DEFAULT_LANG)
            .post(body)
            .withCommonHeaders(ts)
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")
            return true
        }
    }

    /** 图源切换：只替换 host，路径和参数保持接口返回的原样。 */
    private fun rewriteImageHost(url: String): String = when (val index = imageShard) {
        0 -> url
        5 -> replaceHost(url, autoImageHost)
        else -> JmConfig.IMAGE_SHARDS.getOrNull(index - 1)?.let { replaceHost(url, it) } ?: url
    }

    private fun replaceHost(url: String, host: String): String = runCatching {
        val uri = java.net.URI(url)
        java.net.URI(uri.scheme, uri.userInfo, host, uri.port, uri.path, uri.query, uri.fragment).toString()
    }.getOrDefault(url)

    private fun Request.Builder.withCommonHeaders(ts: Long): Request.Builder {
        header("Token", JmCrypto.token(ts))
        header("Tokenparam", JmCrypto.tokenParam(ts))
        header("Accept", "application/json")
        header("Referer", JmConfig.WEB_REFERER)
        header("User-Agent", JmConfig.USER_AGENT)
        auth.jwt?.let { header("Authorization", "Bearer $it") }
        auth.session?.let { header("Cookie", "AVS=$it") }
        return this
    }

    private fun execute(request: Request, ts: Long): String {
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
            .url(rewriteImageHost(url))
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
        const val KEY_IMG_SHARD = "img_shard"
        const val KEY_AUTO_HOST = "auto_host"
        const val HTTP_CACHE_BYTES = 256L * 1024 * 1024
        const val SETTING_TTL_MS = 10L * 60L * 1000L
    }
}
