package com.jmreader.ui.navigation

import android.os.Build
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 底栏背后的「真实内容」零拷贝 GPU 采样器。
 *
 * 使用 [GraphicsLayer] 录制底栏正下方的 1/2 条带，
 * 底栏直接通过 `drawLayer` 绘制该图层并叠加轻磨砂 + AGSL 边缘折射：
 * - 不做 `toImageBitmap()` GPU→CPU 读回与 Bitmap 分配；
 * - 录制按 [captureTick] 门控：同一 tick 只录一次，静止时完全不产生绘制开销；
 * - 采集来源三路互补：列表滚动（nestedScroll）、底栏位移/切页（显式请求）、
 *   View 绘制（OnDrawListener，80ms 节流）兜住异步图片与数据刷新；
 * - Android 12 以下自动关闭录制，退化为高可读性的磨砂半透白胶囊。
 */
@Stable
class BackdropSource {

    internal val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    internal var layer: GraphicsLayer? = null

    /** 底栏可见时才允许采样；进入详情页/阅读器时暂停录制。 */
    internal var enabled: Boolean = true

    /** 内容根节点的窗口坐标原点，用来把底栏的窗口坐标换算成内容坐标。 */
    @Volatile
    internal var contentLeftInWindow: Float = 0f

    @Volatile
    internal var contentTopInWindow: Float = 0f

    /** 底栏在窗口中的位置与尺寸（px）。 */
    @Volatile
    internal var barLeftInWindow: Float = 0f

    @Volatile
    internal var barTopInWindow: Float = -1f

    @Volatile
    internal var barWidthPx: Float = 0f

    @Volatile
    internal var barHeightPx: Float = 0f

    /** 是否已至少完成一次有效录制，用于决定是否启用高透光玻璃态。 */
    internal var hasCapture by mutableStateOf(false)

    /** 录制请求计数；内容层只在这个值变化时重录一次。 */
    internal var captureTick by mutableLongStateOf(0L)

    /** 已录制的 tick；与 [captureTick] 相等说明画面是最新的。 */
    internal var recordedTick: Long = -1L

    /** 通知玻璃底栏重绘的信号（仅由底栏读取，避免与内容层形成循环）。 */
    internal var redrawTick by mutableLongStateOf(0L)

    @Volatile
    internal var pendingNotify: Boolean = false

    /** 上一次发起采样的时间戳，用于节流（主线程读写）。 */
    internal var lastRequestMs: Long = 0L

    internal val scale: Float = BottomBarMetrics.CaptureScale

    /**
     * 请求重新录制。[force] 为 true 时跳过节流（切页、底栏尺寸变化）；
     * 其余来源（滚动、View 绘制兜底）按 [BottomBarMetrics.CaptureThrottleMs] 节流。
     */
    fun requestCapture(force: Boolean = false) {
        if (!supported || !enabled) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastRequestMs < BottomBarMetrics.CaptureThrottleMs) return
        lastRequestMs = now
        captureTick++
    }

    /** OnDrawListener 用：先判断是否已过节流窗口，避免每帧都往消息队列丢 Runnable。 */
    internal fun probeDue(): Boolean {
        if (!supported || !enabled) return false
        return SystemClock.uptimeMillis() - lastRequestMs >= BottomBarMetrics.CaptureThrottleMs
    }
}

@Composable
fun rememberBackdropSource(enabled: Boolean = true): BackdropSource {
    val source = remember { BackdropSource() }
    val layer = if (source.supported) rememberGraphicsLayer() else null
    SideEffect {
        source.layer = layer
        source.enabled = enabled
    }
    return source
}

/**
 * 记录内容层坐标，并在滚动/切页/内容重绘时按需录制底栏正下方区域。
 */
@Composable
fun Modifier.backdropSource(source: BackdropSource): Modifier {
    if (!source.supported) return this
    val view = LocalView.current

    // 兜底：异步图片解码完成、下拉刷新、数据替换都不会走 nestedScroll，
    // 这里跟随 View 的绘制补一次采样（80ms 节流，静止时 View 不重绘就不会触发）。
    DisposableEffect(view, source) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener {
            if (source.probeDue()) view.post { source.requestCapture() }
        }
        observer.addOnDrawListener(listener)
        onDispose {
            runCatching {
                if (observer.isAlive) observer.removeOnDrawListener(listener)
            }
        }
    }

    val scrollObserver = remember(source) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                sourceType: NestedScrollSource,
            ): Offset {
                if (consumed.x != 0f || consumed.y != 0f) {
                    source.requestCapture()
                }
                return Offset.Zero
            }
        }
    }

    return this
        .nestedScroll(scrollObserver)
        .onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            if (pos.x != source.contentLeftInWindow || pos.y != source.contentTopInWindow) {
                source.contentLeftInWindow = pos.x
                source.contentTopInWindow = pos.y
                source.requestCapture(force = true)
            }
        }
        .drawWithContent {
            drawContent()

            // 只在有新的录制请求时重画一次内容条带；静止画面零额外开销。
            val tick = source.captureTick
            if (tick == source.recordedTick) return@drawWithContent

            val layer = source.layer ?: return@drawWithContent
            if (!source.enabled) return@drawWithContent

            val barW = source.barWidthPx
            val barH = source.barHeightPx
            if (barW <= 0f || barH <= 0f || source.barTopInWindow < 0f) return@drawWithContent

            val left = source.barLeftInWindow - source.contentLeftInWindow
            val top = source.barTopInWindow - source.contentTopInWindow
            val s = source.scale
            val width = (barW / s).roundToInt().coerceAtLeast(1)
            val height = (barH / s).roundToInt().coerceAtLeast(1)

            layer.record(IntSize(width, height)) {
                if (s != 1f) {
                    scale(1f / s, 1f / s, pivot = Offset.Zero) {
                        translate(-left, -top) {
                            this@drawWithContent.drawContent()
                        }
                    }
                } else {
                    translate(-left, -top) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
            source.recordedTick = tick

            if (!source.pendingNotify) {
                source.pendingNotify = true
                view.postOnAnimation {
                    source.pendingNotify = false
                    if (!source.hasCapture) source.hasCapture = true
                    source.redrawTick++
                }
            }
        }
}
