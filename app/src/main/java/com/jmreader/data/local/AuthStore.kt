package com.jmreader.data.local

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.jmreader.data.model.Member
import com.jmreader.data.JmRepository

/**
 * 登录态存储。
 * - jwt / session：有效期按 1 小时算，过期后由 JmRepository 用记住的账号密码自动重新登录
 * - 密码用 Android Keystore 加密后保存，不落明文
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_auth", Context.MODE_PRIVATE)

    private val _member = MutableStateFlow(loadMember())
    val member: StateFlow<Member?> = _member.asStateFlow()

    val isValid: Boolean
        get() = prefs.getLong(KEY_EXPIRY, 0) > System.currentTimeMillis() &&
            !prefs.getString(KEY_JWT, null).isNullOrBlank()

    val jwt: String? get() = if (isValid) prefs.getString(KEY_JWT, null) else null
    val session: String? get() = if (isValid) prefs.getString(KEY_SESSION, null) else null

    val hasCredentials: Boolean
        get() = !prefs.getString(KEY_USER, null).isNullOrBlank() &&
            !prefs.getString(KEY_PASS, null).isNullOrBlank()

    fun save(member: Member) {
        prefs.edit()
            .putString(KEY_JWT, member.jwttoken)
            .putString(KEY_SESSION, member.s)
            .putString(KEY_JSON, memberJson(member))
            .putLong(KEY_EXPIRY, expiryOf(member))
            .apply()
        _member.value = member
    }

    /** JWT 自带 exp（实测约一年），优先用它；解析失败退回 1 小时。 */
    private fun expiryOf(member: Member): Long {
        val exp = runCatching {
            val payload = member.jwttoken.split(".").getOrNull(1) ?: return@runCatching null
            val decoded = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
            Regex("\"exp\"\\s*:\\s*(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull()
        }.getOrNull()

        return if (exp != null && exp > 0) exp * 1000L - 60_000L else System.currentTimeMillis() + TOKEN_TTL_MS
    }

    fun saveCredentials(username: String, password: String) {
        val encrypted = SecureStore.encrypt(password) ?: return
        prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, encrypted)
            .apply()
    }

    fun credentials(): Pair<String, String>? {
        val username = prefs.getString(KEY_USER, null) ?: return null
        val encrypted = prefs.getString(KEY_PASS, null) ?: return null
        val password = SecureStore.decrypt(encrypted) ?: return null
        return username to password
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASS).apply()
    }

    /** 只清 jwt，保留记住的账号密码（用于 token 过期后的自动登录）。 */
    fun clearMember() {
        prefs.edit()
            .remove(KEY_JWT)
            .remove(KEY_SESSION)
            .remove(KEY_JSON)
            .remove(KEY_EXPIRY)
            .apply()
        _member.value = null
    }

    /** 退出登录：jwt 和记住的密码都清掉。 */
    fun clearAll() {
        prefs.edit().clear().apply()
        _member.value = null
    }

    private fun loadMember(): Member? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        if (prefs.getLong(KEY_EXPIRY, 0) <= System.currentTimeMillis()) return null

        return runCatching {
            Member(
                uid = raw.substringAfter("uid=").substringBefore(";"),
                username = raw.substringAfter("username=").substringBefore(";"),
                email = raw.substringAfter("email=").substringBefore(";"),
                s = prefs.getString(KEY_SESSION, "").orEmpty(),
                jwttoken = prefs.getString(KEY_JWT, "").orEmpty(),
                totalFavorites = raw.substringAfter("favorites=").substringBefore(";").toIntOrNull() ?: 0,
                level = raw.substringAfter("level=").substringBefore(";").toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    private fun memberJson(member: Member): String =
        "uid=${member.uid};username=${member.username};email=${member.email};" +
            "favorites=${member.totalFavorites};level=${member.level}"

    private companion object {
        const val KEY_JWT = "jwt"
        const val KEY_SESSION = "session"
        const val KEY_JSON = "member"
        const val KEY_EXPIRY = "expiry"
        const val KEY_USER = "saved_user"
        const val KEY_PASS = "saved_pass"
        const val TOKEN_TTL_MS = 60L * 60L * 1000L
    }
}
