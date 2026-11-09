package com.jmreader.data.model

/** 统一错误模型，UI 层只区分这几类。 */
sealed class JmException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 网络不可达、超时、5xx。 */
    class Network(message: String, cause: Throwable? = null) : JmException(message, cause)

    /** 需要登录 / token 失效（HTTP 401）。 */
    class Auth(message: String = "需要登录") : JmException(message)

    /** 接口返回 code != 200。 */
    class Api(val code: Int, message: String) : JmException(message)

    /** 响应解密失败（时间戳或密钥不匹配）。 */
    class Decrypt(cause: Throwable? = null) : JmException("数据解密失败", cause)

    /** JSON 结构不符合预期。 */
    class Parse(cause: Throwable? = null) : JmException("数据解析失败", cause)
}

/** 给用户看的错误文案，避免把异常原文直接甩到界面上。 */
fun Throwable.userMessage(): String = when (this) {
    is JmException.Auth -> "登入已過期，請重新登入"
    is JmException.Network -> "網路連線失敗，請檢查網路"
    is JmException.Api -> "介面錯誤（$code）"
    is JmException.Decrypt -> "資料解析失敗"
    is JmException.Parse -> "資料格式異常"
    is java.net.UnknownHostException -> "無法連上伺服器，請檢查網路或線路"
    is java.net.SocketTimeoutException -> "連線逾時，請稍後重試"
    is java.io.IOException -> "網路連線失敗，請檢查網路"
    else -> "發生未知錯誤"
}
