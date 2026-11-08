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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jmreader.data.userMessage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CategoryBlock
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.SearchHistory
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    repo: JmRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchHistory = remember { SearchHistory.of(context) }
    val recent by searchHistory.keywords.collectAsState()
    var hotBlocks by remember { mutableStateOf<List<CategoryBlock>>(emptyList()) }
    // 搜索结果接口不返回点击量，只有 /album 有；卡片可见时再逐个补，最多 2 个并发
    val views = remember { mutableStateMapOf<String, Int>() }
    val viewRequests = remember { mutableSetOf<String>() }
    val viewGate = remember { Semaphore(2) }
    val blockFilter = remember { ContentFilter.of(context) }
    val blockRules by blockFilter.rules.collectAsState()
    val shown = remember(results, blockRules) {
        results.filterNot { ContentFilter.matches(it, blockRules) }
    }
    val hiddenCount = results.size - shown.size

    suspend fun runSearch(reset: Boolean) {
        val keyword = query.trim()
        if (keyword.isEmpty() || loading) return
        loading = true
        error = null
        runCatchingCancellable { repo.search(keyword, if (reset) 1 else page) }
            .onSuccess { result ->
                results = if (reset) {
                    result.content
                } else {
                    results + result.content.filter { new -> results.none { it.id == new.id } }
                }
                total = result.total
                page = if (reset) 2 else page + 1
                searched = true
                searchHistory.add(keyword)
            }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) runSearch(true)
    }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.categories() }.onSuccess { hotBlocks = it.blocks }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && results.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                !searched -> Column(
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
                                        query = keyword
                                        scope.launch { runSearch(true) }
                                    },
                                    label = { Text(keyword) },
                                )
                            }
                        }
                    }

                    hotBlocks.forEach { block ->
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
                                            query = tag
                                            scope.launch { runSearch(true) }
                                        },
                                        label = { Text(tag) },
                                    )
                                }
                            }
                        }
                    }

                    if (recent.isEmpty() && hotBlocks.isEmpty()) {
                        Text(
                            text = "輸入關鍵字開始搜尋",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }

                results.isEmpty() -> Text(
                    text = "沒有結果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (hiddenCount > 0) "共 $total 個結果 · 已屏蔽 $hiddenCount 项" else "共 $total 個結果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items = shown, key = { it.id }) { album ->
                        val id = album.id
                        val known = views[id]
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            badge = known?.takeIf { it > 0 }?.let { "觀看 ${formatCount(it)}" },
                            onVisible = {
                                if (known == null && viewRequests.add(id)) {
                                    scope.launch {
                                        val aid = id.toLongOrNull() ?: return@launch
                                        viewGate.withPermit {
                                            runCatchingCancellable { repo.album(aid) }
                                                .onSuccess { detail ->
                                                    if (detail.viewCount > 0) views[id] = detail.viewCount
                                                }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = { scope.launch { runSearch(false) } }) { Text("載入更多") }
                            }
                        }
                    }
                }
            }
        }
    }
}
