package com.jmreader.ui.common

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 通用触底加载：列表滚到接近底部时回调一次，[enabled] 为 false（加载中/没有更多/出错）时不触发。
 */
@Composable
fun OnBottomReached(
    gridState: LazyGridState,
    buffer: Int = 6,
    enabled: Boolean = true,
    onLoadMore: () -> Unit,
) {
    val nearBottom by remember(gridState, buffer) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) {
                false
            } else {
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 1 - buffer
            }
        }
    }
    LaunchedEffect(gridState, enabled) {
        snapshotFlow { nearBottom }
            .distinctUntilChanged()
            .collect { reached -> if (reached && enabled) onLoadMore() }
    }
}
