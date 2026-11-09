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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import com.jmreader.data.JmRepository
import com.jmreader.data.local.AlbumMeta
import com.jmreader.data.local.ContentFilter
import com.jmreader.data.local.ensureAlbumMeta
import com.jmreader.data.local.metaBadge
import com.jmreader.data.local.metaTopBadge
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CategoryNode
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.userMessage
import com.jmreader.ui.common.OnBottomReached
import com.jmreader.ui.components.AlbumCard
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.components.IosFilterPill
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink

@Composable
internal fun CategoryTab(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    metas: MutableMap<String, AlbumMeta>,
    metaRequests: MutableSet<String>,
    metaGate: Semaphore,
) {
    var categories by remember { mutableStateOf<List<CategoryNode>>(emptyList()) }
    var slug by remember { mutableStateOf("") }
    var subSlug by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtersOpen by remember { mutableStateOf(true) }
    var noMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockRules by ContentFilter.of(context).rules.collectAsState()

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.categories() }
            .onSuccess { response ->
                categories = response.categories.filter { it.slug.isNotBlank() }
                slug = categories.firstOrNull()?.slug.orEmpty()
            }
            .onFailure { error = it.userMessage() }
    }

    // 首行移出视口时自动收起筛选，滚回顶部（首行可见）时自动展开
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 }
            .collect { atTop ->
                if (filtersOpen != atTop) filtersOpen = atTop
            }
    }

    suspend fun load(reset: Boolean) {
        val cValue = if (subSlug.isNotBlank()) "${slug}_$subSlug" else slug
        if (cValue.isBlank() || (loading && !reset)) return
        loading = true
        error = null
        try {
            runCatchingCancellable { repo.categoryList(cValue, order, if (reset) 1 else page) }
                .onSuccess { result ->
                    val fresh = if (reset) {
                        result.content
                    } else {
                        result.content.filter { item -> albums.none { it.id == item.id } }
                    }
                    albums = if (reset) result.content else albums + fresh
                    total = result.total
                    page = if (reset) 2 else page + 1
                    noMore = !reset && fresh.isEmpty()
                }
                .onFailure { error = it.userMessage() }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(slug) {
        subSlug = ""
    }

    LaunchedEffect(slug, subSlug, order) {
        if (slug.isNotBlank()) load(true)
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
                onClick = { scope.launch { load(true) } },
                enabled = !loading,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = IosBlue, modifier = Modifier.size(18.dp))
            }
        }

        if (error != null && albums.isNotEmpty()) {
            Text(
                text = "更新失敗：$error（點此重試）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { load(true) } }
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

        if (albums.isNotEmpty()) {
            OnBottomReached(
                gridState = gridState,
                enabled = !loading && error == null && !noMore && albums.size < total,
                onLoadMore = { scope.launch { load(false) } },
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                error != null && albums.isEmpty() -> ErrorState(
                    message = error!!,
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { scope.launch { load(true) } },
                )

                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums.filterNot { ContentFilter.matches(it, blockRules) }, key = { it.id }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            badge = metaBadge(metas[album.id]),
                            topBadge = metaTopBadge(metas[album.id]),
                            onVisible = {
                                ensureAlbumMeta(repo, scope, metaGate, metas, metaRequests, album.id)
                            },
                        )
                    }
                    if (loading || error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (loading) {
                                    CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                                } else {
                                    Button(
                                        onClick = { scope.launch { load(false) } },
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
        }
    }
}

internal val SORTS = listOf(
    "" to "最新",
    "mv" to "最多點閱",
    "mp" to "最多圖片",
    "tf" to "最多愛心",
)
