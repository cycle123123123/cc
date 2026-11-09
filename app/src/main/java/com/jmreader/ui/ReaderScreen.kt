package com.jmreader.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.jmreader.core.runCatchingCancellable
import com.jmreader.core.JmConfig
import com.jmreader.data.ChapterEntry
import com.jmreader.data.ComicRead
import com.jmreader.data.DownloadRepository
import com.jmreader.data.JmRepository
import com.jmreader.data.PageStore
import com.jmreader.data.ReadingHistory
import com.jmreader.data.SeriesParser
import kotlinx.coroutines.launch

private enum class ReaderMode { VERTICAL, HORIZONTAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    repo: JmRepository,
    albumId: Long,
    cid: Long,
    title: String,
    onBack: () -> Unit = {},
    onOpenChapter: (Long, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jmreader_reader", android.content.Context.MODE_PRIVATE) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var read by remember { mutableStateOf<ComicRead?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var uiVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var shard by remember { mutableStateOf(repo.imageShard) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    // 底欄「圖源」用单独的状态，让菜单贴着底栏弹出，而不是跑到屏幕右上角
    var bottomSourceMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(prefs.getFloat("brightness", -1f)) }
    var animate by remember { mutableStateOf(prefs.getBoolean("animate", true)) }
    var rtl by remember { mutableStateOf(prefs.getBoolean("rtl", false)) }
    var tapWhenZoomed by remember { mutableStateOf(prefs.getBoolean("tap_when_zoomed", false)) }
    var lockOrientation by remember { mutableStateOf(prefs.getBoolean("lock_orientation", true)) }
    val configuration = LocalConfiguration.current
    var bookmarks by remember(cid) {
        mutableStateOf(
            prefs.getString("bookmarks_$cid", "").orEmpty()
                .split(",")
                .mapNotNull { it.toIntOrNull() }
                .toSet(),
        )
    }
    var mode by remember {
        mutableStateOf(
            if (prefs.getString("mode", "vertical") == "horizontal") ReaderMode.HORIZONTAL else ReaderMode.VERTICAL,
        )
    }

    val savedPage = remember(cid) { prefs.getInt("progress_$cid", 0).coerceAtLeast(0) }

    // 亮度：-1 表示跟随系统
    val activity = context as? android.app.Activity
    DisposableEffect(brightness) {
        val window = activity?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness =
                if (brightness < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness
            window.attributes = params
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val params = window.attributes
            params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params
        }
    }

    // 方向锁定：进入阅读器时锁在当前方向，退出恢复
    DisposableEffect(lockOrientation, configuration.orientation) {
        if (lockOrientation) {
            activity?.requestedOrientation =
                if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(cid) {
        runCatchingCancellable { repo.comicRead(cid) }
            .onSuccess { read = it }
            .onFailure { throwable ->
                // 断网时用本地下载元数据打开已下载章节
                val local = DownloadRepository.of(context).localComicRead(cid)
                if (local != null) read = local else error = throwable.message ?: "載入失敗"
            }
    }

    LaunchedEffect(albumId) {
        runCatchingCancellable { repo.chapter(albumId) }
            .onSuccess { chapters = SeriesParser.parse(it.series) }
    }

    val data = read
    val images = data?.images ?: emptyList()

    val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { read?.images?.size ?: 0 })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedPage)

    val store = remember(data?.id, data?.scrambleId, shard) {
        data?.let {
            PageStore(
                repo = repo,
                cid = cid,
                aid = it.id,
                scrambleId = it.scrambleId,
                targetWidth = context.resources.displayMetrics.widthPixels,
            )
        }
    }
    DisposableEffect(store) {
        onDispose { store?.close() }
    }

    val currentIndex by remember {
        derivedStateOf {
            if (mode == ReaderMode.VERTICAL) listState.firstVisibleItemIndex else pagerState.currentPage
        }
    }

    // 跳頁（設定彈窗與底欄進度條共用）
    fun jumpToPage(target: Int) {
        val safe = target.coerceIn(0, images.lastIndex.coerceAtLeast(0))
        scope.launch {
            if (mode == ReaderMode.VERTICAL) listState.scrollToItem(safe) else pagerState.scrollToPage(safe)
        }
    }

    // 書籤切換（設定彈窗與底欄快捷按鈕共用）
    fun toggleBookmarkAt(page: Int) {
        val updated = if (bookmarks.contains(page)) bookmarks - page else bookmarks + page
        bookmarks = updated
        prefs.edit().putString("bookmarks_$cid", updated.sorted().joinToString(",")).apply()
    }

