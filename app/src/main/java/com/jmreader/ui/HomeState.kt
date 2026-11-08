package com.jmreader.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.AlbumBrief
import com.jmreader.data.AppSetting

/**
 * 首页状态放在 AppRoot 层保活，避免每次从详情返回都重新加载、丢失滚动位置。
 * 也修掉了「返回后第一次点击没反应」的问题。
 */
class HomeState {
    var albums by mutableStateOf<List<AlbumBrief>>(emptyList())
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var update by mutableStateOf<AppSetting?>(null)
    val gridState = LazyGridState()
}
