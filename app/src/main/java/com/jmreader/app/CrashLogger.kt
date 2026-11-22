package com.jmreader.app

import android.content.Context
import android.os.Build

import com.jmreader.BuildConfig

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * 本機崩潰日誌。
 *
 * 沒有後端，也不需要：崩潰時把堆棧寫進 `filesDir/crash/`，
 * 在「我的 → 診斷日誌」裡可以一鍵生成報告並分享出來。
 */
object CrashLogger {

    private const val DIR = "crash"
    private const val MAX_FILES = 10

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 最近的崩潰檔案，新的在前。 */
    fun files(context: Context): List<File> =
        crashDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun clear(context: Context) {
        crashDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * 生成一份可直接分享的純文字報告：設備信息 + 全部崩潰堆棧。
     * 寫在 cacheDir/diag/ 下，通過 FileProvider 分享。
     */
    fun buildReport(context: Context): File {
        val dir = File(context.cacheDir, "diag").apply { mkdirs() }
        val out = File(dir, "jmreader-diagnostics.txt")
        out.writeText(reportText(context))
        return out
    }

    /** 報告正文（分享時直接當文本發出去，不依賴 FileProvider）。 */
    fun reportText(context: Context): String = buildString {
        appendLine("JMReader 診斷報告")
        appendLine("時間: ${stamp(System.currentTimeMillis())}")
        appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("裝置: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系統: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        val crashes = files(context)
        appendLine("崩潰日誌: ${crashes.size} 條")
        if (crashes.isEmpty()) {
            appendLine("（沒有記錄到崩潰）")
        }
        crashes.forEach { file ->
            appendLine()
            appendLine("=== ${file.name} ===")
            appendLine(runCatching { file.readText() }.getOrDefault("<讀取失敗>"))
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = crashDir(context).apply { mkdirs() }
        val name = "crash-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val text = buildString {
            appendLine("時間: ${stamp(System.currentTimeMillis())}")
            appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("裝置: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系統: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("線程: ${thread.name}")
            appendLine()
            appendLine(error.stackTraceToString())
            var cause = error.cause
            var depth = 1
            while (cause != null && depth <= 3) {
                appendLine()
                appendLine("Caused by [$depth]: ${cause.stackTraceToString()}")
                cause = cause.cause
                depth++
            }
        }
        runCatching { File(dir, name).writeText(text) }
        // 只保留最近 10 條，避免無限長大
        dir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    private fun crashDir(context: Context) =
        File(context.applicationContext.filesDir, DIR)

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
