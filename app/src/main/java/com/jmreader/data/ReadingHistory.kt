package com.jmreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
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

/** 阅读历史单例，首页用它显示「繼續閱讀」。 */
class ReadingHistory private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _last = MutableStateFlow(load())
    val last: StateFlow<LastRead?> = _last.asStateFlow()

    fun save(albumId: Long, cid: Long, title: String, page: Int) {
        val item = LastRead(albumId, cid, title, page, System.currentTimeMillis())
        runCatching {
            prefs.edit().putString(KEY_LAST, json.encodeToString(LastRead.serializer(), item)).apply()
        }
        _last.value = item
    }

    private fun load(): LastRead? =
        prefs.getString(KEY_LAST, null)?.let {
            runCatching { json.decodeFromString(LastRead.serializer(), it) }.getOrNull()
        }

    companion object {
        private const val KEY_LAST = "last_read"

        @Volatile
        private var instance: ReadingHistory? = null

        fun of(context: Context): ReadingHistory =
            instance ?: synchronized(this) {
                instance ?: ReadingHistory(context.applicationContext).also { instance = it }
            }
    }
}
