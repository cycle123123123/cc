package com.jmreader.ui.discover

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryBlock
import com.jmreader.data.model.CategoryNode
import com.jmreader.data.model.WeekOptions
import com.jmreader.ui.common.PagedList

/**
 * 發現頁的保活狀態，掛在 [com.jmreader.ui.navigation.AppRoot] 上。
 *
 * 只保存「界面/数据」本身，不持有 repo；PagedList 在首次进入时懒创建，
 * 之后切换底部 tab 复用同一份列表、筛选条件和滚动位置。
 */
class DiscoverState {
    val tabIndex = mutableIntStateOf(0)

    val category = CategoryState()
    val tag = TagState()
    val week = WeekState()
    val recommend = RecommendState()
}

/** 分類 tab：筛选条件 + 分页列表 + 滚动位置。 */
class CategoryState {
    val categories = mutableStateOf<List<CategoryNode>>(emptyList())
    val slug = mutableStateOf("")
    val subSlug = mutableStateOf("")
    val order = mutableStateOf("")
    val filtersOpen = mutableStateOf(true)
    val loadError = mutableStateOf<String?>(null)
    val gridState = LazyGridState()

    /** 上一次成功拉到分类树的时间。 */
    var categoriesAt: Long = 0L

    var paged: PagedList<AlbumBrief>? = null

    /** 上一次真正发起刷新的筛选组合（slug|subSlug|order）。 */
    var lastFilterKey: String = ""

    /** 上一次处理过「切换主分类时清空子分类」的 slug。 */
    var lastSlugForSub: String = ""

    /** 上一次成功刷新第一页的时间；0 表示从未加载。 */
    var loadedAt: Long = 0L
}

/** 標籤 tab：静态分组，只在过期时重拉一次。 */
class TagState {
    val blocks = mutableStateOf<List<CategoryBlock>>(emptyList())
    val loading = mutableStateOf(true)
    var loadedAt: Long = 0L
}

/** 週榜 tab：期数/类型选择 + 列表。 */
class WeekState {
    val options = mutableStateOf<WeekOptions?>(null)
    val type = mutableStateOf("")
    val period = mutableStateOf("")
    val albums = mutableStateOf<List<AlbumBrief>>(emptyList())
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val reloadKey = mutableIntStateOf(0)

    /** 上一次成功拉到期数/类型选项的时间。 */
    var optionsAt: Long = 0L

    /** 上一次真正发起列表请求的 type|period|reloadKey。 */
    var lastRequestKey: String = ""

    var loadedAt: Long = 0L
}

/** 推薦 tab：随机推荐列表。 */
class RecommendState {
    val albums = mutableStateOf<List<AlbumBrief>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    var loadedAt: Long = 0L
}
