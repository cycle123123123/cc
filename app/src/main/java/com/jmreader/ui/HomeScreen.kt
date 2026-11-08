package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.jmreader.data.userMessage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.core.JmConfig
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.LastRead
import com.jmreader.data.PromoteSection
import com.jmreader.data.ReadingHistory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: JmRepository,
    state: HomeState,
    onOpen: (Long) -> Unit,
    onContinue: (com.jmreader.data.LastRead) -> Unit,
    onSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jmreader", android.content.Context.MODE_PRIVATE) }
    val history = remember { ReadingHistory.of(context) }
    val lastRead by history.last.collectAsState()

    val albums = state.albums
    val loading = state.loading
    val error = state.error
    val update = state.update
    var sections by remember { mutableStateOf<List<PromoteSection>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }

    val blockFilter = remember { ContentFilter.of(context) }
    val blockRules by blockFilter.rules.collectAsState()
    val shown = remember(albums, blockRules) {
        albums.filterNot { ContentFilter.matches(it, blockRules) }
    }
    val hiddenCount = albums.size - shown.size

    suspend fun load(reset: Boolean) {
        if (state.loading) return
        state.loading = true
        state.error = null
        runCatchingCancellable { repo.latest(if (reset) 1 else state.page) }
            .onSuccess { list ->
                state.albums = if (reset) {
                    list
                } else {
                    state.albums + list.filter { new -> state.albums.none { it.id == new.id } }
                }
                state.page = if (reset) 2 else state.page + 1
                state.loaded = true
            }
            .onFailure {
                state.error = it.userMessage()
                state.loaded = true
            }
        state.loading = false
    }

    LaunchedEffect(shown) {
        // 首屏封面预加载（Coil 会写进内存 + 磁盘缓存）
        shown.take(8).forEach { album ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(repo.coverUrl(album.id, tall = true))
                    .build(),
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!state.loaded) {
            // 先用上次缓存的首页内容垫着，再走网络刷新
            if (state.albums.isEmpty()) {
                repo.cachedLatest()?.let { state.albums = it }
            }
            // 测速放后台：首屏不用等（首启最多要 2.5s）
            launch { runCatchingCancellable { repo.optimizeHost() } }
            load(true)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首頁") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    runCatchingCancellable { repo.promote() }.onSuccess { sections = it }
                    load(true)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                albums.isEmpty() && loading -> SkeletonGrid()

                albums.isEmpty() && error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { load(true) } }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重試")
                    }
                }

                else -> LazyVerticalGrid(
                    state = state.gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                                text = "已屏蔽 $hiddenCount 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    sections.forEach { section ->
                        val visible = section.content.filterNot { ContentFilter.matches(it, blockRules) }
                        if (visible.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "sec_${section.id}") {
                                Column {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(items = visible, key = { it.id }) { album ->
                                            Box(Modifier.width(120.dp)) {
                                                AlbumCard(
                                                    repo = repo,
                                                    album = album,
                                                    onClick = { album.id.toLongOrNull()?.let(onOpen) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "latest_header") {
                        Text(
                            text = "最新",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    items(items = shown, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (loading) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = { scope.launch { load(false) } }) { Text("載入更多") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonGrid() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(6) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun ContinueCard(repo: JmRepository, item: LastRead, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = repo.coverUrl(item.cid.toString(), tall = true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "繼續閱讀",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "第 ${item.page + 1} 頁",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateBanner(version: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "官方協議已更新至 v$version",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "當前客戶端適配 v${JmConfig.PROTOCOL_VERSION}，如遇異常請更新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss) { Text("知道了") }
    }
}
