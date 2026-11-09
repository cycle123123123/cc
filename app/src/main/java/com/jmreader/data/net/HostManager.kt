package com.jmreader.data.net

import android.content.SharedPreferences
import com.jmreader.core.JmConfig
import com.jmreader.core.JmCrypto
import com.jmreader.core.awaitResponse
import com.jmreader.data.model.AppSetting
import com.jmreader.data.model.JmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import com.jmreader.data.JmRepository

/**
 * 线路（base url）与 `/setting` 元信息：发现、缓存、切换、失败后重发现。
 * 由 [com.jmreader.data.JmRepository] 持有，对外 API 仍由它转出，行为与拆分前一致。
 */
internal class HostManager(
    private val prefs: SharedPreferences,
    private val json: Json,
    private val http: OkHttpClient,
    private val hostBenchmark: HostBenchmark,
    private val jmHttp: JmHttp,
) {

    @Volatile
    var apiBase: String? = null
        private set

    @Volatile
    var imgBase: String = JmConfig.IMG_HOST_FALLBACK
        private set

    /** 线路发现/切换要串行，用 Mutex 而不是 @Synchronized（里面会挂起）。 */
    private val initMutex = Mutex()

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
        val setting = json.decodeFromString(AppSetting.serializer(), jmHttp.get(base, "setting", emptyMap()))
        cachedSetting = setting
        cachedSettingAt = now
        if (setting.imgHost.isNotBlank()) imgBase = setting.imgHost
        prefs.edit()
            .putString(KEY_SETTING_JSON, json.encodeToString(AppSetting.serializer(), setting))
            .putLong(KEY_SETTING_AT, now)
            .apply()
        setting
    }

    /** 封面地址：接口只给 albumId，host 由当前 imgBase 决定。 */
    fun coverUrl(albumId: String, tall: Boolean = false): String {
        val suffix = if (tall) "_3x4" else ""
        return "$imgBase/media/albums/$albumId$suffix.jpg"
    }

    /** 把接口返回的 `/media/...` 相对路径拼成完整地址。 */
    fun assetUrl(path: String): String =
        if (path.startsWith("http")) path else imgBase + path

    suspend fun ensureReady() {
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
    suspend fun rediscover() {
        val candidates = LinkedHashSet<String>()
        collectNetworkHosts(candidates)
        JmConfig.BUILTIN_HOSTS.forEach { candidates.add("https://$it/") }

        val best = hostBenchmark.best(candidates) ?: throw JmException.Network("没有可用线路")
        adopt(best)
    }

    fun adopt(probe: HostBenchmark.Probe) {
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
    suspend fun recoverBase(): String? = initMutex.withLock {
        apiBase = null
        runCatching { rediscover() }
        apiBase
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
        private const val KEY_API_BASE = "api_base"
        private const val KEY_SETTING_JSON = "setting_json"
        private const val KEY_SETTING_AT = "setting_at"
        private const val KEY_BENCH_AT = "bench_at"
        private const val SETTING_TTL_MS = 10L * 60L * 1000L
        private const val BENCH_TTL_MS = 30L * 60L * 1000L
    }
}
