package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.SearchHistory
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    repo: JmRepository,
    state: SearchState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    initialQuery: String = "",
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchHistory = remember { SearchHistory.of(context) }
    val recent by searchHistory.keywords.collectAsState()
    val viewGate = remember { Semaphore(2) }
    val blockFilter = remember { ContentFilter.of(context) }
    val blockRules by blockFilter.rules.collectAsState()
    val shown = remember(state.results, blockRules) {
        state.results.filterNot { ContentFilter.matches(it, blockRules) }
    }
    val hiddenCount = state.results.size - shown.size

    suspend fun runSearch(reset: Boolean) {
        val keyword = state.query.trim()
        if (keyword.isEmpty() || state.loading) return
        state.loading = true
        state.error = null
        runCatchingCancellable { repo.search(keyword, if (reset) 1 else state.page, state.category, state.order) }
            .onSuccess { result ->
                val fresh = result.content.filter { new -> state.results.none { it.id == new.id } }
                state.results = if (reset) result.content else state.results + fresh
                state.total = result.total
                state.page = if (reset) 2 else state.page + 1
                state.searched = true
                state.hasMore = if (reset) {
                    result.content.isNotEmpty() && result.content.size < result.total
                } else {
                    fresh.isNotEmpty() && state.results.size < result.total
                }
                searchHistory.add(keyword)
            }
            .onFailure { state.error = it.userMessage() }
        state.loading = false
    }

    // 只处理一次首屏：从详情返回时不重复请求，滚动位置由 state.gridState 保留
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        placeholder = { Text("搜尋作品 / 作者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { scope.launch { runSearch(true) } }),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runSearch(true) } }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜尋")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                    state.loading && state.results.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.error != null -> Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    !state.searched -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (recent.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "最近搜尋",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { searchHistory.clear() }) { Text("清空") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recent.forEach { keyword ->
                                    AssistChip(
                                        onClick = {
                                            state.query = keyword
                                            scope.launch { runSearch(true) }
                                        },
                                        label = { Text(keyword) },
                                    )
                                }
                            }
                        }

                        state.hotBlocks.forEach { block ->
                            if (block.content.isNotEmpty()) {
                                Text(
                                    text = block.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    block.content.take(12).forEach { tag ->
                                        AssistChip(
                                            onClick = {
                                                state.query = tag
                                                scope.launch { runSearch(true) }
                                            },
                                            label = { Text(tag) },
                                        )
                                    }
                                }
                            }
                        }

                        if (recent.isEmpty() && state.hotBlocks.isEmpty()) {
                            Text(
                                text = "輸入關鍵字開始搜尋",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            )
                        }
                    }

                    state.results.isEmpty() -> Text(
                        text = "沒有結果",
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
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (hiddenCount > 0) {
                                    "共 ${state.total} 個結果 · 已屏蔽 $hiddenCount 项"
                                } else {
                                    "共 ${state.total} 個結果"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(items = shown, key = { it.id }) { album ->
                            val id = album.id
                            AlbumCard(
                                repo = repo,
                                album = album,
                                onClick = { album.id.toLongOrNull()?.let(onOpen) },
                                badge = metaBadge(state.metas[id]),
                                onVisible = {
                                    ensureAlbumMeta(repo, scope, viewGate, state.metas, state.albumRequests, id)
                                },
                            )
                        }
                        if (state.hasMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    when {
                                        state.loading -> CircularProgressIndicator()
                                        state.error != null -> Button(onClick = { scope.launch { runSearch(false) } }) {
                                            Text("載入失敗，點擊重試")
                                        }
                                        else -> Button(onClick = { scope.launch { runSearch(false) } }) {
                                            Text("載入更多（${state.results.size}/${state.total}）")
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
