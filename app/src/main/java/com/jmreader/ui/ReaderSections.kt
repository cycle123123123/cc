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

@Composable
internal fun ReaderBottomBar(
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
internal fun VerticalPage(
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
internal fun HorizontalPage(
    store: PageStore,
    url: String,
    page: Int,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    animate: Boolean,
    pageOffset: Float,
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

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (animate) {
                    val offset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                    alpha = 1f - offset * 0.35f
                    val scale = 1f - offset * 0.05f
                    scaleX = scale
                    scaleY = scale
                }
            },
    ) {
        ZoomablePage(image, "第 $page 頁", onTap, onZoomChanged)
    }
}

@Composable
internal fun ZoomablePage(
    bitmap: Bitmap,
    description: String,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
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
                    onTap = { offset -> onTap(offset) },
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

internal fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsDialog(
    brightness: Float,
    onBrightness: (Float) -> Unit,
    animate: Boolean,
    onAnimate: (Boolean) -> Unit,
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
    tapWhenZoomed: Boolean,
    onTapWhenZoomed: (Boolean) -> Unit,
    lockOrientation: Boolean,
    onLockOrientation: (Boolean) -> Unit,
    index: Int,
    total: Int,
    onJump: (Int) -> Unit,
    bookmarks: Set<Int>,
    onToggleBookmark: () -> Unit,
    onJumpBookmark: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var jump by remember(index, total) { mutableFloatStateOf(index.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("閱讀設定") },
        text = {
            Column {
                Text("亮度", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("暗", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = if (brightness < 0f) 0.5f else brightness,
                        onValueChange = onBrightness,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("亮", style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("翻頁動畫", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = animate, onCheckedChange = onAnimate)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("閱讀方向", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !rtl,
                            onClick = { onRtl(false) },
                            label = { Text("左→右") },
                        )
                        FilterChip(
                            selected = rtl,
                            onClick = { onRtl(true) },
                            label = { Text("右→左") },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("放大時允許點擊翻頁", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = tapWhenZoomed, onCheckedChange = onTapWhenZoomed)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("方向鎖定", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = lockOrientation, onCheckedChange = onLockOrientation)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text(
                    text = if (total > 0) "第 ${index + 1} / $total 頁" else "第 0 頁",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = jump.coerceIn(0f, (total - 1).coerceAtLeast(1).toFloat()),
                    onValueChange = { jump = it },
                    onValueChangeFinished = { onJump(jump.toInt()) },
                    valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                )

                TextButton(onClick = onToggleBookmark) {
                    Text(if (bookmarks.contains(index)) "移除目前頁書籤" else "加入目前頁書籤")
                }

                if (bookmarks.isNotEmpty()) {
                    Text("書籤", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        bookmarks.sorted().forEach { page ->
                            AssistChip(
                                onClick = { onJumpBookmark(page) },
                                label = { Text("第 ${page + 1} 頁") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        },
    )
}
