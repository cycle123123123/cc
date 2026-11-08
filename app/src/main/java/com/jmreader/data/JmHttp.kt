package com.jmreader.data

import com.jmreader.core.JmConfig
import com.jmreader.core.JmCrypto
import com.jmreader.core.awaitResponse
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 无状态的信封层：拼 query、加签名头、发请求、AES 解密、统一抛 [JmException]。
 *
 * 从 [JmRepository] 拆出来，仓库只负责「用哪条线路、要不要重试、怎么缓存」。
 */
internal class JmHttp(
    val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthStore,
) {

    suspend fun get(base: String, path: String, params: Map<String, String>): String {
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

    suspend fun post(base: String, path: String, form: Map<String, String>): String {
        val ts = System.currentTimeMillis() / 1000
        val request = Request.Builder()
            .url(base + path + "?lang=" + JmConfig.DEFAULT_LANG)
            .post(formBody(form))
            .withCommonHeaders(ts)
            .build()
        return execute(request, ts)
    }

    /** 有些接口返回空 body（例如 comment_vote），只判断 HTTP 状态。 */
    suspend fun postRaw(base: String, path: String, form: Map<String, String>): Boolean {
        val ts = System.currentTimeMillis() / 1000
        val request = Request.Builder()
            .url(base + path + "?lang=" + JmConfig.DEFAULT_LANG)
            .post(formBody(form))
            .withCommonHeaders(ts)
            .build()
        client.newCall(request).awaitResponse().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")
            return true
        }
    }

    /** 拉图片原始字节（不做信封解密）。 */
    suspend fun bytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Referer", JmConfig.WEB_REFERER)
            .header("User-Agent", JmConfig.USER_AGENT)
            .build()
        client.newCall(request).awaitResponse().use { response ->
            if (response.code == 401) throw JmException.Auth()
            if (!response.isSuccessful) throw JmException.Network("HTTP ${response.code}")
            return response.body?.bytes() ?: throw JmException.Network("响应为空")
        }
    }

    private fun formBody(form: Map<String, String>): MultipartBody =
        MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply { form.forEach { (key, value) -> addFormDataPart(key, value) } }
            .build()

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
        client.newCall(request).awaitResponse().use { response ->
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
}
