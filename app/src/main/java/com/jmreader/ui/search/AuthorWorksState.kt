package com.jmreader.ui.search

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.local.AlbumMeta
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryNode

class AuthorWorksState {
    var albums by mutableStateOf<List<AlbumBrief>>(emptyList())
    var total by mutableIntStateOf(0)
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var started by mutableStateOf(false)

    var category by mutableStateOf("")
    var categories by mutableStateOf<List<CategoryNode>>(emptyList())

    var order by mutableStateOf("")

    val metas = mutableStateMapOf<String, AlbumMeta>()
    val albumRequests = mutableSetOf<String>()

    var hasMore by mutableStateOf(true)
    val gridState = LazyGridState()
}
