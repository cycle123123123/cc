package com.jmreader.ui.navigation

import androidx.compose.ui.unit.dp

/** 悬浮玻璃底栏的尺寸与动画参数，集中一处方便调。 */
object BottomBarMetrics {
    val Height = 72.dp
    val HorizontalMargin = 16.dp
    val BottomMargin = 8.dp

    /** 选中镜片的基础尺寸（拉伸时会在此基础上加宽、略微压扁）。 */
    val LensWidth = 84.dp
    val LensHeight = 58.dp

    /** 列表底部要留出的空间：底栏高度 + 外边距 + 安全余量。 */
    val ContentInset = 104.dp

    /** 背景采样周期（毫秒）。60ms ≈ 16fps。 */
    const val CaptureIntervalMs = 60L

    /** 采样缩放倍数：1/4 分辨率，读回成本很低。 */
    const val CaptureScale = 4
}