    // 圖源測速（頂欄菜單與底欄「圖源」共用）
    fun benchmarkSources() {
        val sample = images.firstOrNull()?.image.orEmpty()
        scope.launch {
            val best = repo.benchmarkImageHosts(sample)
            if (best != null) {
                shard = 5
                android.widget.Toast.makeText(
                    context,
                    "已選 圖源 ${best.first}（${best.second}ms）",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "測速失敗，請稍後再試",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 阅读进度
    LaunchedEffect(data?.id, mode) {
        if (data == null) return@LaunchedEffect
        snapshotFlow {
            if (mode == ReaderMode.VERTICAL) listState.firstVisibleItemIndex else pagerState.currentPage
        }.collect { index ->
            prefs.edit().putInt("progress_$cid", index).apply()
            ReadingHistory.of(context).save(albumId, cid, title, index)
        }
    }

    // 预取当前页和后 3 页（首次打开时就是 1-4 页）
    LaunchedEffect(currentIndex, data?.id, mode) {
        val comic = data ?: return@LaunchedEffect
        val next = comic.images.drop(currentIndex).take(4).map { it.image to it.page }
        store?.prefetch(next)
    }

    // 首次进入阅读器时后台测一次图源，之后 24 小时内不再测
    LaunchedEffect(data?.id) {
        val sample = data?.images?.firstOrNull()?.image ?: return@LaunchedEffect
        runCatchingCancellable { repo.maybeAutoBenchmarkImageHost(sample) }
    }

    // 快看到末尾时预取下一话的前 3 页
    val prefetchedChapters = remember { mutableSetOf<Long>() }
    LaunchedEffect(currentIndex, data?.id) {
        val comic = data ?: return@LaunchedEffect
        if (comic.images.size - currentIndex > 3) return@LaunchedEffect
        val index = chapters.indexOfFirst { it.id == cid }
        val next = chapters.getOrNull(index + 1) ?: return@LaunchedEffect
        if (prefetchedChapters.add(next.id)) {
            runCatchingCancellable { repo.prefetchChapter(next.id) }
        }
    }

    // 音量键翻页
    DisposableEffect(Unit) {
        ReaderKeyBus.active = true
        onDispose { ReaderKeyBus.active = false }
    }
    LaunchedEffect(mode, data?.id) {
        ReaderKeyBus.events.collect { keyCode ->
            val total = data?.images?.size ?: return@collect
            if (total == 0) return@collect
            val step = if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1
            val now = if (mode == ReaderMode.VERTICAL) listState.firstVisibleItemIndex else pagerState.currentPage
            val target = (now + step).coerceIn(0, total - 1)
            if (mode == ReaderMode.VERTICAL) listState.animateScrollToItem(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    // 全屏
    DisposableEffect(uiVisible) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (uiVisible) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    LaunchedEffect(mode, data?.id) {
        if (data != null && pagerState.currentPage >= data.images.size) {
            pagerState.scrollToPage(data.images.lastIndex.coerceAtLeast(0))
        }
    }

    fun toggleMode() {
        val index = currentIndex
        mode = if (mode == ReaderMode.VERTICAL) ReaderMode.HORIZONTAL else ReaderMode.VERTICAL
        prefs.edit().putString("mode", if (mode == ReaderMode.HORIZONTAL) "horizontal" else "vertical").apply()
        scope.launch {
            if (mode == ReaderMode.VERTICAL) listState.scrollToItem(index) else pagerState.scrollToPage(index)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            error != null -> Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )

            data == null || store == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            else -> {
                val pageStore = store
                if (mode == ReaderMode.VERTICAL) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(items = images, key = { it.page }) { page ->
                            VerticalPage(
                                store = pageStore,
                                url = page.image,
                                page = page.page,
                                onTap = { uiVisible = !uiVisible },
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !zoomed,
                        reverseLayout = rtl,
                    ) { index ->
                        val page = images.getOrNull(index)
                        if (page != null) {
                            HorizontalPage(
                                store = pageStore,
                                url = page.image,
                                page = page.page,
                                onTap = { offset ->
                                    if (zoomed && !tapWhenZoomed) {
                                        uiVisible = !uiVisible
                                    } else {
                                        val third = context.resources.displayMetrics.widthPixels / 3f
                                        when {
                                            offset.x < third -> {
                                                val delta = if (rtl) 1 else -1
                                                val target = (pagerState.currentPage + delta)
                                                    .coerceIn(0, images.lastIndex.coerceAtLeast(0))
                                                scope.launch { pagerState.animateScrollToPage(target) }
                                            }
                                            offset.x > third * 2 -> {
                                                val delta = if (rtl) -1 else 1
                                                val target = (pagerState.currentPage + delta)
                                                    .coerceIn(0, images.lastIndex.coerceAtLeast(0))
                                                scope.launch { pagerState.animateScrollToPage(target) }
                                            }
                                            else -> uiVisible = !uiVisible
                                        }
                                    }
                                },
                                onZoomChanged = { zoomed = it },
                                animate = animate,
                                pageOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "閱讀設定")
                    }
                    Box {
                        IconButton(onClick = { sourceMenuOpen = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "圖源")
                        }
                        DropdownMenu(
                            expanded = sourceMenuOpen,
                            onDismissRequest = { sourceMenuOpen = false },
                            containerColor = Color(0xF21C1C1E),
                        ) {
                            SourceMenu(
                                shard = shard,
                                onPick = { value ->
                                    if (value >= 0) repo.imageShard = value
                                    shard = value
                                    sourceMenuOpen = false
                                },
                                onBenchmark = {
                                    sourceMenuOpen = false
                                    benchmarkSources()
                                },
                            )
                        }
                    }
                    IconButton(onClick = { toggleMode() }) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = if (mode == ReaderMode.VERTICAL) "切換為橫向" else "切換為縱向",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        AnimatedVisibility(visible = uiVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            val seriesIndex = chapters.indexOfFirst { it.id == cid }
            ReaderBottomBar(
                index = currentIndex,
                total = images.size,
                previous = if (seriesIndex > 0) chapters[seriesIndex - 1] else null,
                next = if (seriesIndex >= 0) chapters.getOrNull(seriesIndex + 1) else null,
                hasChapters = chapters.size > 1,
                onPrevious = { chapter -> chapter?.let { onOpenChapter(it.id, it.title) } },
                onNext = { chapter -> chapter?.let { onOpenChapter(it.id, it.title) } },
                onJump = { jumpToPage(it) },
                isVertical = mode == ReaderMode.VERTICAL,
                onToggleMode = { toggleMode() },
                isBookmarked = bookmarks.contains(currentIndex),
                onToggleBookmark = { toggleBookmarkAt(currentIndex) },
                onOpenSource = { bottomSourceMenu = true },
                onOpenSettings = { showSettings = true },
            )
        }

        // 底欄「圖源」的菜單錨點：貼著底欄向上展開
        if (uiVisible) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                DropdownMenu(
                    expanded = bottomSourceMenu,
                    onDismissRequest = { bottomSourceMenu = false },
                    containerColor = Color(0xF21C1C1E),
                ) {
                    SourceMenu(
                        shard = shard,
                        onPick = { value ->
                            if (value >= 0) repo.imageShard = value
                            shard = value
                            bottomSourceMenu = false
                        },
                        onBenchmark = {
                            bottomSourceMenu = false
                            benchmarkSources()
                        },
                    )
                }
            }
        }

        if (showSettings) {
            ReaderSettingsDialog(
                brightness = brightness,
                onBrightness = {
                    brightness = it
                    prefs.edit().putFloat("brightness", it).apply()
                },
                animate = animate,
                onAnimate = {
                    animate = it
                    prefs.edit().putBoolean("animate", it).apply()
                },
                rtl = rtl,
                onRtl = {
                    rtl = it
                    prefs.edit().putBoolean("rtl", it).apply()
                },
                tapWhenZoomed = tapWhenZoomed,
                onTapWhenZoomed = {
                    tapWhenZoomed = it
                    prefs.edit().putBoolean("tap_when_zoomed", it).apply()
                },
                lockOrientation = lockOrientation,
                onLockOrientation = {
                    lockOrientation = it
                    prefs.edit().putBoolean("lock_orientation", it).apply()
                },
                index = currentIndex,
                total = images.size,
                onJump = { jumpToPage(it) },
                bookmarks = bookmarks,
                onToggleBookmark = { toggleBookmarkAt(currentIndex) },
                onJumpBookmark = { jumpToPage(it) },
                onDismiss = { showSettings = false },
            )
        }
    }
}

/** 圖源菜单内容（顶栏图标与底栏「圖源」共用）。 */
@Composable
private fun ColumnScope.SourceMenu(
    shard: Int,
    onPick: (Int) -> Unit,
    onBenchmark: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(if (shard == 0) "✓ 自動" else "自動", color = Color.White) },
        onClick = { onPick(0) },
    )
    DropdownMenuItem(
        text = { Text(if (shard == 5) "✓ 測速最優" else "測速最優", color = Color.White) },
        onClick = onBenchmark,
    )
    JmConfig.IMAGE_SHARDS.forEachIndexed { index, _ ->
        DropdownMenuItem(
            text = {
                Text(
                    if (shard == index + 1) "✓ 圖源 ${index + 1}"
                    else "圖源 ${index + 1}",
                    color = Color.White,
                )
            },
            onClick = { onPick(index + 1) },
        )
    }
}
