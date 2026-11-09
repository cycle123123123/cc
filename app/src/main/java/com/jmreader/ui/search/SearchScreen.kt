package com.jmreader.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.components.CategoryChips
import com.jmreader.data.local.ContentFilter
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSearchBg
import com.jmreader.data.local.SearchHistory
import com.jmreader.ui.components.SortChips
import com.jmreader.data.JmRepository
import com.jmreader.data.model.AlbumBrief
import com.jmreader.core.runCatchingCancellable
import com.jmreader.ui.components.loadWorkCategories
import com.jmreader.ui.common.PageResult
import com.jmreader.ui.common.PagedList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    repo: JmRepository,
    state: AlbumQueryState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    initialQuery: String = "",
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchHistory = remember { SearchHistory.of(context) }
    val recent by searchHistory.keywords.collectAsState()
    val blockRules by ContentFilter.of(context).rules.collectAsState()

    // 状态类拿不到 repo：首次进入时懒创建，之后挂在 state 上，从详情返回复用同一份列表。
    val paged = remember(state, repo) {
        state.paged ?: PagedList<AlbumBrief>(
            keyOf = { it.id },
            fetch = { page ->
                val result = repo.search(
                    query = state.query.trim(),
                    page = page,
                    category = state.category,
                    order = state.order,
                )
                PageResult(items = result.content, total = result.total)
            },
        ).also { state.paged = it }
    }
    val albums = paged.items

    // 屏蔽过滤由 AlbumGrid 内部完成，这里只算计数，文案与数字语义保持不变。
    val hiddenCount = remember(albums, blockRules) {
        albums.count { ContentFilter.matches(it, blockRules) }
    }

    suspend fun runSearch(reset: Boolean) {
        if (state.query.isBlank()) return
        if (reset) {
            // 与原实现一致：加载中忽略重复搜索（避免两次 refresh 并发覆盖结果）。
            if (paged.loading) return
            // refresh() 取第 1 页并整体替换列表：切换关键词/分类/排序都从头开始。
            paged.refresh()
            if (paged.error == null) {
                state.searched = true
                searchHistory.add(state.query.trim())
            }
        } else {
            paged.loadMore()
        }
    }

    LaunchedEffect(Unit) {
        if (!state.started) {
            state.started = true
            if (initialQuery.isNotBlank()) {
                state.query = initialQuery
                runSearch(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.categories.isEmpty()) {
            state.categories = loadWorkCategories(repo)
        }
        if (state.hotBlocks.isEmpty()) {
            runCatchingCancellable { repo.categories() }.onSuccess { state.hotBlocks = it.blocks }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBg)
            .statusBarsPadding(),
    ) {
        // iOS App Store 搜索栏：浅灰圆角搜索框 + 右侧蓝色「取消」按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosSearchBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                ) {
                    if (state.query.isEmpty()) {
                        Text(
                            text = "搜尋作品、作者、車號…",
                            fontSize = 14.sp,
                            color = IosLabelSecondary,
                        )
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = IosLabelPrimary,
                        ),
                        cursorBrush = SolidColor(IosBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { scope.launch { runSearch(true) } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = IosLabelSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { state.query = "" },
                    )
                }
            }
            if (state.query.isNotBlank()) {
                TextButton(onClick = { scope.launch { runSearch(true) } }) {
                    Text("搜尋", color = IosBlue, fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = onBack) {
                Text("取消", color = IosBlue, fontWeight = FontWeight.Medium)
            }
        }

        if (state.searched) {
            CategoryChips(
                categories = state.categories,
                selected = state.category,
                onSelect = { slug ->
                    if (slug != state.category) {
                        state.category = slug
                        scope.launch { runSearch(true) }
                    }
                },
            )
            SortChips(
                selected = state.order,
                onSelect = { value ->
                    if (value != state.order) {
                        state.order = value
                        scope.launch { runSearch(true) }
                    }
                },
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                paged.loading && albums.isEmpty() ->
                    CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                paged.error != null && albums.isEmpty() -> ErrorState(
                    message = paged.error!!,
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { scope.launch { runSearch(true) } },
                )

                !state.searched -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (recent.isNotEmpty()) {
                        val shape = RoundedCornerShape(14.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(IosCardWhite)
                                .border(0.5.dp, Color(0x12000000), shape)
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "最近搜尋",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IosLabelPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "清空",
                                    fontSize = 13.sp,
                                    color = IosBlue,
                                    modifier = Modifier.clickable { searchHistory.clear() },
                                )
                            }
                            FlowRow(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                recent.forEach { keyword ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IosGroupedBg)
                                            .clickable {
                                                state.query = keyword
                                                scope.launch { runSearch(true) }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(keyword, fontSize = 13.sp, color = IosLabelPrimary)
                                    }
                                }
                            }
                        }
                    }

                    state.hotBlocks.forEach { block ->
                        if (block.content.isNotEmpty()) {
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(IosCardWhite)
                                    .border(0.5.dp, Color(0x12000000), shape)
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text = block.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IosLabelPrimary,
                                )
                                FlowRow(
                                    modifier = Modifier.padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    block.content.take(12).forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(IosGroupedBg)
                                                .clickable {
                                                    state.query = tag
                                                    scope.launch { runSearch(true) }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(tag, fontSize = 13.sp, color = IosBlue, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (recent.isEmpty() && state.hotBlocks.isEmpty()) {
                        Text(
                            text = "輸入關鍵字開始搜尋",
                            color = IosLabelSecondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }

                albums.isEmpty() -> Text(
                    text = "沒有找到相關作品",
                    color = IosLabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> AlbumGrid(
                    repo = repo,
                    albums = albums,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                    state = state.gridState,
                    loading = paged.loading,
                    error = paged.error,
                    hasMore = paged.hasMore,
                    onLoadMore = { scope.launch { paged.loadMore() } },
                    header = {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (hiddenCount > 0) {
                                    "共 ${paged.total} 個結果 · 已屏蔽 $hiddenCount 項"
                                } else {
                                    "共 ${paged.total} 個結果"
                                },
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                            )
                        }
                    },
                )
            }
        }
    }
}
