package com.jmreader.data.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import com.jmreader.data.model.AppSetting
import com.jmreader.core.JmConfig
import com.jmreader.core.JmCrypto
import com.jmreader.data.JmRepository
import com.jmreader.core.awaitResponse

/**
 * 线路探测：并发请求每个候选线路的 `/setting`，返回耗时最短的那条。
 *
 * 从 JmRepository 里拆出来，纯粹是「探测 + 择优」，不持有全局状态，
 * 便于单测和复用（启动优化、失败重试用的是同一套逻辑）。
 */
class HostBenchmark(
    private val http: OkHttpClient,
    private val json: Json,
    private val timeoutMs: Long = 2500L,
) {

    data class Probe(val base: String, val setting: AppSetting?, val ms: Long)

    /** @return 可用的最优线路，全部失败返回 null */
    suspend fun best(candidates: Collection<String>): Probe? = coroutineScope {
        candidates
            .map { base -> async(Dispatchers.IO) { probe(base) } }
            .awaitAll()
            .filter { it.setting != null }
            .minByOrNull { it.ms }
    }

    suspend fun probe(base: String): Probe {
        val started = System.currentTimeMillis()
        val setting = runCatching {
            val ts = System.currentTimeMillis() / 1000
            val request = Request.Builder()
                .url(base + "setting?lang=" + JmConfig.DEFAULT_LANG)
                .header("Token", JmCrypto.token(ts))
                .header("Tokenparam", JmCrypto.tokenParam(ts))
                .header("Accept", "application/json")
                .header("Referer", JmConfig.WEB_REFERER)
                .header("User-Agent", JmConfig.USER_AGENT)
                .build()
            val call = http.newCall(request)
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            call.awaitResponse().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val envelope = json.parseToJsonElement(body).jsonObject
                val code = envelope["code"]?.jsonPrimitive?.intOrNull ?: -1
                if (code != 200) return@use null
                val data = envelope["data"]?.jsonPrimitive?.contentOrNull ?: return@use null
                json.decodeFromString(AppSetting.serializer(), JmCrypto.decryptPayload(data, ts))
            }
        }.getOrNull()
        return Probe(base, setting, System.currentTimeMillis() - started)
    }
}
