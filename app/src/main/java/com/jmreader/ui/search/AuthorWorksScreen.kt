package com.jmreader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import com.jmreader.data.model.AlbumBrief
import com.jmreader.ui.components.AlbumCard
import com.jmreader.ui.components.CategoryChips
import com.jmreader.data.model.CategoryNode
import com.jmreader.data.local.ContentFilter
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.ui.components.SortChips
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable
import com.jmreader.ui.components.loadWorkCategories
import com.jmreader.data.model.userMessage
import com.jmreader.ui.common.OnBottomReached
import com.jmreader.data.local.ensureAlbumMeta
import com.jmreader.data.local.metaBadge
import com.jmreader.data.local.metaTopBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorWorksScreen(
    repo: JmRepository,
    authorName: String,
    state: AuthorWorksState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockRules by ContentFilter.of(context).rules.collectAsState()
    val shown = remember(state.albums, blockRules) {
        state.albums.filterNot { ContentFilter.matches(it, blockRules) }
    }
    val hiddenCount = state.albums.size - shown.size

    suspend fun load(reset: Boolean) {
        if (state.loading) return
        state.loading = true
        state.error = null
        runCatchingCancellable { repo.search(authorName, if (reset) 1 else state.page, state.category, state.order) }
            .onSuccess { result ->
                val fresh = result.content.filter { new -> state.albums.none { it.id == new.id } }
                state.albums = if (reset) result.content else state.albums + fresh
                state.total = result.total
                state.page = if (reset) 2 else state.page + 1
                state.hasMore = if (reset) {
                    result.content.isNotEmpty() && result.content.size < result.total
                } else {
                    fresh.isNotEmpty() && state.albums.size < result.total
                }
            }
            .onFailure { state.error = it.userMessage() }
        state.loading = false
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

    val viewGate = remember { Semaphore(2) }

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
            if (state.albums.isNotEmpty() || state.category.isNotBlank()) {
                CategoryChips(
                    categories = state.categories,
                    selected = state.category,
                    onSelect = { slug ->
                        if (slug != state.category) {
                            state.category = slug
                            state.hasMore = true
                            scope.launch { load(true) }
                        }
                    },
                )
                SortChips(
                    selected = state.order,
                    onSelect = { value ->
                        if (value != state.order) {
                            state.order = value
                            state.hasMore = true
                            scope.launch { load(true) }
                        }
                    },
                )
            }
            if (state.albums.isNotEmpty()) {
                OnBottomReached(
                    gridState = state.gridState,
                    enabled = !state.loading && state.error == null && state.hasMore,
                    onLoadMore = { scope.launch { load(false) } },
                )
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading && state.albums.isEmpty() ->
                        CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                    state.error != null && state.albums.isEmpty() -> ErrorState(
                        message = state.error!!,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = { scope.launch { load(true) } },
                    )

                    state.albums.isEmpty() -> Text(
                        text = "沒有找到作品",
                        color = IosLabelSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LazyVerticalGrid(
                        state = state.gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (hiddenCount > 0) {
                                    "共 ${state.total} 部 · 已屏蔽 $hiddenCount 項"
                                } else {
                                    "共 ${state.total} 部"
                                },
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                            )
                        }
                        items(items = shown, key = { it.id }) { album ->
                            val id = album.id
                            AlbumCard(
                                repo = repo,
                                album = album,
                                onClick = { album.id.toLongOrNull()?.let(onOpen) },
                                badge = metaBadge(state.metas[id]),
                                topBadge = metaTopBadge(state.metas[id]),
                                onVisible = {
                                    ensureAlbumMeta(repo, scope, viewGate, state.metas, state.albumRequests, id)
                                },
                            )
                        }
                        if (state.loading || state.error != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (state.loading) {
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
}
