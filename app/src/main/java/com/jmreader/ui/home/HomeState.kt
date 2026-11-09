package com.jmreader.ui.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.model.AlbumBrief
import com.jmreader.ui.common.PagedList
import com.jmreader.data.model.AppSetting

/**
 * 首页状态放在 AppRoot 层保活，避免每次从详情返回都重新加载、丢失滚动位置。
 * 也修掉了「返回后第一次点击没反应」的问题。
 */
class HomeState {
    var update by mutableStateOf<AppSetting?>(null)
    val gridState = LazyGridState()

    /** 「最新上架」的分页状态：实例常驻（HomeState 在 AppRoot 保活），首次进入时懒创建。 */
    var paged: PagedList<AlbumBrief>? = null

    /** 首屏请求返回前的磁盘缓存占位（[PagedList] 不对外暴露 items 写入）。 */
    var cached by mutableStateOf<List<AlbumBrief>>(emptyList())
}
