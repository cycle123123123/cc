package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                state.loading && state.results.isEmpty() ->
                    CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                state.error != null -> ErrorState(
                    message = state.error!!,
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

                state.results.isEmpty() -> Text(
                    text = "沒有找到相關作品",
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
                                "共 ${state.total} 個結果 · 已屏蔽 $hiddenCount 項"
                            } else {
                                "共 ${state.total} 個結果"
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
                            onVisible = {
                                ensureAlbumMeta(repo, scope, viewGate, state.metas, state.albumRequests, id)
                            },
                        )
                    }
                    if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                when {
                                    state.loading -> CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                                    state.error != null -> Button(
                                        onClick = { scope.launch { runSearch(false) } },
                                        shape = CircleShape,
                                    ) {
                                        Text("載入失敗，點擊重試")
                                    }
                                    else -> Button(
                                        onClick = { scope.launch { runSearch(false) } },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = IosCardWhite,
                                            contentColor = IosBlue,
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                                    ) {
                                        Text(
                                            "載入更多（${state.results.size}/${state.total}）",
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        )
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
