package com.jmreader.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.components.CategoryChips
import com.jmreader.data.local.ContentFilter
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.components.SortChips
import com.jmreader.data.JmRepository
import com.jmreader.data.model.AlbumBrief
import com.jmreader.ui.components.loadWorkCategories
import com.jmreader.ui.common.PageResult
import com.jmreader.ui.common.PagedList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorWorksScreen(
    repo: JmRepository,
    authorName: String,
    state: AlbumQueryState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val container = LocalAppContainer.current
    val blockRules by container.contentFilter.rules.collectAsState()

    // 状态类拿不到 repo：首次进入时懒创建，之后挂在 state 上，从详情返回复用同一份列表。
    val paged = remember(state, repo, authorName) {
        state.paged ?: PagedList<AlbumBrief>(
            keyOf = { it.id },
            fetch = { page ->
                val result = repo.search(authorName, page, state.category, state.order)
                PageResult(items = result.content, total = result.total)
            },
        ).also { state.paged = it }
    }
    val albums = paged.items

    // 屏蔽过滤由 AlbumGrid 内部完成，这里只算计数，文案与数字语义保持不变。
    val hiddenCount = remember(albums, blockRules) {
        albums.count { ContentFilter.matches(it, blockRules) }
    }

    suspend fun load(reset: Boolean) {
        if (reset) {
            // 与原实现一致：加载中忽略重复请求（避免两次 refresh 并发覆盖结果）。
            if (paged.loading) return
            // refresh() 取第 1 页并整体替换列表：切换分类/排序都从头开始。
            paged.refresh()
        } else {
            paged.loadMore()
        }
    }

    LaunchedEffect(authorName) {
        if (!state.started) {
            state.started = true
            load(true)
        }
        if (state.categories.isEmpty()) {
            state.categories = loadWorkCategories(repo)
        }
    }

    Scaffold(
        containerColor = IosGroupedBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = authorName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBlue,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosGroupedBg,
                    titleContentColor = IosLabelPrimary,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (albums.isNotEmpty() || state.category.isNotBlank()) {
                CategoryChips(
                    categories = state.categories,
                    selected = state.category,
                    onSelect = { slug ->
                        if (slug != state.category) {
                            state.category = slug
                            scope.launch { load(true) }
                        }
                    },
                )
                SortChips(
                    selected = state.order,
                    onSelect = { value ->
                        if (value != state.order) {
                            state.order = value
                            scope.launch { load(true) }
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
                        onRetry = { scope.launch { load(true) } },
                    )

                    albums.isEmpty() -> Text(
                        text = "沒有找到作品",
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
                                        "共 ${paged.total} 部 · 已屏蔽 $hiddenCount 項"
                                    } else {
                                        "共 ${paged.total} 部"
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
}
