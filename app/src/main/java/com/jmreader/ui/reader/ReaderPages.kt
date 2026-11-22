package com.jmreader.ui.reader

import android.graphics.Bitmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmreader.data.page.PageStore
import com.jmreader.ui.theme.IosBlue
/**
 * 竖向条漫的一页。
 *
 * 单页自己管理加载状态：失败时可以点击重试，长按可交给外层弹「保存到相册」。
 * 缩放由外层容器统一处理，这里只负责单击/双击/长按的判定。
 */
@Composable
internal fun VerticalPage(
    store: PageStore,
    url: String,
    page: Int,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (Bitmap) -> Unit,
) {
    var bitmap by remember(page) { mutableStateOf(store.cached(page)) }
    var failed by remember(page) { mutableStateOf(false) }
    var reloadKey by remember(page) { mutableIntStateOf(0) }

    LaunchedEffect(page, url, reloadKey) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        val loaded = store.load(url, page)
        if (loaded != null) bitmap = loaded else failed = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .pointerInput(page) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap(it) },
                    onLongPress = { bitmap?.let(onLongPress) },
                )
            },
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

            failed -> PageFailed(
                page = page,
                isLocal = url.startsWith(LOCAL_PREFIX),
                onRetry = { reloadKey++ },
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
    onLongPress: (Bitmap) -> Unit,
    animate: Boolean,
    pageOffset: Float,
) {
    var bitmap by remember(page) { mutableStateOf(store.cached(page)) }
    var failed by remember(page) { mutableStateOf(false) }
    var reloadKey by remember(page) { mutableIntStateOf(0) }

    LaunchedEffect(page, url, reloadKey) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        val loaded = store.load(url, page)
        if (loaded != null) bitmap = loaded else failed = true
    }

    val image = bitmap
    if (image == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) {
                PageFailed(
                    page = page,
                    isLocal = url.startsWith(LOCAL_PREFIX),
                    onRetry = { reloadKey++ },
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
        ZoomablePage(image, "第 $page 頁", onTap, onZoomChanged, onLongPress)
    }
}

@Composable
internal fun ZoomablePage(
    bitmap: Bitmap,
    description: String,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onLongPress: (Bitmap) -> Unit,
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
                    onLongPress = { onLongPress(bitmap) },
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

/** 加载失败/未下载的占位；远程图片失败时给一个可点的重试胶囊。 */
@Composable
private fun PageFailed(page: Int, isLocal: Boolean, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isLocal) "第 $page 頁未下載" else "第 $page 頁載入失敗",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!isLocal) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(IosBlue)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("點此重試", color = Color.White, fontSize = 12.sp)
            }
        }
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

/** 离线占位 URL 的前缀（`local://{cid}/{原图文件名}`）。 */
internal const val LOCAL_PREFIX = "local://"
