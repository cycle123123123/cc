package com.jmreader.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.jmreader.core.JmConfig
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.LastRead
import com.jmreader.data.PromoteSection
import com.jmreader.data.ReadingHistory
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: JmRepository,
    state: HomeState,
    onOpen: (Long) -> Unit,
    onContinue: (LastRead) -> Unit,
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
        shown.take(9).forEach { album ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(repo.coverUrl(album.id, tall = true))
                    .build(),
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!state.loaded) {
            if (state.albums.isEmpty()) {
                repo.cachedLatest()?.let { state.albums = it }
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBg)
            .statusBarsPadding(),
    ) {
        // iOS App Store 极简顶栏：大标题 + 浅灰搜索框
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosGroupedBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "首頁",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosSearchBg)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜尋",
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "搜尋漫畫、作者、標籤…",
                    fontSize = 14.sp,
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

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
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                albums.isEmpty() && loading -> SkeletonGrid()

                albums.isEmpty() && error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { scope.launch { load(true) } },
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("重試")
                    }
                }

                else -> LazyVerticalGrid(
                    state = state.gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                text = "已依規則隱藏 $hiddenCount 部作品",
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                            )
                        }
                    }

                    sections.forEach { section ->
                        val visible = section.content.filterNot { ContentFilter.matches(it, blockRules) }
                        if (visible.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "sec_${section.id}") {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = IosSeparator.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                    Text(
                                        text = section.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IosLabelPrimary,
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(items = visible, key = { it.id }) { album ->
                                            Box(Modifier.width(108.dp)) {
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
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = IosSeparator.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Text(
                                text = "最新上架",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = IosLabelPrimary,
                            )
                        }
                    }

                    items(items = shown, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (loading) {
                                CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                            } else {
                                Button(
                                    onClick = { scope.launch { load(false) } },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IosCardWhite,
                                        contentColor = IosBlue,
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                                ) {
                                    Text(
                                        text = "載入更多",
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 16.dp),
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

@Composable
private fun SkeletonGrid() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(9) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosFillGray.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun ContinueCard(repo: JmRepository, item: LastRead, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = repo.coverUrl(item.cid.toString(), tall = true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(7.dp))
                .background(IosFillGray)
                .border(0.5.dp, Color(0x14000000), RoundedCornerShape(7.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = "繼續閱讀",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = IosBlue,
            )
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosLabelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "上次讀到 第 ${item.page + 1} 頁",
                fontSize = 12.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(IosBlueSoft)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "繼續",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosBlue,
            )
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x14000000), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "官方協議已更新至 v$version",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosLabelPrimary,
            )
            Text(
                text = "當前客戶端適配 v${JmConfig.PROTOCOL_VERSION}，如遇異常請更新",
                fontSize = 11.sp,
                color = IosLabelSecondary,
            )
        }
        TextButton(onClick = onDismiss) {
            Text("知道了", color = IosBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}
