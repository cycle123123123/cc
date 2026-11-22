package com.jmreader.app

import android.content.Context

import com.jmreader.data.FavoriteWatch
import com.jmreader.data.JmRepository
import com.jmreader.data.download.DownloadRepository
import com.jmreader.data.local.AlbumMetaCache
import com.jmreader.data.local.AppPrefs
import com.jmreader.data.local.BackupManager
import com.jmreader.data.local.ContentFilter
import com.jmreader.data.local.ReaderPrefs
import com.jmreader.data.local.ReadingHistory
import com.jmreader.data.local.ReadingStats
import com.jmreader.data.local.SearchHistory
import com.jmreader.data.update.UpdateChecker
/**
 * 应用级单例集合。由 [JmApplication] 创建，UI 通过
 * `com.jmreader.ui.common.LocalAppContainer` 取用。
 *
 * 这些 `X.of(...)` 仍然保留（非 Compose 场景可用），这里只是把同一批实例集中起来，
 * 保证每个单例只有一份状态。
 */
class AppContainer(context: Context) {

    val repository: JmRepository = JmRepository.of(context)

    val contentFilter: ContentFilter = ContentFilter.of(context)

    val downloads: DownloadRepository = DownloadRepository.of(context)

    val readingHistory: ReadingHistory = ReadingHistory.of(context)

    val readingStats: ReadingStats = ReadingStats.of(context)

    /** 收藏追更（只查韓漫連載）。 */
    val favoriteWatch: FavoriteWatch = FavoriteWatch.of(context)

    val searchHistory: SearchHistory = SearchHistory.of(context)

    val updateChecker: UpdateChecker = UpdateChecker.of(context)

    val readerPrefs: ReaderPrefs = ReaderPrefs.of(context)

    val appPrefs: AppPrefs = AppPrefs.of(context)

    /** 全局卡片角标缓存（object，本身即单例）。 */
    val albumMetaCache: AlbumMetaCache = AlbumMetaCache

    /**
     * 備份/恢復的門面：UI 只負責選檔案路徑和讀寫文件，
     * 不再逐個觸碰 ReadingHistory / ContentFilter / ReadingStats 等 store。
     */
    fun exportBackupJson(): String = BackupManager.encode(
        BackupManager.collect(
            history = readingHistory,
            filter = contentFilter,
            stats = readingStats,
            search = searchHistory,
            reader = readerPrefs,
        ),
    )

    /** @return 恢復的條目數（歷史 + 進度 + 書籤） */
    fun restoreBackupJson(text: String): Int = BackupManager.apply(
        data = BackupManager.decode(text),
        history = readingHistory,
        filter = contentFilter,
        stats = readingStats,
        search = searchHistory,
        reader = readerPrefs,
    )
}
