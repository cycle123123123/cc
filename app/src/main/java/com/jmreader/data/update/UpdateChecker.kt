package com.jmreader.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.jmreader.data.JmRepository
import com.jmreader.BuildConfig

/** GitHub Release 里的一条更新信息。 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/** 手动检查更新的结果：区分「有新版本」「已是最新」「检查失败」。 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String? = null) : UpdateCheckResult
}

@Serializable
private data class GhRelease(
    @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @kotlinx.serialization.SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

/**
 * 应用内更新：查 GitHub 最新的 release，和 [BuildConfig.VERSION_NAME] 比版本。
 * 结果缓存 15 分钟，避免每次启动都打一枪（未认证 API 每小时只有 60 次）。
 */
class UpdateChecker private constructor(
    private val appContext: Context,
    http: OkHttpClient,
) {

    private val http = http
    private val prefs = appContext.getSharedPreferences("jmreader_update", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** @return 有新版本时返回信息，否则 null */
    suspend fun checkIfDue(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0)
        if (!force && now - checkedAt < CHECK_TTL_MS) return@withContext cached()

        val fetched = runCatching { fetchLatest() }.getOrNull()
        if (fetched == null) {
            return@withContext cached()
        }
        store(fetched, now)
        fetched.takeIf { isNewer(it.version, currentVersion) }
    }

    /**
     * 手动检查更新：忽略 15 分钟 TTL、强制走网络、失败时不回退到过期缓存。
     * 让 UI 能区分「已是最新」和「检查失败」。
     */
    suspend fun checkManual(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val fetched = try {
            fetchLatest()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return@withContext UpdateCheckResult.Failed(e.message)
        }
        store(fetched, System.currentTimeMillis())
        if (isNewer(fetched.version, currentVersion)) {
            UpdateCheckResult.Available(fetched)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun store(fetched: UpdateInfo, now: Long) {
        prefs.edit()
            .putLong(KEY_CHECKED_AT, now)
            .putString(KEY_VERSION, fetched.version)
            .putString(KEY_NOTES, fetched.notes)
            .putString(KEY_URL, fetched.downloadUrl)
            .putLong(KEY_SIZE, fetched.sizeBytes)
            .apply()
    }

    private fun fetchLatest(): UpdateInfo {
        val request = Request.Builder()
            .url(API_URL)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "JMReader")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val release = json.decodeFromString(GhRelease.serializer(), body)
            val asset = release.assets.firstOrNull { it.name == APK_NAME }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                ?: throw IOException("Release 裡沒有 apk 安裝包")
            return UpdateInfo(
                version = release.tagName.trim().trimStart('v', 'V'),
                notes = release.body.take(500),
                downloadUrl = asset.url,
                sizeBytes = asset.size,
            )
        }
    }

    private fun cached(): UpdateInfo? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (!isNewer(version, currentVersion)) return null
        return UpdateInfo(
            version = version,
            notes = prefs.getString(KEY_NOTES, "").orEmpty(),
            downloadUrl = url,
            sizeBytes = prefs.getLong(KEY_SIZE, 0),
        )
    }

    /**
     * 下载 APK 到 cache/update。
     * 直连 GitHub 失败时依次尝试几个公共加速镜像（国内网络常见连不上 github.com）。
     */
    suspend fun download(url: String, onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (prefix in DOWNLOAD_PREFIXES) {
            try {
                return@withContext downloadFrom(prefix + url, onProgress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw (lastError as? IOException) ?: IOException(lastError?.message ?: "下载失败")
    }

    private fun downloadFrom(url: String, onProgress: (Int) -> Unit): File {
        val dir = File(appContext.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, APK_NAME)
        if (target.exists()) target.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JMReader")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应为空")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        return target
    }

    /** 拉起系统安装器（需要用户允许「安装未知应用」）。 */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }

    /**
     * 有 root 时静默安装（su + pm install）。
     * 首次会弹 Magisk/SuperSU 授权框；失败返回 false，由调用方回退到系统安装器。
     */
    fun tryRootInstall(file: File): Boolean {
        val tmp = "/data/local/tmp/jmreader-update.apk"
        val copy = runSu("cp \"${file.absolutePath}\" $tmp && chmod 644 $tmp") ?: return false
        if (copy.first != 0) return false
        val install = runSu("pm install -r $tmp", timeoutSeconds = 120) ?: return false
        runSu("rm -f $tmp")
        return install.second.contains("Success", ignoreCase = true)
    }

    /** @return (退出码, 输出)；su 不可用/超时返回 null */
    private fun runSu(command: String, timeoutSeconds: Long = 60): Pair<Int, String>? = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy()
            return null
        }
        process.exitValue() to output
    }.getOrNull()

    companion object {
        private const val API_URL = "https://api.github.com/repos/cycle123123123/cc/releases/latest"
        private const val APK_NAME = "jmreader.apk"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_VERSION = "version"
        private const val KEY_NOTES = "notes"
        private const val KEY_URL = "url"
        private const val KEY_SIZE = "size"
        private const val CHECK_TTL_MS = 15L * 60L * 1000L

        /** 直连 + 公共加速镜像；依次尝试，第一个成功即返回。 */
        private val DOWNLOAD_PREFIXES = listOf(
            "",
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
        )

        @Volatile
        private var instance: UpdateChecker? = null

        fun of(context: Context): UpdateChecker =
            instance ?: synchronized(this) {
                instance ?: UpdateChecker(
                    context.applicationContext,
                    JmRepository.of(context).okHttpClient,
                ).also { instance = it }
            }

        /** "0.2.2" > "0.2.1" */
        internal fun isNewer(latest: String, current: String): Boolean {
            val a = latest.split('.').mapNotNull { it.trim().toIntOrNull() }
            val b = current.split('.').mapNotNull { it.trim().toIntOrNull() }
            if (a.isEmpty()) return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
