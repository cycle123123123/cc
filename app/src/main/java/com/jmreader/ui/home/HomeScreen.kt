package com.jmreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import com.jmreader.ui.components.AlbumCard
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.data.local.AlbumMetaCache
import com.jmreader.data.local.ContentFilter
import com.jmreader.data.local.metaBadge
import com.jmreader.data.local.metaTopBadge
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSearchBg
import com.jmreader.ui.theme.IosSeparator
import com.jmreader.data.local.LastRead
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.PromoteSection
import com.jmreader.data.local.ReadingHistory
import com.jmreader.core.JmConfig
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable
import com.jmreader.ui.common.PageResult
import com.jmreader.ui.common.PagedList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: JmRepository,
    state: HomeState,
    onOpen: (Long) -> Unit,
    onContinue: (LastRead) -> Unit,
    onSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jmreader", android.content.Context.MODE_PRIVATE) }
    val history = remember { ReadingHistory.of(context) }
    val lastRead by history.last.collectAsState()

    // 状态类拿不到 repo：首次进入时懒创建，之后挂在 state 上，从详情返回复用同一份列表。
    val pager = remember(state, repo) {
        state.paged ?: PagedList<AlbumBrief>(
            keyOf = { it.id },
            // latest 接口没有总数：用 Int.MAX_VALUE 占位，hasMore 只看「新页是否为空」，与原实现一致
            fetch = { page ->
                val items = repo.latest(page)
                if (page == 1) state.cached = emptyList()
                PageResult(items, Int.MAX_VALUE)
            },
        ).also { state.paged = it }
    }
    val albums = pager.items.ifEmpty { state.cached }
    val loading = pager.loading
    val error = pager.error
    val update = state.update
    var sections by remember { mutableStateOf<List<PromoteSection>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }

    val blockFilter = remember { ContentFilter.of(context) }
    val blockRules by blockFilter.rules.collectAsState()
    val shown = remember(albums, blockRules) {
        albums.filterNot { ContentFilter.matches(it, blockRules) }
    }
    val hiddenCount = albums.size - shown.size

    LaunchedEffect(shown) {
        shown.take(9).forEach { album ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(repo.coverUrl(album.id, tall = true))
                    .build(),
            )
        }
    }

    LaunchedEffect(Unit) {
        // loaded 为 true 或上次已失败时不再自动重拉（与原 loaded 语义一致）
        if (!pager.loaded && pager.error == null) {
            if (pager.items.isEmpty()) {
                repo.cachedLatest()?.let { state.cached = it }
            }
            launch { runCatchingCancellable { repo.optimizeHost() } }
            pager.refresh()
        }
    }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.settingInfo() }
            .onSuccess { setting ->
                val latest = setting.jm3Version
                val dismissed = prefs.getString("dismissed_protocol", null)
                if (latest.isNotBlank() && latest != JmConfig.PROTOCOL_VERSION && latest != dismissed) {
                    state.update = setting
                }
            }
            .onFailure { }
        runCatchingCancellable { repo.promote() }.onSuccess { sections = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBg)
            .statusBarsPadding(),
    ) {
        // iOS App Store 极简顶栏：大标题 + 浅灰搜索框
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosGroupedBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "首頁",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosSearchBg)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜尋",
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "搜尋漫畫、作者、標籤…",
                    fontSize = 14.sp,
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    runCatchingCancellable { repo.promote() }.onSuccess { sections = it }
                    pager.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                albums.isEmpty() && loading -> SkeletonGrid()

                albums.isEmpty() && error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { scope.launch { pager.refresh() } },
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("重試")
                    }
                }

                else -> AlbumGrid(
                    repo = repo,
                    albums = albums,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                    state = state.gridState,
                    loading = loading,
                    error = error,
                    hasMore = pager.hasMore,
                    onLoadMore = { scope.launch { pager.loadMore() } },
                    compact = true,
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
                ) {
                    // 首页所有非网格内容都作为整行 header：更新横幅 / 继续阅读 / 已屏蔽 / 分区横滑 / 最新上架标题
                    update?.let { setting ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "update") {
                            UpdateBanner(
                                version = setting.jm3Version,
                                onDismiss = {
                                    prefs.edit().putString("dismissed_protocol", setting.jm3Version).apply()
                                    state.update = null
                                },
                            )
                        }
                    }

                    lastRead?.let { item ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                            ContinueCard(repo = repo, item = item, onClick = { onContinue(item) })
                        }
                    }

                    if (hiddenCount > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hidden") {
                            Text(
                                text = "已依規則隱藏 $hiddenCount 部作品",
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                            )
                        }
                    }

                    sections.forEach { section ->
                        val visible = section.content.filterNot { ContentFilter.matches(it, blockRules) }
                        if (visible.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "sec_${section.id}") {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = IosSeparator.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                    Text(
                                        text = section.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IosLabelPrimary,
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(items = visible, key = { it.id }) { album ->
                                            Box(Modifier.width(108.dp)) {
                                                AlbumCard(
                                                    repo = repo,
                                                    album = album,
                                                    onClick = { album.id.toLongOrNull()?.let(onOpen) },
                                                    compact = true,
                                                    badge = metaBadge(AlbumMetaCache[album.id]),
                                                    topBadge = metaTopBadge(AlbumMetaCache[album.id]),
                                                    onVisible = { AlbumMetaCache.request(repo, album.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "latest_header") {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = IosSeparator.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Text(
                                text = "最新上架",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = IosLabelPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
