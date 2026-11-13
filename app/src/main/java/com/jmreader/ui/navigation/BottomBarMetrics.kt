package com.jmreader.ui.navigation

import androidx.compose.ui.unit.dp

/** 悬浮玻璃底栏的尺寸与模糊参数，集中一处方便调。 */
object BottomBarMetrics {
    val Height = 64.dp
    val MaxWidth = 420.dp
    val HorizontalMargin = 16.dp
    val BottomMargin = 8.dp

    /** 选中镜片的基础边距与尺寸（拉伸时会在此基础上加宽、略微压扁）。 */
    val LensHorizontalPadding = 5.dp
    val LensHeight = 52.dp
    val LensMaxWidth = 112.dp
    val LensMaxStretch = 20.dp

    /** 列表底部要留出的空间：底栏高度 + 外边距 + 安全余量。 */
    val ContentInset = 96.dp

    /** 1:1 零拷贝 GPU 图层录制，保证边缘折射与轻磨砂锐利通透。 */
    const val CaptureScale = 1f
}
