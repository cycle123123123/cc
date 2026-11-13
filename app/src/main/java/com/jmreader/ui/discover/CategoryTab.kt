package com.jmreader.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jmreader.data.JmRepository
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryNode
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.userMessage
import com.jmreader.ui.common.PageResult
import com.jmreader.ui.common.PagedList
import com.jmreader.ui.common.RefreshPolicy
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.components.IosFilterPill
import com.jmreader.ui.navigation.BottomBarMetrics
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosLabelSecondary

@Composable
internal fun CategoryTab(
    repo: JmRepository,
    state: CategoryState,
    onOpen: (Long) -> Unit,
) {
    var categories by state.categories
    var slug by state.slug
    var subSlug by state.subSlug
    var order by state.order
    var filtersOpen by state.filtersOpen
    var loadError by state.loadError
    val gridState = state.gridState
    val scope = rememberCoroutineScope()

    val pager = remember(repo) {
        state.paged ?: PagedList<AlbumBrief>(
            keyOf = { it.id },
            fetch = { page ->
                val cValue = if (subSlug.isNotBlank()) "${slug}_$subSlug" else slug
                if (cValue.isBlank()) {
                    PageResult(emptyList(), 0)
                } else {
                    val result = repo.categoryList(cValue, order, page)
                    PageResult(result.content, result.total)
                }
            },
        ).also { state.paged = it }
    }
    val albums = pager.items
    val total = pager.total
    val loading = pager.loading
    val error = pager.error

    // 手动刷新（按钮/失败重试）：立即请求并刷新保鲜时间戳
    suspend fun refreshNow() {
        pager.refresh()
        if (pager.error == null) {
            state.loadedAt = System.currentTimeMillis()
            state.lastFilterKey = "$slug|$subSlug|$order"
        }
    }

    LaunchedEffect(Unit) {
        val stale = RefreshPolicy.isStale(
            state.categoriesAt,
            System.currentTimeMillis(),
            RefreshPolicy.ListTtlMs,
        )
        if (categories.isNotEmpty() && !stale) return@LaunchedEffect
        runCatchingCancellable { repo.categories() }
            .onSuccess { response ->
                categories = response.categories.filter { it.slug.isNotBlank() }
                state.categoriesAt = System.currentTimeMillis()
                if (slug.isBlank()) slug = categories.firstOrNull()?.slug.orEmpty()
            }
            .onFailure { loadError = it.userMessage() }
    }

    // 首行移出视口时自动收起筛选，滚回顶部（首行可见）时自动展开
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 }
            .collect { atTop ->
                if (filtersOpen != atTop) filtersOpen = atTop
            }
    }

    // 只在真的切换主分类时清空子分类；从别的 tab 切回来时保留用户的选择
    LaunchedEffect(slug) {
        if (state.lastSlugForSub != slug) {
            state.lastSlugForSub = slug
            subSlug = ""
        }
    }

    // 只有筛选条件真的变了、首次进入、或数据过期时才请求；
    // 单纯从别的 tab 切回来不重复拉取。
    LaunchedEffect(slug, subSlug, order) {
        if (slug.isBlank()) return@LaunchedEffect
        val key = "$slug|$subSlug|$order"
        val stale = RefreshPolicy.isStale(
            state.loadedAt,
            System.currentTimeMillis(),
            RefreshPolicy.ListTtlMs,
        )
        val filterChanged = state.lastFilterKey != key
        if (!filterChanged && pager.loaded && !stale) return@LaunchedEffect
        state.lastFilterKey = key
        pager.refresh()
        if (pager.error == null) state.loadedAt = System.currentTimeMillis()
    }

    Column(Modifier.fillMaxSize()) {
        val subCategories = categories.firstOrNull { it.slug == slug }?.subCategories.orEmpty()
        val summary = buildList {
            categories.firstOrNull { it.slug == slug }?.name?.let(::add)
            subCategories.firstOrNull { it.slug == subSlug }?.name?.let(::add)
            SORTS.firstOrNull { it.first == order }?.second?.let(::add)
            if (total > 0) add("共 $total 部")
        }.joinToString(" · ")

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                fontSize = 12.sp,
                color = IosLabelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { refreshNow() } },
                enabled = !loading,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = IosBlue, modifier = Modifier.size(18.dp))
            }
        }

        val failure = error ?: loadError
        if (failure != null && albums.isNotEmpty()) {
            Text(
                text = "更新失敗：$failure（點此重試）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { refreshNow() } }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        AnimatedVisibility(
            visible = filtersOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = categories, key = { it.slug }) { item ->
                            IosFilterPill(
                                selected = slug == item.slug,
                                label = item.name,
                                onClick = { slug = item.slug },
                            )
                        }
                    }
                }

                if (subCategories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            IosFilterPill(
                                selected = subSlug.isBlank(),
                                label = "全部",
                                onClick = { subSlug = "" },
                            )
                        }
                        items(items = subCategories, key = { it.cid }) { sub ->
                            IosFilterPill(
                                selected = subSlug == sub.slug,
                                label = sub.name,
                                onClick = { subSlug = sub.slug },
                            )
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = SORTS, key = { it.first }) { sort ->
                        IosFilterPill(
                            selected = order == sort.first,
                            label = sort.second,
                            onClick = { order = sort.first },
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                failure != null && albums.isEmpty() -> ErrorState(
                    message = failure,
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { scope.launch { refreshNow() } },
                )

                else -> AlbumGrid(
                    repo = repo,
                    albums = albums,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    loading = loading,
                    error = error,
                    hasMore = pager.hasMore,
                    onLoadMore = { scope.launch { pager.loadMore() } },
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = BottomBarMetrics.ContentInset),
                )
            }
        }
    }
}

internal val SORTS = listOf(
    "" to "最新",
    "mv" to "最多點閱",
    "mp" to "最多圖片",
    "tf" to "最多愛心",
)
