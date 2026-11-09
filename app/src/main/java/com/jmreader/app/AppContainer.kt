package com.jmreader.app

import android.content.Context
import com.jmreader.data.JmRepository
import com.jmreader.data.download.DownloadRepository
import com.jmreader.data.local.AlbumMetaCache
import com.jmreader.data.local.AppPrefs
import com.jmreader.data.local.ContentFilter
import com.jmreader.data.local.ReaderPrefs
import com.jmreader.data.local.ReadingHistory
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

    val searchHistory: SearchHistory = SearchHistory.of(context)

    val updateChecker: UpdateChecker = UpdateChecker.of(context)

    val readerPrefs: ReaderPrefs = ReaderPrefs.of(context)

    val appPrefs: AppPrefs = AppPrefs.of(context)

    /** 全局卡片角标缓存（object，本身即单例）。 */
    val albumMetaCache: AlbumMetaCache = AlbumMetaCache
}
