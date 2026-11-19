package com.jmreader.ui.reader

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import com.jmreader.ui.navigation.AppRoot
import com.jmreader.data.model.ChapterEntry
import com.jmreader.data.model.ComicRead
import com.jmreader.data.page.PageCache
import com.jmreader.data.page.PageStore
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.navigation.Screen
import com.jmreader.data.model.SeriesParser
import com.jmreader.core.JmConfig
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.ui.theme.IosBlue

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
    val container = LocalAppContainer.current
    val readerPrefs = container.readerPrefs

    // 阅读统计：本话停留时长 + 实际看过的页数（少于 5 秒且没翻页的会话丢弃）
    val readingStats = container.readingStats
    val sessionStart = remember(cid) { SystemClock.elapsedRealtime() }
    val seenPages = remember(cid) { mutableSetOf<Int>() }
    DisposableEffect(cid) {
        onDispose {
            val seconds = (SystemClock.elapsedRealtime() - sessionStart) / 1000
            val pages = seenPages.size
            if (seconds >= 5 || pages > 1) readingStats.record(seconds, pages, albumId)
        }
    }
    val toast = rememberToaster()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 當前話的資料。read 本身不帶 key，用 loadedCid 標記它屬於哪一話：
    // AppRoot 用同一個 slot 換掉 Screen.Reader 時，cid 變了但 state 會留下來，
    // 只有 loadedCid == cid 才把 read 當成「當前畫面」，否則一律視為未載入。
    var read by remember { mutableStateOf<ComicRead?>(null) }
    var loadedCid by remember { mutableStateOf<Long?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var uiVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    // 条漫整体缩放：1f..3f，切话/切模式归位
    var vScale by remember(cid) { mutableFloatStateOf(1f) }
    var vOffsetX by remember(cid) { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var saveTarget by remember { mutableStateOf<Pair<Bitmap, Int>?>(null) }
    var shard by remember { mutableStateOf(repo.imageShard) }
    // 底欄「圖源」用单独的状态，让菜单贴着底栏弹出，而不是跑到屏幕右上角
    var bottomSourceMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // 進入閱讀器時的第一話沿用歷史進度，之後切話一律從第 0 頁開始
    var initialRestored by remember { mutableStateOf(false) }
    // 頁碼歸位之前不要寫進度，否則切話瞬間會把上一話的頁碼寫到新話身上
    var positionReady by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(readerPrefs.brightness) }
    var animate by remember { mutableStateOf(readerPrefs.animate) }
    var rtl by remember { mutableStateOf(readerPrefs.rtl) }
    var tapWhenZoomed by remember { mutableStateOf(readerPrefs.tapWhenZoomed) }
    var lockOrientation by remember { mutableStateOf(readerPrefs.lockOrientation) }
    val configuration = LocalConfiguration.current
    // 書籤只保留最近添加的一頁（null = 沒有書籤）
    var bookmark by remember(cid) { mutableStateOf(readerPrefs.bookmark(cid)) }
    var mode by remember {
        mutableStateOf(
            if (readerPrefs.vertical) ReaderMode.VERTICAL else ReaderMode.HORIZONTAL,
        )
    }

    val savedPage = remember(cid) { readerPrefs.progress(cid) }

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
        // 切話先清空，避免上一話的圖片/頁碼/緩存串到新的一話
        read = null
        loadedCid = null
        error = null
        positionReady = false

        // 已下載的章節先用本地元數據秒開（斷網也能進），再嘗試用線上資料刷新
        val local = container.downloads.localComicRead(cid)
        if (local != null) {
            read = local
            loadedCid = cid
        }

        runCatchingCancellable { repo.comicRead(cid) }
            .onSuccess {
                read = it
                loadedCid = cid
            }
            .onFailure { throwable ->
                if (local == null) error = throwable.message ?: "載入失敗"
            }
    }

    LaunchedEffect(albumId) {
        // 目錄優先取 /album 的 series：它一定是數組，且和詳情頁的目錄同源。
        // /chapter 的 series 在多話作品裡可能只是「總話數」（數字），
        // 解析不出數組時 chapters 會是空列表 → 上一話/下一話永久禁用，点了没反应。
        val fromAlbum = runCatchingCancellable { repo.seriesChapters(repo.album(albumId)) }
            .getOrNull()
            .orEmpty()
        chapters = if (fromAlbum.isNotEmpty()) {
            fromAlbum
        } else {
            runCatchingCancellable { SeriesParser.parse(repo.chapter(albumId).series) }
                .getOrNull()
                .orEmpty()
        }
    }

    val data = read?.takeIf { loadedCid == cid }
    val images = data?.images ?: emptyList()

    val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { read?.images?.size ?: 0 })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedPage)

    // store 綁定當前 cid：換話時舊 store 必須立刻作廢，
    // 否則它會用舊 cid 讀寫 PageCache（緩存鍵是 cid+頁碼），把上一話的圖當成新一話的圖。
    val store = remember(cid, data?.id, data?.scrambleId, shard) {
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

    // 進入閱讀器沿用上次進度；切到相鄰話則從第 0 頁開始
    LaunchedEffect(cid, data?.id) {
        val comic = data ?: return@LaunchedEffect
        zoomed = false
        if (initialRestored) {
            if (comic.images.isNotEmpty()) {
                pagerState.scrollToPage(0)
                listState.scrollToItem(0)
            }
        } else {
            // 首次載入用的就是 rememberPagerState/rememberLazyListState 的初始頁（savedPage）
            initialRestored = true
        }
        positionReady = true
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

    // 書籤只保留一條：page == null 表示移除
    fun setBookmark(page: Int?) {
        bookmark = page
        readerPrefs.saveBookmark(cid, page)
    }

    // 圖源測速（底欄「圖源」菜單用）
    fun benchmarkSources() {
        val sample = images.firstOrNull()?.image.orEmpty()
        scope.launch {
            val best = repo.benchmarkImageHosts(sample)
            if (best != null) {
                shard = 5
                toast("已選 圖源 ${best.first}（${best.second}ms）", false)
            } else {
                toast("測速失敗，請稍後再試", false)
            }
        }
    }

    // 阅读进度（必須帶 cid：不然切話後舊協程會繼續把頁碼寫進上一話的 progress）
    LaunchedEffect(cid, data?.id, mode, positionReady) {
        if (data == null || !positionReady) return@LaunchedEffect
        snapshotFlow {
            if (mode == ReaderMode.VERTICAL) listState.firstVisibleItemIndex else pagerState.currentPage
        }.collect { index ->
            readerPrefs.saveProgress(cid, index)
            container.readingHistory.save(albumId, cid, title, index)
        }
    }

    // 预取当前页和后 3 页（首次打开时就是 1-4 页）
    LaunchedEffect(currentIndex, data?.id, mode, cid) {
        seenPages.add(currentIndex)
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
    LaunchedEffect(currentIndex, data?.id, cid) {
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
    LaunchedEffect(mode, cid, data?.id) {
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

    // 竖向缩放与统一的「zoomed」状态保持一致（横向由页面自己回调）
    LaunchedEffect(mode, vScale) {
        if (mode == ReaderMode.VERTICAL) zoomed = vScale > 1.01f
    }

    fun toggleMode() {
        val index = currentIndex
        mode = if (mode == ReaderMode.VERTICAL) ReaderMode.HORIZONTAL else ReaderMode.VERTICAL
        readerPrefs.vertical = (mode == ReaderMode.VERTICAL)
        // 切模式时把条漫缩放归位，避免横向页继承 2x 缩放
        vScale = 1f
        vOffsetX = 0f
        zoomed = false
        scope.launch {
            if (mode == ReaderMode.VERTICAL) listState.scrollToItem(index) else pagerState.scrollToPage(index)
        }
    }

    fun clampVerticalX(value: Float, scale: Float): Float {
        val max = viewportWidth * (scale - 1f) / 2f
        return if (max <= 0f) 0f else value.coerceIn(-max, max)
    }

    /** 双击缩放：以双击点的横坐标为中心，平滑放大/还原。 */
    fun zoomVerticalTo(target: Float, focusX: Float) {
        val startScale = vScale
        val startOffset = vOffsetX
        val targetOffset = if (target <= 1.01f) {
            0f
        } else {
            val max = viewportWidth * (target - 1f) / 2f
            (-(focusX - viewportWidth / 2f) * (target - 1f)).coerceIn(-max, max)
        }
        scope.launch {
            animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                vScale = startScale + (target - startScale) * t
                vOffsetX = startOffset + (targetOffset - startOffset) * t
            }
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportWidth = it.width }
                            .graphicsLayer {
                                scaleX = vScale
                                scaleY = vScale
                                translationX = vOffsetX
                            }
                            .pointerInput(cid) {
                                // 只在双指时消费；单指继续交给 LazyColumn 滚动
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size >= 2) {
                                            val next = (vScale * event.calculateZoom()).coerceIn(1f, 3f)
                                            vScale = next
                                            vOffsetX = if (next <= 1.01f) {
                                                0f
                                            } else {
                                                clampVerticalX(vOffsetX + event.calculatePan().x, next)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                    ) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(items = images, key = { it.page }) { page ->
                                VerticalPage(
                                    store = pageStore,
                                    url = page.image,
                                    page = page.page,
                                    onTap = { uiVisible = !uiVisible },
                                    onDoubleTap = { offset ->
                                        if (vScale > 1.01f) zoomVerticalTo(1f, offset.x)
                                        else zoomVerticalTo(2f, offset.x)
                                    },
                                    onLongPress = { bitmap -> saveTarget = bitmap to page.page },
                                )
                            }
                            if (chapters.size > 1) {
                                item(key = "footer_next") {
                                    val index = chapters.indexOfFirst { it.id == cid }
                                    val next = chapters.getOrNull(index + 1)
                                    VerticalNextChapterFooter(
                                        nextTitle = next?.title,
                                        hasChapters = true,
                                        onNext = next?.let { chapter -> { onOpenChapter(chapter.id, chapter.title) } },
                                    )
                                }
                            }
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
                                onLongPress = { bitmap -> saveTarget = bitmap to page.page },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
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
                isBookmarked = bookmark == currentIndex,
                onToggleBookmark = { setBookmark(if (bookmark == currentIndex) null else currentIndex) },
                onOpenSource = { bottomSourceMenu = true },
                onOpenSettings = { showSettings = true },
            )
        }

        // 全屏沉浸时的迷你页码胶囊（右下角）
        if (!uiVisible && images.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = 10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        if (bookmark == currentIndex) append("★ ")
                        append("${currentIndex + 1} / ${images.size}")
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
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
                    readerPrefs.brightness = it
                },
                animate = animate,
                onAnimate = {
                    animate = it
                    readerPrefs.animate = it
                },
                rtl = rtl,
                onRtl = {
                    rtl = it
                    readerPrefs.rtl = it
                },
                tapWhenZoomed = tapWhenZoomed,
                onTapWhenZoomed = {
                    tapWhenZoomed = it
                    readerPrefs.tapWhenZoomed = it
                },
                lockOrientation = lockOrientation,
                onLockOrientation = {
                    lockOrientation = it
                    readerPrefs.lockOrientation = it
                },
                index = currentIndex,
                total = images.size,
                onJump = { jumpToPage(it) },
                bookmark = bookmark,
                onAddBookmark = { setBookmark(currentIndex) },
                onRemoveBookmark = { setBookmark(null) },
                onJumpBookmark = { jumpToPage(it) },
                onDismiss = { showSettings = false },
            )
        }

        saveTarget?.let { (bitmap, page) ->
            AlertDialog(
                onDismissRequest = { saveTarget = null },
                title = { Text("保存圖片") },
                text = { Text("保存第 $page 頁到相冊（Pictures/JMReader）？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            saveTarget = null
                            scope.launch {
                                val result = savePageToGallery(context, bitmap, cid, page)
                                toast(
                                    result.fold(
                                        onSuccess = { "已保存到相冊 Pictures/JMReader" },
                                        onFailure = { "保存失敗：${it.message ?: "未知錯誤"}" },
                                    ),
                                )
                            }
                        },
                    ) {
                        Text("保存", color = IosBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { saveTarget = null }) { Text("取消") }
                },
            )
        }
    }
}

/** 条漫末尾的「下一话」衔接条；最后一话时提示一下。 */
@Composable
private fun VerticalNextChapterFooter(
    nextTitle: String?,
    hasChapters: Boolean,
    onNext: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (onNext != null) Color(0xFF1F1F22) else Color.Transparent)
            .then(
                if (onNext != null) Modifier.clickable(onClick = onNext) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            nextTitle != null -> Text(
                text = "下一話：$nextTitle →",
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            hasChapters -> Text(
                text = "已是最後一話",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
            )
        }
    }
}

/** 圖源菜单内容（底欄「圖源」用）。 */
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

