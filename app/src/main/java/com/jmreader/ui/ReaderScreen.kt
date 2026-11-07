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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.jmreader.data.ChapterEntry
import com.jmreader.data.ComicRead
import com.jmreader.data.DownloadRepository
import com.jmreader.data.JmRepository
import com.jmreader.data.PageStore
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
    var mode by remember {
        mutableStateOf(
            if (prefs.getString("mode", "vertical") == "horizontal") ReaderMode.HORIZONTAL else ReaderMode.VERTICAL,
        )
    }

    val savedPage = remember(cid) { prefs.getInt("progress_$cid", 0).coerceAtLeast(0) }

    LaunchedEffect(cid) {
        runCatching { repo.comicRead(cid) }
            .onSuccess { read = it }
            .onFailure { throwable ->
                // 断网时用本地下载元数据打开已下载章节
                val local = DownloadRepository.of(context).localComicRead(cid)
                if (local != null) read = local else error = throwable.message ?: "載入失敗"
            }
    }

    LaunchedEffect(albumId) {
        runCatching { repo.chapter(albumId) }
            .onSuccess { chapters = SeriesParser.parse(it.series) }
    }

    val data = read
    val images = data?.images ?: emptyList()

    val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { read?.images?.size ?: 0 })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedPage)

    val store = remember(data?.id, data?.scrambleId) {
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

    // 阅读进度
    LaunchedEffect(data?.id, mode) {
        if (data == null) return@LaunchedEffect
        snapshotFlow {
            if (mode == ReaderMode.VERTICAL) listState.firstVisibleItemIndex else pagerState.currentPage
        }.collect { index ->
            prefs.edit().putInt("progress_$cid", index).apply()
        }
    }

    // 预取后 3 页
    LaunchedEffect(currentIndex, data?.id, mode) {
        val comic = data ?: return@LaunchedEffect
        val next = comic.images.drop(currentIndex + 1).take(3).map { it.image to it.page }
        store?.prefetch(next)
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
                    ) { index ->
                        val page = images.getOrNull(index)
                        if (page != null) {
                            HorizontalPage(
                                store = pageStore,
                                url = page.image,
                                page = page.page,
                                onTap = { uiVisible = !uiVisible },
                                onZoomChanged = { zoomed = it },
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
            ReaderBottomBar(
                index = currentIndex,
                total = images.size,
                previous = if (chapters.isNotEmpty()) chapters.getOrNull(chapters.indexOfFirst { it.id == cid } - 1) else null,
                next = if (chapters.isNotEmpty()) chapters.getOrNull(chapters.indexOfFirst { it.id == cid } + 1) else null,
                hasChapters = chapters.size > 1,
                onPrevious = { chapter -> chapter?.let { onOpenChapter(it.id, it.title) } },
                onNext = { chapter -> chapter?.let { onOpenChapter(it.id, it.title) } },
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    index: Int,
    total: Int,
    previous: ChapterEntry?,
    next: ChapterEntry?,
    hasChapters: Boolean,
    onPrevious: (ChapterEntry?) -> Unit,
    onNext: (ChapterEntry?) -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.78f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasChapters) {
                TextButton(enabled = previous != null, onClick = { onPrevious(previous) }) {
                    Text("上一話", color = Color.White)
                }
            }

            Text(
                text = if (total > 0) "${index + 1} / $total" else "",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )

            if (hasChapters) {
                TextButton(enabled = next != null, onClick = { onNext(next) }) {
                    Text("下一話", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VerticalPage(
    store: PageStore,
    url: String,
    page: Int,
    onTap: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = store.cached(url), url) {
        if (value == null) value = store.load(url, page)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .pointerInput(url) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "第 $page 頁",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )

            url.isBlank() -> Text(
                text = "第 $page 頁未下載",
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodySmall,
            )

            else -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun HorizontalPage(
    store: PageStore,
    url: String,
    page: Int,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = store.cached(url), url) {
        if (value == null) value = store.load(url, page)
    }

    val image = bitmap
    if (image == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (url.isBlank()) {
                Text(
                    text = "第 $page 頁未下載",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
        return
    }

    ZoomablePage(image, "第 $page 頁", onTap, onZoomChanged)
}

@Composable
private fun ZoomablePage(
    bitmap: Bitmap,
    description: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(bitmap) {
                // 只处理双指手势，单指留给 Pager 翻页
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            scale = newScale
                            offset = if (newScale <= 1f) {
                                Offset.Zero
                            } else {
                                clampOffset(offset + event.calculatePan(), size, newScale)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}
