package com.jmreader.data.net

import com.jmreader.data.model.JmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * 请求执行层：线路就绪 → 会话检查 → 签名请求 → 鉴权/线路失效重试 → 反序列化。
 *
 * 既实现 [ApiFetcher]（只读接口用），也提供登录后写接口的两个样板方法。
 */
internal class ApiClient(
    private val hosts: HostManager,
    private val session: SessionManager,
    private val jmHttp: JmHttp,
    private val json: Json,
) : ApiFetcher {

    override suspend fun <T> fetch(
        path: String,
        params: Map<String, String>,
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
    suspend fun authedForm(path: String, form: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            hosts.ensureReady()
            session.ensureSession()
            val base = hosts.apiBase ?: throw JmException.Network("线路未就绪")
            jmHttp.post(base, path, form)
        }

    /** 同 [authedForm]，但只判断 HTTP 状态（有些接口返回空 body）。 */
    suspend fun authedFormRaw(path: String, form: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            hosts.ensureReady()
            session.ensureSession()
            val base = hosts.apiBase ?: throw JmException.Network("线路未就绪")
            jmHttp.postRaw(base, path, form)
        }
}
