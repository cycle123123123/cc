package com.jmreader.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch

/**
 * 作者页状态。和 [SearchState] 一样挂在 overlay 栈上，
 * 进作品详情再返回时保留列表和滚动位置。
 */
class AuthorWorksState {
    var albums by mutableStateOf<List<AlbumBrief>>(emptyList())
    var total by mutableIntStateOf(0)
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var started by mutableStateOf(false)

    /** 是否还有下一页。用「已加载条数」判断，不能用屏蔽后的数量，否则会一直显示「載入更多」却加不出东西。 */
    var hasMore by mutableStateOf(true)
    val gridState = LazyGridState()
}

/**
 * 作者作品页。
 *
 * 接口只有 `creator_work?author_id=`，但作品详情里的 `author` 只有名字、没有 id；
 * 而 `/search?search_query=<名字>` 实测能命中该作者的作品（作者名通常也在标题里），
 * 所以这里用搜索兜底，标题显示作者名。
 */
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
        runCatchingCancellable { repo.search(authorName, if (reset) 1 else state.page) }
            .onSuccess { result ->
                val fresh = result.content.filter { new -> state.albums.none { it.id == new.id } }
                state.albums = if (reset) result.content else state.albums + fresh
                state.total = result.total
                state.page = if (reset) 2 else state.page + 1
                // 服务端有时会把越界页返回成上一页（全是重复 id），拿不到新数据就停止翻页
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = authorName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.albums.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.albums.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { load(true) } }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重試")
                    }
                }

                state.albums.isEmpty() -> Text(
                    text = "沒有找到作品",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    state = state.gridState,
                    columns = GridCells.Fixed(2),
                    // 底部多留一点：否则「載入更多」会贴在手势条下面，点不到
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (hiddenCount > 0) {
                                "共 ${state.total} 部 · 已屏蔽 $hiddenCount 项"
                            } else {
                                "共 ${state.total} 部"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items = shown, key = { it.id }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                        )
                    }
                    if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                when {
                                    state.loading -> CircularProgressIndicator()
                                    state.error != null -> Button(onClick = { scope.launch { load(false) } }) {
                                        Text("載入失敗，點擊重試")
                                    }
                                    else -> Button(onClick = { scope.launch { load(false) } }) {
                                        Text("載入更多（${state.albums.size}/${state.total}）")
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
