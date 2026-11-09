package com.jmreader.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 接口的 addtime 是秒级时间戳字符串，转成 yyyy-MM-dd；解析失败原样返回。 */
internal fun formatEpochSeconds(raw: String): String {
    val seconds = raw.toLongOrNull() ?: return raw
    if (seconds <= 0L) return raw
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000L))
    }.getOrDefault(raw)
}

internal fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
