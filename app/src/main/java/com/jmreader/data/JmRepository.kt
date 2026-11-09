package com.jmreader.data

import android.content.Context
import android.graphics.Bitmap
import com.jmreader.core.JmConfig
import com.jmreader.core.TtlCache
import com.jmreader.data.local.AuthStore
import com.jmreader.data.local.HomeCache
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.AlbumDetail
import com.jmreader.data.model.AppSetting
import com.jmreader.data.model.ChapterEntry
import com.jmreader.data.model.ComicRead
import com.jmreader.data.model.JmException
import com.jmreader.data.model.Member
import com.jmreader.data.model.SeriesParser
import com.jmreader.data.net.AccountApi
import com.jmreader.data.net.ApiFetcher
import com.jmreader.data.net.BrowseApi
import com.jmreader.data.net.CatalogApi
import com.jmreader.data.net.HostBenchmark
import com.jmreader.data.net.HostManager
import com.jmreader.data.net.JmHttp
import com.jmreader.data.net.SessionManager
import com.jmreader.data.page.ImageHostManager
import com.jmreader.data.page.PageCache
import com.jmreader.data.page.PageLoader
import com.jmreader.data.page.PageRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import com.jmreader.data.model.CategoryResponse
import com.jmreader.data.model.ChapterInfo
import com.jmreader.data.model.CreatorAuthor
import com.jmreader.data.model.CreatorWork
import com.jmreader.data.model.FavoritePage
import com.jmreader.data.model.FavoriteToggleResult
import com.jmreader.data.model.ForumPage
import com.jmreader.data.model.HistoryPage
import com.jmreader.data.model.PromoteSection
import com.jmreader.data.model.SearchPage
import com.jmreader.data.model.SimpleResult
import com.jmreader.data.model.WeekOptions
import com.jmreader.data.model.WeekPage

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

    /** 信封层：拼参数、签名头、解密；仓库只管线路与重试。 */
    private val jmHttp = JmHttp(http, json, auth)

    /** 线路发现与 `/setting` 元信息。 */
    private val hosts = HostManager(prefs, json, http, hostBenchmark, jmHttp)

    /** 登录态与静默重登。 */
    private val session = SessionManager(
        auth = auth,
        json = json,
        jmHttp = jmHttp,
        ensureReady = { hosts.ensureReady() },
        base = { hosts.apiBase },
    )

    /** 只读接口的统一取数入口（带线路重试与鉴权刷新）。 */
    private val fetcher = object : ApiFetcher {
        override suspend fun <T> fetch(
            path: String,
            params: Map<String, String>,
            deserializer: DeserializationStrategy<T>,
        ): T = get(path, params, deserializer)
    }

    private val browse = BrowseApi(fetcher)

    private val albumCache = TtlCache<Long, AlbumDetail>(16, ALBUM_TTL_MS)
    private val readCache = TtlCache<Long, ComicRead>(8, READ_TTL_MS)

    private val catalog = CatalogApi(fetcher, albumCache, readCache, HomeCache(appContext.filesDir, json))

    private val account = AccountApi(fetcher, ::authedForm, ::authedFormRaw, json)

    /** 图源选择与测速。 */
    private val imageHosts = ImageHostManager(prefs, http)

    /** 页面字节缓存（带容量上限和 LRU 淘汰）与阅读页加载器。 */
    private val pageCache = PageCache(File(appContext.cacheDir, "pages"))
    private val pageLoader = PageLoader(appContext, pageCache) { url -> fetchBytes(url) }
    private val pages = PageRepository(
        pageCache = pageCache,
        pageLoader = pageLoader,
        http = http,
        fetchBytes = { url -> fetchBytes(url) },
        comicRead = { cid -> comicRead(cid) },
    )

    val imgBase: String get() = hosts.imgBase

    // ------------------------------------------------------------ 浏览

    suspend fun latest(page: Int): List<AlbumBrief> = catalog.latest(page)

    /** 冷启动时先用上次的首页数据垫着，避免一直转圈。 */
    fun cachedLatest(): List<AlbumBrief>? = catalog.cachedLatest()

    suspend fun album(id: Long): AlbumDetail = catalog.album(id)

    suspend fun chapter(id: Long): ChapterInfo = catalog.chapter(id)

    suspend fun comicRead(id: Long): ComicRead = catalog.comicRead(id)

    suspend fun search(query: String, page: Int, category: String = "", order: String = ""): SearchPage =
        catalog.search(query, page, category, order)

    suspend fun week(): WeekOptions = browse.week()

    /** 周榜：type = manga/another，period = 期数 id（`/week` 的 categories[].id）。 */
    suspend fun weekList(type: String, period: String, page: Int): WeekPage = browse.weekList(type, period, page)

    suspend fun categories(): CategoryResponse = catalog.categories()

    /** 分类浏览：c = 分类 slug，o = 排序（""/mv/mp/tf/old），page 从 1 开始。 */
    suspend fun categoryList(slug: String, order: String, page: Int): SearchPage = catalog.categoryList(slug, order, page)

    /** /setting 里的版本信息，用于协议更新提示。缓存 10 分钟。 */
    suspend fun settingInfo(force: Boolean = false): AppSetting = hosts.settingInfo(force)

    suspend fun forum(aid: Long, page: Int): ForumPage = browse.forum(aid, page)

    fun coverUrl(albumId: String, tall: Boolean = false): String = hosts.coverUrl(albumId, tall)

    /** 把接口返回的 `/media/...` 相对路径拼成完整地址。 */
    fun assetUrl(path: String): String = hosts.assetUrl(path)

    // ------------------------------------------------------------ 账号

    suspend fun login(username: String, password: String, remember: Boolean = true): Member =
        session.login(username, password, remember)

    suspend fun logout() = session.logout()

    suspend fun favorites(page: Int): FavoritePage = account.favorites(page)

    suspend fun favorites(page: Int, folderId: String): FavoritePage = account.favorites(page, folderId)

    suspend fun history(page: Int): HistoryPage = account.history(page)

    /** 收藏 / 取消收藏（toggle），返回 type = add / remove。 */
    suspend fun toggleFavorite(aid: Long): FavoriteToggleResult = account.toggleFavorite(aid)

    suspend fun addFavoriteFolder(name: String): SimpleResult = account.addFavoriteFolder(name)

    suspend fun renameFavoriteFolder(folderId: String, name: String): SimpleResult =
        account.renameFavoriteFolder(folderId, name)

    suspend fun deleteFavoriteFolder(folderId: String): SimpleResult = account.deleteFavoriteFolder(folderId)

    suspend fun moveFavorite(folderId: String, aid: Long): SimpleResult = account.moveFavorite(folderId, aid)

    suspend fun deleteComment(commentId: String): SimpleResult = account.deleteComment(commentId)

    suspend fun promote(): List<PromoteSection> = browse.promote()

    suspend fun randomRecommend(): List<AlbumBrief> = browse.randomRecommend()

    suspend fun creatorAuthors(page: Int): List<CreatorAuthor> = browse.creatorAuthors(page)

    suspend fun creatorWorks(authorId: String, page: Int): List<CreatorWork> = browse.creatorWorks(authorId, page)

    /** 收藏 / 取消收藏。接口为 POST favorite，form 里带 aid。 */
    suspend fun addFavorite(aid: Long): Boolean = account.addFavorite(aid)

    /** 点赞（切换）。接口为 POST like，form 里带 id。 */
    suspend fun toggleLike(aid: Long): Boolean = account.toggleLike(aid)

    /** 发表评论 / 回复（需要登录）。接口为 POST comment。 */
    suspend fun postComment(aid: Long, content: String, parentCommentId: String? = null): Boolean =
        account.postComment(aid, content, parentCommentId)

    /** 评论点赞（需要登录），返回体可能为空，只看 HTTP 是否成功。 */
    suspend fun voteComment(cid: String): Boolean = account.voteComment(cid)

    // ------------------------------------------------------------ 图源

    /** 图源：0 = 跟随接口返回，1..4 = 指定 CDN host，5 = 测速最优。 */
    var imageShard: Int
        get() = imageHosts.imageShard
        set(value) {
            imageHosts.imageShard = value
        }

    val autoImageHost: String get() = imageHosts.autoImageHost

    /**
     * 首次进阅读器时后台自动测一次图源，之后 24 小时内不再测。
     * 不改变界面状态，后续翻页会直接走更快的 CDN。
     */
    suspend fun maybeAutoBenchmarkImageHost(sampleUrl: String) = imageHosts.maybeAutoBenchmark(sampleUrl)

    /**
     * 对 4 个 CDN host 并发 HEAD 测速，选最快的并切到「测速最优」。
     * @return (图源序号 1..4, 耗时毫秒)，全部失败返回 null
     */
    suspend fun benchmarkImageHosts(sampleUrl: String): Pair<Int, Long>? =
        imageHosts.benchmarkImageHosts(sampleUrl)

    /**
     * 在后台重新测速并切到最快线路。默认 30 分钟内不重复测。
     * 之前缓存的那条线路可能刚好是最慢的（实测 8s vs 0.8s）。
     */
    suspend fun optimizeHost(force: Boolean = false) = hosts.optimizeHost(force)

    // ------------------------------------------------------------ 阅读页

    /** 下载原始字节（不做任何处理），供离线下载使用。 */
    suspend fun downloadBytes(url: String): ByteArray = pages.downloadBytes(url)

    /** 磁盘缓存占用（阅读页缓存 + HTTP 缓存），给「清除快取」显示用。 */
    suspend fun cacheSizeBytes(): Long = pages.cacheSizeBytes()

    /** 清空阅读页字节缓存和 HTTP 缓存（Coil 的图片缓存由 UI 层清）。 */
    suspend fun clearCaches() = pages.clearCaches()

    /** 预取下一话的前 3 页到磁盘缓存，切话时首屏不用等。 */
    suspend fun prefetchChapter(cid: Long) = pages.prefetchChapter(cid)

    /** 下载并还原一页。优先读本地下载文件，其次读页面缓存，最后走网络。 */
    suspend fun loadPage(
        cid: Long,
        page: Int,
        url: String,
        aid: Long,
        scrambleId: Long,
        targetWidth: Int,
    ): Bitmap = pages.loadPage(cid, page, url, aid, scrambleId, targetWidth)

    /** 从 /album 的 series 字段提取目录；单本作品返回空列表。 */
    fun seriesChapters(detail: AlbumDetail): List<ChapterEntry> = SeriesParser.parse(detail.series)

    // ------------------------------------------------------------ internals

    private suspend fun <T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        hosts.ensureReady()
        session.ensureSession()
        val base = hosts.apiBase ?: throw JmException.Network("线路未就绪")
        val body = try {
            jmHttp.get(base, path, params)
        } catch (e: JmException.Auth) {
            // token 失效：用记住的密码重登一次再重试
            if (session.silentLogin()) jmHttp.get(base, path, params) else throw e
        } catch (e: JmException.Network) {
            // 缓存线路失效：重新发现一次再重试
            val fresh = hosts.recoverBase()
            if (fresh != null && fresh != base) jmHttp.get(fresh, path, params) else throw e
        }
        try {
            json.decodeFromString(deserializer, body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
    }

    /** 需要登录的写接口：就绪检查 + 静默重登 + 统一取线路，返回响应体。 */
    private suspend fun authedForm(path: String, form: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            hosts.ensureReady()
            session.ensureSession()
            val base = hosts.apiBase ?: throw JmException.Network("线路未就绪")
            jmHttp.post(base, path, form)
        }

    /** 同 [authedForm]，但只判断 HTTP 状态（有些接口返回空 body）。 */
    private suspend fun authedFormRaw(path: String, form: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            hosts.ensureReady()
            session.ensureSession()
            val base = hosts.apiBase ?: throw JmException.Network("线路未就绪")
            jmHttp.postRaw(base, path, form)
        }

    private suspend fun fetchBytes(url: String): ByteArray = jmHttp.bytes(imageHosts.rewrite(url))

    companion object {
        @Volatile
        private var instance: JmRepository? = null

        /** 全局单例：OkHttp 的 Cache 要求同一目录只有一个实例，不能到处 new。 */
        fun of(context: Context): JmRepository =
            instance ?: synchronized(this) {
                instance ?: JmRepository(context.applicationContext).also { instance = it }
            }

        private const val HTTP_CACHE_BYTES = 256L * 1024 * 1024
        private const val ALBUM_TTL_MS = 60L * 1000L
        private const val READ_TTL_MS = 5L * 60L * 1000L
        private const val PROBE_TIMEOUT_MS = 2500L
    }
}
