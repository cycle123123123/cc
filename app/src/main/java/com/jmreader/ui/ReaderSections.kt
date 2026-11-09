package com.jmreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.ChapterEntry
import com.jmreader.data.PageStore

@Composable
internal fun ReaderBottomBar(
    index: Int,
    total: Int,
    previous: ChapterEntry?,
    next: ChapterEntry?,
    hasChapters: Boolean,
    onPrevious: (ChapterEntry?) -> Unit,
    onNext: (ChapterEntry?) -> Unit,
    onJump: (Int) -> Unit,
    isVertical: Boolean,
    onToggleMode: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accent = IosBlue
    val idle = Color.White.copy(alpha = 0.72f)
    val maxPage = (total - 1).coerceAtLeast(0).toFloat()
    var dragPage by remember { mutableFloatStateOf(index.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 外部页码变化时同步；拖动过程中不被外部值覆盖
    LaunchedEffect(index, total) {
        if (!isDragging) dragPage = index.toFloat().coerceIn(0f, maxPage)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.84f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        ) {
            // ── 第一行：進度條 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderChapterButton(
                    text = "上一話",
                    enabled = hasChapters && previous != null,
                    onClick = { onPrevious(previous) },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (total > 1) {
                        Slider(
                            value = dragPage.coerceIn(0f, maxPage),
                            onValueChange = {
                                isDragging = true
                                dragPage = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onJump(dragPage.toInt().coerceIn(0, total - 1))
                            },
                            valueRange = 0f..maxPage,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = if (total > 0) {
                            "${dragPage.toInt().coerceIn(0, total - 1) + 1} / $total"
                        } else {
                            "— / —"
                        },
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                }

                ReaderChapterButton(
                    text = "下一話",
                    enabled = hasChapters && next != null,
                    onClick = { onNext(next) },
                )
            }

            // ── 第二行：快捷按鈕 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderBarAction(
                    icon = if (isVertical) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
                    label = if (isVertical) "縱向" else "橫向",
                    tint = idle,
                    onClick = onToggleMode,
                )
                ReaderBarAction(
                    icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = "書籤",
                    tint = if (isBookmarked) accent else idle,
                    onClick = onToggleBookmark,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Dns,
                    label = "圖源",
                    tint = idle,
                    onClick = onOpenSource,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Settings,
                    label = "更多設定",
                    tint = idle,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ReaderChapterButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.28f),
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.ReaderBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
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

@OptIn(ExperimentalMaterial3Api::class)
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
    bookmark: Int?,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: () -> Unit,
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

                val bookmarkPage = bookmark
                if (bookmarkPage == null) {
                    TextButton(onClick = onAddBookmark) { Text("當前頁加入書籤") }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "第 ${bookmarkPage + 1} 頁",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onJumpBookmark(bookmarkPage) }) { Text("跳轉") }
                        TextButton(onClick = onRemoveBookmark) { Text("移除") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        },
    )
}
