package com.jmreader.data

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
