package com.jmreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登录态存储。官方 token 有效期按 1 小时处理（前端 authExpiry = now + 1h）。
 * 这里只存 jwt / session / 展示信息，不保存密码。
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_auth", Context.MODE_PRIVATE)

    private val _member = MutableStateFlow(load())
    val member: StateFlow<Member?> = _member.asStateFlow()

    val jwt: String? get() = prefs.getString(KEY_JWT, null)
    val session: String? get() = prefs.getString(KEY_SESSION, null)

    fun save(member: Member) {
        prefs.edit()
            .putString(KEY_JWT, member.jwttoken)
            .putString(KEY_SESSION, member.s)
            .putString(KEY_JSON, memberJson(member))
            .putLong(KEY_EXPIRY, System.currentTimeMillis() + TOKEN_TTL_MS)
            .apply()
        _member.value = member
    }

    fun clear() {
        prefs.edit().clear().apply()
        _member.value = null
    }

    private fun load(): Member? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        val expiry = prefs.getLong(KEY_EXPIRY, 0)
        if (expiry in 1 until System.currentTimeMillis()) {
            prefs.edit().clear().apply()
            return null
        }
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
        const val TOKEN_TTL_MS = 60L * 60L * 1000L
    }
}
