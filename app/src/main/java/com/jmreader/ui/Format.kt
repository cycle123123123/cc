package com.jmreader.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 6128 -> "6128"，12345 -> "1.2 萬"，123456789 -> "1.2 億"。 */
internal fun formatCount(value: Int): String = when {
    value >= 100_000_000 -> String.format(Locale.US, "%.1f 億", value / 100_000_000.0)
    value >= 10_000 -> String.format(Locale.US, "%.1f 萬", value / 10_000.0)
    else -> value.toString()
}

/** 接口的 addtime 是秒级时间戳字符串，转成 yyyy-MM-dd；解析失败原样返回。 */
internal fun formatEpochSeconds(raw: String): String {
    val seconds = raw.toLongOrNull() ?: return raw
    if (seconds <= 0L) return raw
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000L))
    }.getOrDefault(raw)
}
