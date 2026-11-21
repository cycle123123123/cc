package com.jmreader.ui.reader

/**
 * 阅读器的页码与缩放计算（纯函数，便于 JVM 单测）。
 *
 * 之前这些公式散在 [ReaderScreen] 的手势回调、跳页、音量键三处，
 * 边界值（0 页、最后一页、缩放回弹）容易各写一套。
 */
internal object ReaderGeometry {

    /** 把目标页夹进 `0..total-1`；没有页时返回 0。 */
    fun clampPage(target: Int, total: Int): Int =
        target.coerceIn(0, (total - 1).coerceAtLeast(0))

    /** 翻页（音量键 / 左右点击）：没有页时返回 0。 */
    fun stepPage(current: Int, step: Int, total: Int): Int =
        if (total <= 0) 0 else clampPage(current + step, total)

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** 条漫放大后可平移的边界：放大 n 倍时每侧多出 (n-1)/2 个视口宽。 */
    fun clampPanX(value: Float, scale: Float, viewportWidth: Int): Float {
        val max = viewportWidth * (scale - 1f) / 2f
        return if (max <= 0f) 0f else value.coerceIn(-max, max)
    }

    /** 以双击点的横坐标为中心缩放到 [targetScale] 时应有的水平偏移。 */
    fun panForScale(targetScale: Float, focusX: Float, viewportWidth: Int): Float {
        if (targetScale <= 1.01f) return 0f
        val max = viewportWidth * (targetScale - 1f) / 2f
        if (max <= 0f) return 0f
        return (-(focusX - viewportWidth / 2f) * (targetScale - 1f)).coerceIn(-max, max)
    }

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 3f
}
