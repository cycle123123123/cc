package com.jmreader.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.TimeZone

/** 一天的阅读量。[day] 是本地时区的 epochDay（`millis + tzOffset` / 86400000）。 */
@Serializable
data class DayStat(
    val day: Int,
    val seconds: Long = 0L,
    val pages: Int = 0,
)

/** 对外暴露的统计快照（界面只需要读）。 */
data class ReadingStatsSnapshot(
    val totalSeconds: Long = 0L,
    val totalPages: Int = 0,
    val albumIds: Set<Long> = emptySet(),
    val days: List<DayStat> = emptyList(),
) {
    val albumCount: Int get() = albumIds.size
}

/**
 * 阅读统计：只记在本机，不上传任何数据。
 *
 * 记录口径刻意保守——只统计「真的在阅读器里停留过」的会话：
 * 打开不足 5 秒且没翻页的会被丢弃（见 ReaderScreen），避免误触把数字刷上去。
 */
class ReadingStats private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jmreader_stats", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ReadingStatsSnapshot> = _state.asStateFlow()

    /** 结束一次阅读会话。 */
    fun record(seconds: Long, pages: Int, albumId: Long) {
        if (seconds <= 0 && pages <= 0) return
        val safeSeconds = seconds.coerceAtLeast(0)
        val safePages = pages.coerceAtLeast(0)

        val cur = _state.value
        val today = todayKey()
        val days = cur.days.toMutableList()
        val index = days.indexOfFirst { it.day == today }
        if (index >= 0) {
            val old = days[index]
            days[index] = old.copy(
                seconds = old.seconds + safeSeconds,
                pages = old.pages + safePages,
            )
        } else {
            days.add(DayStat(today, safeSeconds, safePages))
        }

        val next = Persisted(
            totalSeconds = cur.totalSeconds + safeSeconds,
            totalPages = cur.totalPages + safePages,
            albumIds = (cur.albumIds + albumId).toList().takeLast(MAX_ALBUMS),
            days = days.sortedBy { it.day }.takeLast(MAX_DAYS),
        )
        _state.value = next.toSnapshot()
        persist(next)
    }

    fun reset() {
        _state.value = ReadingStatsSnapshot()
        runCatching { prefs.edit().clear().apply() }
    }

    private fun persist(value: Persisted) {
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(Persisted.serializer(), value)).apply()
        }
    }

    private fun load(): ReadingStatsSnapshot {
        val raw = prefs.getString(KEY, null) ?: return ReadingStatsSnapshot()
        val persisted = runCatching {
            json.decodeFromString(Persisted.serializer(), raw)
        }.getOrNull() ?: return ReadingStatsSnapshot()
        return persisted.toSnapshot()
    }

    @Serializable
    private data class Persisted(
        val totalSeconds: Long = 0L,
        val totalPages: Int = 0,
        val albumIds: List<Long> = emptyList(),
        val days: List<DayStat> = emptyList(),
    ) {
        fun toSnapshot() = ReadingStatsSnapshot(
            totalSeconds = totalSeconds,
            totalPages = totalPages,
            albumIds = albumIds.toSet(),
            days = days,
        )
    }

    companion object {
        private const val KEY = "stats_v1"
        private const val MAX_DAYS = 60
        private const val MAX_ALBUMS = 1000

        @Volatile
        private var instance: ReadingStats? = null

        fun of(context: Context): ReadingStats =
            instance ?: synchronized(this) {
                instance ?: ReadingStats(context.applicationContext).also { instance = it }
            }

        /** 本地时区的「今天」，用来分桶。 */
        fun todayKey(now: Long = System.currentTimeMillis()): Int {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            val offset = cal.timeZone.getOffset(now)
            return ((now + offset) / MILLIS_PER_DAY).toInt()
        }

        /** 连续阅读天数：今天没读则从昨天往前数；断一天即停。 */
        fun streak(today: Int, activeDays: Set<Int>): Int {
            var day = if (today in activeDays) today else today - 1
            var count = 0
            while (day in activeDays) {
                count++
                day--
            }
            return count
        }

        fun dayLabel(day: Int): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = day.toLong() * MILLIS_PER_DAY
            }
            return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
        }

        fun weekdayLabel(day: Int): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = day.toLong() * MILLIS_PER_DAY
            }
            return WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }

        private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
