package com.jmreader.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CategoryBlock
import com.jmreader.data.CategoryNode

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

    /** 首次进入是否已经处理过（防止从详情返回时重复发起首屏请求）。 */
    var started by mutableStateOf(false)

    var categories by mutableStateOf<List<CategoryNode>>(emptyList())
    var hotBlocks by mutableStateOf<List<CategoryBlock>>(emptyList())

    val gridState = LazyGridState()
    val views = mutableStateMapOf<String, Int>()
    val viewRequests = mutableSetOf<String>()
}
