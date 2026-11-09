package com.jmreader.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 最近一次阅读：哪本作品、哪一话、第几页。 */
@Serializable
data class LastRead(
    val albumId: Long,
    val cid: Long,
    val title: String,
    val page: Int,
    val updatedAt: Long = 0,
)

/** 阅读历史单例，首页用它显示「繼續閱讀」，我的页用它显示本地历史。 */
class ReadingHistory private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(LastRead.serializer())

    private val _last = MutableStateFlow(loadLast())
    val last: StateFlow<LastRead?> = _last.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<LastRead>> = _history.asStateFlow()

    init {
        // 兼容旧版本：以前只存 last_read，第一次升级时把它补进历史列表（同作品不重复）。
        val latest = _last.value
        if (latest != null) {
            val existing = _history.value.firstOrNull { it.albumId == latest.albumId }
            val merged = when {
                existing == null -> (_history.value + latest)
                latest.updatedAt > existing.updatedAt -> _history.value.map {
                    if (it.albumId == latest.albumId) latest else it
                }
                else -> _history.value
            }.sortedByDescending { it.updatedAt }.take(MAX_ITEMS)
            if (merged != _history.value) {
                _history.value = merged
                persistHistory(merged)
            }
        }
    }

    fun save(albumId: Long, cid: Long, title: String, page: Int) {
        val item = LastRead(albumId, cid, title, page, System.currentTimeMillis())
        runCatching {
            prefs.edit().putString(KEY_LAST, json.encodeToString(LastRead.serializer(), item)).apply()
        }
        _last.value = item

        // 同一作品只保留最新一条，最新的排最前，最多 100 条。
        val updated = (listOf(item) + _history.value.filterNot { it.albumId == albumId })
            .take(MAX_ITEMS)
        _history.value = updated
        persistHistory(updated)
    }

    fun clear() {
        runCatching { prefs.edit().remove(KEY_LAST).remove(KEY_HISTORY).apply() }
        _last.value = null
        _history.value = emptyList()
    }

    private fun persistHistory(items: List<LastRead>) {
        runCatching {
            prefs.edit().putString(KEY_HISTORY, json.encodeToString(listSerializer, items)).apply()
        }
    }

    private fun loadLast(): LastRead? =
        prefs.getString(KEY_LAST, null)?.let {
            runCatching { json.decodeFromString(LastRead.serializer(), it) }.getOrNull()
        }

    private fun loadHistory(): List<LastRead> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull().orEmpty()
        // distinctBy 保留第一条（最新的一条），再按时间倒序兜底。
        return parsed.distinctBy { it.albumId }
            .sortedByDescending { it.updatedAt }
            .take(MAX_ITEMS)
    }

    companion object {
        private const val KEY_LAST = "last_read"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_ITEMS = 100

        @Volatile
        private var instance: ReadingHistory? = null

        fun of(context: Context): ReadingHistory =
            instance ?: synchronized(this) {
                instance ?: ReadingHistory(context.applicationContext).also { instance = it }
            }
    }
}
