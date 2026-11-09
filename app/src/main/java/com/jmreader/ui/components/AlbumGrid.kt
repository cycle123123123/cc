package com.jmreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jmreader.data.JmRepository
import com.jmreader.data.local.AlbumMetaCache
import com.jmreader.data.local.ContentFilter
import com.jmreader.data.local.metaBadge
import com.jmreader.data.local.metaTopBadge
import com.jmreader.data.model.AlbumBrief
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.OnBottomReached
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosPink

/**
 * 全站统一的 3 列作品网格：屏蔽过滤、角标、触底自动加载、底部 loading / 重试。
 * 各屏特有的顶部内容用 [header] 传入（首页的继续阅读/分区横滑、搜索计数等）。
 */
@Composable
fun AlbumGrid(
    repo: JmRepository,
    albums: List<AlbumBrief>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    loading: Boolean = false,
    error: String? = null,
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onLongClick: ((AlbumBrief) -> Unit)? = null,
    applyBlockFilter: Boolean = true,
    showMeta: Boolean = true,
    showTopBadge: Boolean = showMeta,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
    header: LazyGridScope.() -> Unit = {},
) {
    val container = LocalAppContainer.current
    val rules by container.contentFilter.rules.collectAsState()
    val shown = remember(albums, rules, applyBlockFilter) {
        if (applyBlockFilter) albums.filterNot { ContentFilter.matches(it, rules) } else albums
    }

    if (onLoadMore != null && albums.isNotEmpty()) {
        OnBottomReached(
            gridState = state,
            enabled = !loading && error == null && hasMore,
            onLoadMore = onLoadMore,
        )
    }

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        header()

        items(items = shown, key = { it.id }) { album ->
            val id = album.id
            AlbumCard(
                repo = repo,
                album = album,
                onClick = { id.toLongOrNull()?.let(onOpen) },
                onLongClick = onLongClick?.let { callback -> { callback(album) } },
                badge = if (showMeta) metaBadge(AlbumMetaCache[id]) else null,
                topBadge = if (showTopBadge) metaTopBadge(AlbumMetaCache[id]) else null,
                onVisible = if (showMeta) ({ AlbumMetaCache.request(repo, id) }) else null,
                compact = compact,
            )
        }

        if (onLoadMore != null && (loading || error != null)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                    } else {
                        Button(
                            onClick = onLoadMore,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosCardWhite,
                                contentColor = IosPink,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        ) {
                            Text("載入失敗，點擊重試", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
