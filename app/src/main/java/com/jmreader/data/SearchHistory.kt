package com.jmreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 最近搜索关键词，最多保留 10 条，新的排前面。 */
class SearchHistory private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_search", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _keywords = MutableStateFlow(load())
    val keywords: StateFlow<List<String>> = _keywords.asStateFlow()

    fun add(keyword: String) {
        val value = keyword.trim()
        if (value.isEmpty()) return
        val next = (listOf(value) + _keywords.value.filterNot { it.equals(value, true) }).take(MAX)
        persist(next)
    }

    fun remove(keyword: String) {
        persist(_keywords.value - keyword)
    }

    fun clear() {
        persist(emptyList())
    }

    private fun persist(value: List<String>) {
        runCatching {
            prefs.edit()
                .putString(KEY_LIST, json.encodeToString(ListSerializer(String.serializer()), value))
                .apply()
        }
        _keywords.value = value
    }

    private fun load(): List<String> =
        prefs.getString(KEY_LIST, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        }.orEmpty()

    companion object {
        private const val KEY_LIST = "keywords"
        private const val MAX = 10

        @Volatile
        private var instance: SearchHistory? = null

        fun of(context: Context): SearchHistory =
            instance ?: synchronized(this) {
                instance ?: SearchHistory(context.applicationContext).also { instance = it }
            }
    }
}
