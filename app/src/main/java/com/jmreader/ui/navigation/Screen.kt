package com.jmreader.ui.navigation

import com.jmreader.ui.search.AlbumQueryState

sealed interface Screen {
    data class Search(val query: String = "", val state: AlbumQueryState = AlbumQueryState()) : Screen
    data class AuthorWorks(val name: String, val state: AlbumQueryState = AlbumQueryState()) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val aid: Long, val cid: Long, val title: String) : Screen
    data object Downloads : Screen
    data object Blocks : Screen
}

/** 底部三个 root tab，仅 AppRoot 使用。 */
internal enum class RootTab { HOME, DISCOVER, ME }
