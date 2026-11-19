package com.jmreader.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本機備份內容。
 *
 * 只包含「用戶自己產生、丟了會心疼」的數據：閱讀歷史、屏蔽規則、閱讀統計、
 * 搜索歷史、閱讀進度與書籤。
 *
 * **不含**帳號密碼、Cookie、JWT 等敏感憑據（`AuthStore` / `SecureStore` 一律不碰）。
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val history: List<LastRead> = emptyList(),
    val blocks: BlockRules = BlockRules(),
    val stats: ReadingStatsSnapshot = ReadingStatsSnapshot(),
    val searchHistory: List<String> = emptyList(),
    val progress: Map<String, Int> = emptyMap(),
    val bookmarks: Map<String, Int> = emptyMap(),
)

/**
 * 備份/恢復的工具：純 JSON 讀寫，不碰文件系統（由 UI 層的 SAF 負責選路徑）。
 * 拆成靜態方法，方便 JVM 單測直接做序列化往返。
 */
object BackupManager {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

    fun decode(text: String): BackupData = json.decodeFromString(BackupData.serializer(), text)

    /** 收集當前狀態（UI 層負責把 container 裡的 store 傳進來）。 */
    fun collect(
        history: ReadingHistory,
        filter: ContentFilter,
        stats: ReadingStats,
        search: SearchHistory,
        reader: ReaderPrefs,
    ): BackupData = BackupData(
        exportedAt = System.currentTimeMillis(),
        history = history.snapshot(),
        blocks = filter.rules.value,
        stats = stats.state.value,
        searchHistory = search.keywords.value,
        progress = reader.exportProgress(),
        bookmarks = reader.exportBookmarks(),
    )

    /**
     * 把備份寫回各個 store，並同步它們的 StateFlow。
     * 返回成功寫入的條目數（歷史 + 進度 + 書籤 + 屏蔽規則），僅用於提示。
     */
    fun apply(
        data: BackupData,
        history: ReadingHistory,
        filter: ContentFilter,
        stats: ReadingStats,
        search: SearchHistory,
        reader: ReaderPrefs,
    ): Int {
        require(data.version <= 1) { "備份版本過新（v${data.version}），請升級 App" }
        history.restore(data.history)
        filter.restore(data.blocks)
        stats.restore(data.stats)
        search.restore(data.searchHistory)
        reader.restoreProgress(data.progress, data.bookmarks)
        return data.history.size + data.progress.size + data.bookmarks.size
    }
}
