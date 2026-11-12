package com.jmreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect

/**
 * 底栏背后的「真实内容」采样器。
 *
 * Compose 的 `Modifier.blur` 只能模糊自己，`Window` 级 blur 又只能糊窗口外的东西，
 * 所以这里用 [GraphicsLayer] 把内容层重新录一份 1/4 分辨率的底部条带，
 * 再交给底栏当背景做模糊——效果等价于酷安那套 RenderNode + RenderEffect。
 */
@Stable
class BackdropSource {

    internal var layer: GraphicsLayer? = null

    /** 下一帧是否需要录制一次。由定时器置位，绘制时消费。 */
    @Volatile
    internal var wantCapture: Boolean = false

    /** 已经录好，等待读回。 */
    @Volatile
    internal var pendingRead: Boolean = false

    /** 内容根节点的窗口坐标原点，用来把底栏的窗口坐标换算成内容坐标。 */
    @Volatile
    internal var contentTopInWindow: Float = 0f

    /** 底栏在窗口中的位置与高度（px）。 */
    @Volatile
    internal var barTopInWindow: Float = -1f

    @Volatile
    internal var barHeightPx: Float = 0f

    /** 录音结果；null 表示还没采到（或设备不支持），底栏退化为半透明白。 */
    var image by mutableStateOf<ImageBitmap?>(null)
        internal set

    /** 采样结果只打一条日志，避免每帧刷屏。 */
    internal var logged: Boolean = false

    internal val scale = BottomBarMetrics.CaptureScale
}

@Composable
fun rememberBackdropSource(): BackdropSource {
    val source = remember { BackdropSource() }
    val layer = rememberGraphicsLayer()
    SideEffect { source.layer = layer }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(BottomBarMetrics.CaptureIntervalMs)
            if (source.barHeightPx <= 0f) continue
            source.wantCapture = true
            // 等一帧让 draw 阶段把内容录进 layer
            delay(24)
            if (source.pendingRead) {
                source.pendingRead = false
                val result = runCatching { layer.toImageBitmap() }
                val bitmap = result.getOrNull()
                if (bitmap != null) {
                    source.image = bitmap
                    if (!source.logged) {
                        source.logged = true
                        Log.d("GlassBar", "capture ok ${bitmap.width}x${bitmap.height}")
                    }
                } else if (!source.logged) {
                    source.logged = true
                    Log.w("GlassBar", "capture failed: ${result.exceptionOrNull()}")
                }
            }
        }
    }
    return source
}

/**
 * 把内容绘制两遍：一遍正常输出，一遍按 1/4 缩放录制底栏那一条，供玻璃层采样。
 * 只有定时器置位的那一帧才会多画一次。
 */
fun Modifier.backdropSource(source: BackdropSource): Modifier = drawWithContent {
    drawContent()

    val layer = source.layer ?: return@drawWithContent
    if (!source.wantCapture) return@drawWithContent
    source.wantCapture = false

    val barHeight = source.barHeightPx
    if (barHeight <= 0f) return@drawWithContent
    val top = source.barTopInWindow - source.contentTopInWindow
    if (top < 0f) return@drawWithContent

    val s = source.scale
    val width = (size.width / s).toInt().coerceAtLeast(1)
    val height = (barHeight / s).toInt().coerceAtLeast(1)

    layer.record(this, layoutDirection, IntSize(width, height)) {
        scale(1f / s, 1f / s, pivot = Offset.Zero) {
            translate(0f, -top) {
                this@drawWithContent.drawContent()
            }
        }
    }
    source.pendingRead = true
}
