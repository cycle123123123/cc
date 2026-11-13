package com.jmreader.ui.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.FolderItem
import com.jmreader.ui.common.PagedList

/**
 * 「我的」页保活状态，挂在 [com.jmreader.ui.navigation.AppRoot] 上。
 *
 * 切走再切回时保留：当前内部 tab、搜索词、网格滚动位置、收藏夹选择，
 * 以及收藏/云端历史两条分页流水线（不再每次重新请求）。
 */
class LibraryState {
    val tab = mutableIntStateOf(0)
    val query = mutableStateOf("")
    val gridState = LazyGridState()
    val searchOpen = mutableStateOf(false)

    val folders = mutableStateOf<List<FolderItem>>(emptyList())
    val folderId = mutableStateOf("")
    val loadedFolder = mutableStateOf<String?>(null)

    var favoritesPaged: PagedList<AlbumBrief>? = null
    var historyPaged: PagedList<AlbumBrief>? = null

    val showFolders = mutableStateOf(false)
    val newFolderName = mutableStateOf("")
    val renameTarget = mutableStateOf<FolderItem?>(null)
    val renameText = mutableStateOf("")
    val moveTarget = mutableStateOf<AlbumBrief?>(null)
    val showClearHistory = mutableStateOf(false)

    /** 上一次成功刷新收藏 / 历史的时间；0 表示从未加载。 */
    var favoritesAt: Long = 0L
    var historyAt: Long = 0L

    /** 已经处理过「切 interior tab 时回到顶部」的 tab，避免返回本页时被再次重置。 */
    var scrolledTab: Int = -1

    /** 当前数据所属账号；换账号时清空上一个人的收藏/历史缓存。 */
    var uid: String = ""
}
