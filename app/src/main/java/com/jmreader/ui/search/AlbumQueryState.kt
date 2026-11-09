package com.jmreader.ui.search

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryBlock
import com.jmreader.data.model.CategoryNode
import com.jmreader.ui.common.PagedList

/**
 * 搜索页与作者页共用的「按条件查作品」状态。挂在 overlay 栈的 [com.jmreader.ui.navigation.Screen.Search]
 * / [com.jmreader.ui.navigation.Screen.AuthorWorks] 上。
 *
 * 之前状态放在 composable 里，点进详情时列表页会离开组合（栈里只渲染最顶层），
 * 返回时重新创建 → 结果重新请求、滚动回顶部。现在状态跟 Screen 一起留在栈里，
 * 返回时直接复用，滚动位置也由 [gridState] 保留。
 *
 * 分页数据由 [paged] 提供；它同样挂在这里，所以返回时列表不会重拉。
 */
class AlbumQueryState {
    /** 搜索关键词（作者页不用）。 */
    var query by mutableStateOf("")

    /** 分类 slug，""=全部。 */
    var category by mutableStateOf("")

    /** 排序：""=默認，mv=最多點閱，tf=最多愛心。 */
    var order by mutableStateOf("")

    var categories by mutableStateOf<List<CategoryNode>>(emptyList())
    var hotBlocks by mutableStateOf<List<CategoryBlock>>(emptyList())

    /** 是否已经成功搜过一次（决定显示分类/排序行还是搜索首页）。 */
    var searched by mutableStateOf(false)

    /** 首次进入是否已经处理过（防止从详情返回时重复发起首屏请求）。 */
    var started by mutableStateOf(false)

    val gridState = LazyGridState()

    /**
     * 分页数据。状态类拿不到 repo，由屏幕在首次进入时懒创建并挂回这里
     * （`state.paged ?: PagedList(...).also { state.paged = it }`）。
     */
    var paged: PagedList<AlbumBrief>? = null
}
