package com.jmreader.ui.search

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryBlock
import com.jmreader.data.model.CategoryNode
import com.jmreader.ui.navigation.Screen
import com.jmreader.data.local.AlbumMeta

/**
 * 搜索页状态。挂在 overlay 栈的 [Screen.Search] 上。
 *
 * 之前状态放在 composable 里，点进详情时搜索页会离开组合（栈里只渲染最顶层），
 * 返回时重新创建 → 结果重新请求、滚动回顶部。现在状态跟 Screen 一起留在栈里，
 * 返回时直接复用，滚动位置也由 [gridState] 保留。
 */
class SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<AlbumBrief>>(emptyList())
    var total by mutableIntStateOf(0)
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var category by mutableStateOf("")

    /** 排序：""=默認，mv=最多點閱，tf=最多愛心。 */
    var order by mutableStateOf("")

    /** 首次进入是否已经处理过（防止从详情返回时重复发起首屏请求）。 */
    var started by mutableStateOf(false)

    var categories by mutableStateOf<List<CategoryNode>>(emptyList())
    var hotBlocks by mutableStateOf<List<CategoryBlock>>(emptyList())

    /** 是否还有下一页（避免「載入更多」点了没反应）。 */
    var hasMore by mutableStateOf(true)

    val gridState = LazyGridState()

    /** 点击量 / 爱心数 / 是否日文（卡片可见时按需从 /album 补）。 */
    val metas = mutableStateMapOf<String, AlbumMeta>()
    val albumRequests = mutableSetOf<String>()
}
