package com.jmreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jmreader.core.ImageDescrambler
import com.jmreader.core.JmConfig
import com.jmreader.core.JmCrypto
import com.jmreader.core.awaitResponse
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** 所有网络访问的入口。无第三方 DI，直接构造（用 [of] 取单例）。 */
class JmRepository(context: Context) {

    val auth = AuthStore(context)

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val prefs = context.applicationContext.getSharedPreferences("jmreader", Context.MODE_PRIVATE)

    /** 给 Coil 复用同一个连接池和磁盘缓存。 */
    val okHttpClient: OkHttpClient get() = http

    private val http = OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "http"), HTTP_CACHE_BYTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val hostBenchmark = HostBenchmark(http, json, PROBE_TIMEOUT_MS)

    /** 页面字节缓存（带容量上限和 LRU 淘汰）。 */
    private val pageCache = PageCache(File(appContext.cacheDir, "pages"))

    @Volatile
    private var apiBase: String? = null

    /** 线路发现/切换要串行，用 Mutex 而不是 @Synchronized（里面会挂起）。 */
    private val initMutex = Mutex()

    private val albumCache = android.util.LruCache<Long, AlbumDetail>(16)
    private val albumCacheAt = mutableMapOf<Long, Long>()
    private val readCache = android.util.LruCache<Long, ComicRead>(8)
    private val readCacheAt = mutableMapOf<Long, Long>()

    @Volatile
    var imgBase: String = JmConfig.IMG_HOST_FALLBACK
        private set

    // ------------------------------------------------------------ 浏览

    suspend fun latest(page: Int): List<AlbumBrief> {
        val list = get("latest", mapOf("page" to page.toString()), ListSerializer(AlbumBrief.serializer()))
        if (page == 1) saveLatestCache(list)
        return list
    }

    /** 冷启动时先用上次的首页数据垫着，避免一直转圈。 */
    fun cachedLatest(): List<AlbumBrief>? = runCatching {
        val file = File(appContext.filesDir, HOME_CACHE)
        if (!file.exists()) return null
        json.decodeFromString(ListSerializer(AlbumBrief.serializer()), file.readText())
    }.getOrNull()

    private fun saveLatestCache(list: List<AlbumBrief>) {
        runCatching {
            File(appContext.filesDir, HOME_CACHE)
                .writeText(json.encodeToString(ListSerializer(AlbumBrief.serializer()), list))
        }
    }

    suspend fun album(id: Long): AlbumDetail {
        val cached = albumCache.get(id)
        val cachedAt = albumCacheAt[id] ?: 0L
        if (cached != null && System.currentTimeMillis() - cachedAt < ALBUM_TTL_MS) return cached

        val album = get("album", mapOf("id" to id.toString()), AlbumDetail.serializer())
        albumCache.put(id, album)
        albumCacheAt[id] = System.currentTimeMillis()
        return album
    }

    suspend fun chapter(id: Long): ChapterInfo =
        get("chapter", mapOf("id" to id.toString()), ChapterInfo.serializer())

    suspend fun comicRead(id: Long): ComicRead {
        val cached = readCache.get(id)
        val cachedAt = readCacheAt[id] ?: 0L
        if (cached != null && System.currentTimeMillis() - cachedAt < READ_TTL_MS) return cached

        val comic = get("comic_read", mapOf("id" to id.toString()), ComicRead.serializer())
        readCache.put(id, comic)
        readCacheAt[id] = System.currentTimeMillis()
        return comic
    }

    /** @param category 分类 slug（同人 doujin / 單本 single / 短篇 short / 韓漫 hanman…），空串表示全部 */
    suspend fun search(query: String, page: Int, category: String = ""): SearchPage {
        val params = mutableMapOf("search_query" to query, "page" to page.toString())
        if (category.isNotBlank()) params["c"] = category
        return get("search", params, SearchPage.serializer())
    }

    suspend fun week(): WeekOptions = get("week", emptyMap(), WeekOptions.serializer())

    /** 周榜：type = manga/another，period = 期数 id（`/week` 的 categories[].id）。 */
    suspend fun weekList(type: String, period: String, page: Int): WeekPage {
        val params = mutableMapOf("page" to page.toString())
        if (type.isNotBlank()) params["type"] = type
        if (period.isNotBlank()) params["id"] = period
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

        if (!force) {
            val raw = prefs.getString(KEY_SETTING_JSON, null)
            val at = prefs.getLong(KEY_SETTING_AT, 0)
            if (raw != null && now - at < SETTING_TTL_MS) {
                runCatching { json.decodeFromString(AppSetting.serializer(), raw) }.getOrNull()?.let {
                    cachedSetting = it
                    cachedSettingAt = at
                    if (it.imgHost.isNotBlank()) imgBase = it.imgHost
                    return@withContext it
                }
            }
        }

        ensureReady()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val setting = json.decodeFromString(AppSetting.serializer(), request(base, "setting", emptyMap()))
        cachedSetting = setting
        cachedSettingAt = now
        if (setting.imgHost.isNotBlank()) imgBase = setting.imgHost
        prefs.edit()
            .putString(KEY_SETTING_JSON, json.encodeToString(AppSetting.serializer(), setting))
            .putLong(KEY_SETTING_AT, now)
            .apply()
        setting
    }

    suspend fun forum(aid: Long, page: Int): ForumPage =
        get("forum", mapOf("aid" to aid.toString(), "page" to page.toString()), ForumPage.serializer())

    fun coverUrl(albumId: String, tall: Boolean = false): String {
        val suffix = if (tall) "_3x4" else ""
        return "$imgBase/media/albums/$albumId$suffix.jpg"
    }

    /** 把接口返回的 `/media/...` 相对路径拼成完整地址。 */
    fun assetUrl(path: String): String =
        if (path.startsWith("http")) path else imgBase + path

    // ------------------------------------------------------------ 账号

    suspend fun login(username: String, password: String, remember: Boolean = true): Member =
        withContext(Dispatchers.IO) {
            ensureReady()
            val base = apiBase ?: throw JmException.Network("线路未就绪")
            val member = performLoginRequest(base, username, password)
            if (remember) auth.saveCredentials(username, password) else auth.clearCredentials()
            member
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady()
            apiBase?.let { formPost(it, "logout", emptyMap()) }
        }
        auth.clearAll()
    }

    @Volatile
    private var loggingIn = false

    private suspend fun performLoginRequest(base: String, username: String, password: String): Member {
        val body = formPost(base, "login", mapOf("username" to username, "password" to password))
        val member = try {
            json.decodeFromString(Member.serializer(), body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
        auth.save(member)
        return member
    }

    /** token 过期但有记住的密码时，静默重新登录。 */
    private suspend fun ensureSession() {
        if (auth.isValid) return
        if (auth.hasCredentials) silentLogin()
    }

    private suspend fun silentLogin(): Boolean {
        if (loggingIn) return false
        val credentials = auth.credentials() ?: return false
        loggingIn = true
        return try {
            ensureReady()
            val base = apiBase ?: return false
            performLoginRequest(base, credentials.first, credentials.second)
            true
        } catch (_: Throwable) {
            false
        } finally {
            loggingIn = false
        }
    }

    suspend fun favorites(page: Int): FavoritePage =
        get("favorite", mapOf("page" to page.toString()), FavoritePage.serializer())

    suspend fun favorites(page: Int, folderId: String): FavoritePage {
        val params = mutableMapOf("page" to page.toString())
        if (folderId.isNotBlank()) params["folder_id"] = folderId
        return get("favorite", params, FavoritePage.serializer())
    }

    suspend fun history(page: Int): HistoryPage =
        get("watch_list", mapOf("page" to page.toString()), HistoryPage.serializer())

    /** 收藏 / 取消收藏（toggle），返回 type = add / remove。 */
    suspend fun toggleFavorite(aid: Long): FavoriteToggleResult = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = formPost(base, "favorite", mapOf("aid" to aid.toString()))
        runCatching { json.decodeFromString(FavoriteToggleResult.serializer(), body) }
            .getOrElse { FavoriteToggleResult() }
    }

    suspend fun addFavoriteFolder(name: String): SimpleResult =
        folderOperation(mapOf("type" to "add", "folder_name" to name, "folder_id" to "", "nid" to ""))

    suspend fun renameFavoriteFolder(folderId: String, name: String): SimpleResult =
        folderOperation(mapOf("type" to "edit", "folder_name" to name, "folder_id" to folderId))

    suspend fun deleteFavoriteFolder(folderId: String): SimpleResult =
        folderOperation(mapOf("type" to "del", "folder_name" to "", "folder_id" to folderId))

    suspend fun moveFavorite(folderId: String, aid: Long): SimpleResult =
        folderOperation(mapOf("type" to "move", "folder_id" to folderId, "aid" to aid.toString()))

    private suspend fun folderOperation(form: Map<String, String>): SimpleResult = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = formPost(base, "favorite_folder", form)
        runCatching { json.decodeFromString(SimpleResult.serializer(), body) }.getOrElse { SimpleResult() }
    }

    suspend fun deleteComment(commentId: String): SimpleResult = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = formPost(base, "comment_delete", mapOf("comment_id" to commentId))
        runCatching { json.decodeFromString(SimpleResult.serializer(), body) }.getOrElse { SimpleResult() }
    }

    suspend fun promote(): List<PromoteSection> =
        get("promote", emptyMap(), ListSerializer(PromoteSection.serializer()))

    suspend fun randomRecommend(): List<AlbumBrief> =
        get("random_recommend", emptyMap(), ListSerializer(AlbumBrief.serializer()))

    suspend fun creatorAuthors(page: Int): List<CreatorAuthor> {
        val response = get(
            "creator_author",
            mapOf("page" to page.toString()),
            CreatorAuthorResponse.serializer(),
        )
        return response.data.content
    }

    suspend fun creatorWorks(authorId: String, page: Int): List<CreatorWork> {
        val response = get(
            "creator_work",
            mapOf("author_id" to authorId, "page" to page.toString()),
            CreatorWorkResponse.serializer(),
        )
        return response.data.content
    }

    /** 收藏 / 取消收藏。接口为 POST favorite，form 里带 aid。 */
    suspend fun addFavorite(aid: Long): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPost(base, "favorite", mapOf("aid" to aid.toString()))
        true
    }

    /** 点赞（切换）。接口为 POST like，form 里带 id。 */
    suspend fun toggleLike(aid: Long): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPost(base, "like", mapOf("id" to aid.toString()))
        true
    }

    /** 发表评论 / 回复（需要登录）。接口为 POST comment。 */
    suspend fun postComment(aid: Long, content: String, parentCommentId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            ensureReady()
            ensureSession()
            val base = apiBase ?: throw JmException.Network("线路未就绪")
            val form = mutableMapOf("comment" to content, "aid" to aid.toString())
            if (!parentCommentId.isNullOrBlank()) form["comment_id"] = parentCommentId
            formPost(base, "comment", form)
            true
        }

    /** 评论点赞（需要登录），返回体可能为空，只看 HTTP 是否成功。 */
    suspend fun voteComment(cid: String): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        formPostRaw(base, "comment_vote", mapOf("id" to cid))
    }

    /**
     * 首次进阅读器时后台自动测一次图源，之后 24 小时内不再测。
     * 不改变界面状态，后续翻页会直接走更快的 CDN。
     */
    suspend fun maybeAutoBenchmarkImageHost(sampleUrl: String) = withContext(Dispatchers.IO) {
        if (imageShard != 0) return@withContext
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_AUTO_BENCH_AT, 0) < AUTO_BENCH_TTL_MS) return@withContext
        benchmarkImageHosts(sampleUrl)
        prefs.edit().putLong(KEY_AUTO_BENCH_AT, now).apply()
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

    // ------------------------------------------------------------ 图片

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

    /** 从 /album 的 series 字段提取目录；单本作品返回空列表。 */
    fun seriesChapters(detail: AlbumDetail): List<ChapterEntry> = SeriesParser.parse(detail.series)

    // ------------------------------------------------------------ internals

    private suspend fun <T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        ensureReady()
        ensureSession()
        val base = apiBase ?: throw JmException.Network("线路未就绪")
        val body = try {
            request(base, path, params)
        } catch (e: JmException.Auth) {
            // token 失效：用记住的密码重登一次再重试
            if (silentLogin()) request(base, path, params) else throw e
        } catch (e: JmException.Network) {
            // 缓存线路失效：重新发现一次再重试
            val fresh = recoverBase()
            if (fresh != null && fresh != base) request(fresh, path, params) else throw e
        }
        try {
            json.decodeFromString(deserializer, body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
    }

    private suspend fun ensureReady() {
        if (apiBase != null) return

        initMutex.withLock {
            if (apiBase != null) return

            // 有缓存线路就直接用，把「拉线路清单 + 探测 setting」留给真正的失败路径
            prefs.getString(KEY_API_BASE, null)?.let { cached ->
                apiBase = cached
                runCatching {
                    val raw = prefs.getString(KEY_SETTING_JSON, null) ?: return@runCatching
                    val setting = json.decodeFromString(AppSetting.serializer(), raw)
                    if (setting.imgHost.isNotBlank()) imgBase = setting.imgHost
                }
                return
            }

            rediscover()
        }
    }

    /** 并发探测可用线路，成功后写入缓存。 */
    private suspend fun rediscover() {
        val candidates = LinkedHashSet<String>()
        collectNetworkHosts(candidates)
        JmConfig.BUILTIN_HOSTS.forEach { candidates.add("https://$it/") }

        val best = hostBenchmark.best(candidates) ?: throw JmException.Network("没有可用线路")
        adopt(best)
    }

    private fun adopt(probe: HostBenchmark.Probe) {
        apiBase = probe.base
        val setting = probe.setting ?: return
        imgBase = setting.imgHost.ifBlank { JmConfig.IMG_HOST_FALLBACK }
        prefs.edit()
            .putString(KEY_API_BASE, probe.base)
            .putString(KEY_SETTING_JSON, json.encodeToString(AppSetting.serializer(), setting))
            .putLong(KEY_SETTING_AT, System.currentTimeMillis())
            .putLong(KEY_BENCH_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * 在后台重新测速并切到最快线路。默认 30 分钟内不重复测。
     * 之前缓存的那条线路可能刚好是最慢的（实测 8s vs 0.8s）。
     */
    suspend fun optimizeHost(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_BENCH_AT, 0) < BENCH_TTL_MS) return@withContext
        val current = apiBase ?: prefs.getString(KEY_API_BASE, null) ?: return@withContext

        val candidates = LinkedHashSet<String>().apply {
            add(current)
            JmConfig.BUILTIN_HOSTS.forEach { add("https://$it/") }
        }
        val best = hostBenchmark.best(candidates) ?: return@withContext
        prefs.edit().putLong(KEY_BENCH_AT, now).apply()
        if (best.base != current) adopt(best)
    }

    /** 请求失败时重新发现线路；返回新的 base 或 null。 */
    private suspend fun recoverBase(): String? = initMutex.withLock {
        apiBase = null
        runCatching { rediscover() }
        apiBase
    }

    private suspend fun request(base: String, path: String, params: Map<String, String>): String {
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

    private suspend fun formPost(base: String, path: String, form: Map<String, String>): String {
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
    private suspend fun formPostRaw(base: String, path: String, form: Map<String, String>): Boolean {
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
        http.newCall(request).awaitResponse().use { response ->
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

    private suspend fun execute(request: Request, ts: Long): String {
        http.newCall(request).awaitResponse().use { response ->
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

    private suspend fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(rewriteImageHost(url))
            .header("Referer", JmConfig.WEB_REFERER)
            .header("User-Agent", JmConfig.USER_AGENT)
            .build()
        http.newCall(request).awaitResponse().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")
            return response.body?.bytes() ?: throw JmException.Network("响应为空")
        }
    }

    /** 拉取并解密线路清单。失败不影响后续兜底逻辑。 */
    private suspend fun collectNetworkHosts(target: MutableSet<String>) {
        for (url in JmConfig.SERVER_LIST_URLS) {
            try {
                val request = Request.Builder().url(url).build()
                val raw = http.newCall(request).awaitResponse().use { it.body?.string().orEmpty() }
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

    companion object {
        @Volatile
        private var instance: JmRepository? = null

        /** 全局单例：OkHttp 的 Cache 要求同一目录只有一个实例，不能到处 new。 */
        fun of(context: Context): JmRepository =
            instance ?: synchronized(this) {
                instance ?: JmRepository(context.applicationContext).also { instance = it }
            }

        private const val KEY_API_BASE = "api_base"
        private const val KEY_IMG_SHARD = "img_shard"
        private const val KEY_AUTO_HOST = "auto_host"
        private const val KEY_SETTING_JSON = "setting_json"
        private const val KEY_SETTING_AT = "setting_at"
        private const val KEY_BENCH_AT = "bench_at"
        private const val KEY_AUTO_BENCH_AT = "auto_bench_at"
        private const val HOME_CACHE = "home_cache.json"
        private const val HTTP_CACHE_BYTES = 256L * 1024 * 1024
        private const val SETTING_TTL_MS = 10L * 60L * 1000L
        private const val BENCH_TTL_MS = 30L * 60L * 1000L
        private const val AUTO_BENCH_TTL_MS = 24L * 60L * 60L * 1000L
        private const val PROBE_TIMEOUT_MS = 2500L
        private const val ALBUM_TTL_MS = 60L * 1000L
        private const val READ_TTL_MS = 5L * 60L * 1000L
        private const val MAX_DECODE_PIXELS = 6_000_000L
    }
}
