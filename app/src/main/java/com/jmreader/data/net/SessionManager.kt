package com.jmreader.data.net

import com.jmreader.data.local.AuthStore
import com.jmreader.data.model.JmException
import com.jmreader.data.model.Member
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 登录态：密码登录、登出、JWT 过期后的静默重登。
 * 线路由 [HostManager] 提供（通过构造参数传入的两个回调），避免两个类互相持有。
 */
internal class SessionManager(
    private val auth: AuthStore,
    private val json: Json,
    private val jmHttp: JmHttp,
    private val ensureReady: suspend () -> Unit,
    private val base: () -> String?,
) {

    @Volatile
    private var loggingIn = false

    suspend fun login(username: String, password: String, remember: Boolean = true): Member =
        withContext(Dispatchers.IO) {
            ensureReady()
            val base = base() ?: throw JmException.Network("线路未就绪")
            val member = performLoginRequest(base, username, password)
            if (remember) auth.saveCredentials(username, password) else auth.clearCredentials()
            member
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady()
            base()?.let { jmHttp.post(it, "logout", emptyMap()) }
        }
        auth.clearAll()
    }

    private suspend fun performLoginRequest(base: String, username: String, password: String): Member {
        val body = jmHttp.post(base, "login", mapOf("username" to username, "password" to password))
        val member = try {
            json.decodeFromString(Member.serializer(), body)
        } catch (t: Throwable) {
            throw JmException.Parse(t)
        }
        auth.save(member)
        return member
    }

    /** token 过期但有记住的密码时，静默重新登录。 */
    suspend fun ensureSession() {
        if (auth.isValid) return
        if (auth.hasCredentials) silentLogin()
    }

    suspend fun silentLogin(): Boolean {
        if (loggingIn) return false
        val credentials = auth.credentials() ?: return false
        loggingIn = true
        return try {
            ensureReady()
            val base = base() ?: return false
            performLoginRequest(base, credentials.first, credentials.second)
            true
        } catch (_: Throwable) {
            false
        } finally {
            loggingIn = false
        }
    }
}
