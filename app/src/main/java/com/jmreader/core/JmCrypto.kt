package com.jmreader.core

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okio.ByteString.Companion.decodeBase64

/**
 * 官方客户端 v2.1.13 的请求签名与响应解密。
 * 详见 docs/API.md §3、§4。
 *
 * 只依赖 JVM + okio，便于在 JVM 单元测试里直接验证。
 */
object JmCrypto {

    fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) sb.append('0')
            sb.append(Integer.toHexString(value))
        }
        return sb.toString()
    }

    /** 请求头 Token 的值：md5("{ts}{TOKEN_SECRET}")。 */
    fun token(ts: Long): String = md5(ts.toString() + JmConfig.TOKEN_SECRET)

    /** 请求头 Tokenparam 的值："{ts},{APP_VERSION}"。 */
    fun tokenParam(ts: Long): String = "$ts,${JmConfig.APP_VERSION}"

    /** 解密 {code,data} 信封里的 data。 */
    fun decryptPayload(data: String, ts: Long): String {
        val raw = decodeBase64(data)
        var lastError: Throwable? = null
        for (secret in arrayOf(JmConfig.TOKEN_SECRET, JmConfig.CONTENT_SECRET)) {
            try {
                return aesEcbDecrypt(raw, md5(ts.toString() + secret))
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("响应解密失败", lastError)
    }

    /** 解密线路清单 txt。 */
    fun decryptHostList(payload: String): String =
        aesEcbDecrypt(decodeBase64(payload), md5(JmConfig.HOST_LIST_KEY))

    private fun decodeBase64(text: String): ByteArray {
        val compact = text.filterNot { it.isWhitespace() }
        return (compact.decodeBase64() ?: throw IllegalArgumentException("Base64 解码失败")).toByteArray()
    }

    private fun aesEcbDecrypt(data: ByteArray, keyHex: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyHex.toByteArray(Charsets.UTF_8), "AES"))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
