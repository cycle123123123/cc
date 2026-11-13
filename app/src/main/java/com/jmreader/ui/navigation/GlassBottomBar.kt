package com.jmreader.ui.navigation

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosLabelSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class NavTabSpec(
    val tab: RootTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val NAV_TABS = listOf(
    NavTabSpec(RootTab.HOME, "首頁", Icons.Filled.Home, Icons.Outlined.Home),
    NavTabSpec(RootTab.DISCOVER, "發現", Icons.Filled.Explore, Icons.Outlined.Explore),
    NavTabSpec(RootTab.ME, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * iOS 26 风格的悬浮液态玻璃底栏：
 * 零拷贝背景模糊 + 顶部高光玻璃边框 + 跟手拖拽与果冻拉伸镜片。
 */
@Composable
internal fun GlassBottomBar(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    source: BackdropSource,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableIntStateOf(selected.ordinal) }
    var dragStretchTarget by remember { mutableFloatStateOf(0f) }

    // 动画单位是「第几个 tab」(0f..2f)，计算拉伸时再乘以每格 dp 宽度还原线速度。
    val lensPos = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) {
        if (dragging) return@LaunchedEffect
        committed = selected.ordinal
        lensPos.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(dampingRatio = 0.56f, stiffness = 480f),
        )
    }

    // 手指拖拽中途停顿 50ms 后，拉伸量平滑回落为 0。
    LaunchedEffect(dragStretchTarget, dragging) {
        if (dragging && dragStretchTarget > 0f) {
            delay(50)
            dragStretchTarget = 0f
        }
    }

    val dragStretch by animateFloatAsState(
        targetValue = if (dragging) dragStretchTarget else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 460f),
        label = "dragStretch",
    )
    val lensScale by animateFloatAsState(
        targetValue = if (dragging) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "lensScale",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = BottomBarMetrics.HorizontalMargin,
                end = BottomBarMetrics.HorizontalMargin,
                bottom = BottomBarMetrics.BottomMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val barWidth = maxWidth.coerceAtMost(BottomBarMetrics.MaxWidth)
        val itemWidth = barWidth / NAV_TABS.size
        val baseLensWidth = (itemWidth - BottomBarMetrics.LensHorizontalPadding * 2)
            .coerceAtMost(BottomBarMetrics.LensMaxWidth)

        // 按真实 dp/s 换算速度 -> 水平液态拉伸（点击切页与手指拖拽均生效）
        val animVelocityDp = abs(lensPos.velocity) * itemWidth.value
        val velocityStretch = (animVelocityDp * 0.022f)
            .coerceIn(0f, BottomBarMetrics.LensMaxStretch.value)
        val stretchDp = maxOf(velocityStretch, dragStretch).dp
        val squashDp = (stretchDp * 0.14f).coerceAtMost(3.dp)

        val lensWidth = (baseLensWidth + stretchDp)
            .coerceAtMost(barWidth - BottomBarMetrics.LensHorizontalPadding * 2)
        val lensHeight = BottomBarMetrics.LensHeight - squashDp
        val itemCenter = itemWidth * lensPos.value + itemWidth / 2
        val minLensX = BottomBarMetrics.LensHorizontalPadding
        val maxLensX = (barWidth - lensWidth - BottomBarMetrics.LensHorizontalPadding)
            .coerceAtLeast(minLensX)
        val lensX = (itemCenter - lensWidth / 2).coerceIn(minLensX, maxLensX)
        val lensY = (BottomBarMetrics.Height - lensHeight) / 2

        val isBlurred = source.supported && source.hasCapture

        Box(
            modifier = Modifier
                .width(barWidth)
                .height(BottomBarMetrics.Height)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    val changed = source.barLeftInWindow != bounds.left ||
                        source.barTopInWindow != bounds.top ||
                        source.barWidthPx != bounds.width ||
                        source.barHeightPx != bounds.height
                    source.barLeftInWindow = bounds.left
                    source.barTopInWindow = bounds.top
                    source.barWidthPx = bounds.width
                    source.barHeightPx = bounds.height
                    if (changed) source.requestCapture()
                }
                .pointerInput(itemWidth, NAV_TABS.size) {
                    val itemPx = itemWidth.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            committed = selected.ordinal
                            dragStretchTarget = 0f
                            scope.launch { lensPos.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val dragDp = with(density) { dragAmount.toDp().value }
                            dragStretchTarget = (abs(dragDp) * 1.5f)
                                .coerceIn(0f, BottomBarMetrics.LensMaxStretch.value)

                            val next = (lensPos.value + dragAmount / itemPx)
                                .coerceIn(0f, (NAV_TABS.size - 1).toFloat())
                            scope.launch { lensPos.snapTo(next) }

                            // 镜片中心越过两格中线立即切换页面，内容实时跟随
                            val near = next.roundToInt().coerceIn(0, NAV_TABS.size - 1)
                            if (near != committed) {
                                committed = near
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(NAV_TABS[near].tab)
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            dragStretchTarget = 0f
                            val target = lensPos.value.roundToInt().coerceIn(0, NAV_TABS.size - 1)
                            scope.launch {
                                lensPos.animateTo(
                                    target.toFloat(),
                                    spring(dampingRatio = 0.56f, stiffness = 480f),
                                )
                            }
                            if (target != committed) {
                                committed = target
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(NAV_TABS[target].tab)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragStretchTarget = 0f
                            scope.launch {
                                lensPos.animateTo(
                                    committed.toFloat(),
                                    spring(dampingRatio = 0.56f, stiffness = 480f),
                                )
                            }
                        },
                    )
                }
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(CircleShape),
        ) {
            GlassBackdrop(source)

            // 垂直玻璃光泽层：有真实模糊时透出底层色彩，无模糊时保持高对比磨砂白
            val tintBrush = if (isBlurred) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color.White.copy(alpha = 0.54f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.96f),
                        Color(0xFFF5F5F9).copy(alpha = 0.92f),
                    ),
                )
            }
            Box(Modifier.matchParentSize().background(tintBrush))

            // 顶部迎光高光 + 底部微阴影的立体玻璃描边
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = 0.75.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.92f),
                                Color.Black.copy(alpha = 0.07f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            // 选中液态镜片
            Box(
                Modifier
                    .offset(x = lensX, y = lensY)
                    .size(width = lensWidth, height = lensHeight)
                    .scale(lensScale)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.48f),
                                IosBlue.copy(alpha = 0.14f),
                            ),
                        ),
                    )
                    .background(IosBlue.copy(alpha = 0.08f))
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.90f),
                                IosBlue.copy(alpha = 0.24f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            Row(Modifier.fillMaxSize()) {
                NAV_TABS.forEach { spec ->
                    val isSelected = selected == spec.tab
                    GlassTabItem(
                        spec = spec,
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(spec.tab)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.GlassBackdrop(source: BackdropSource) {
    if (!source.supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .blur(20.dp, BlurredEdgeTreatment.Rectangle)
            .drawBehind {
                source.redrawTick
                val layer = source.layer ?: return@drawBehind
                if (!source.hasCapture) return@drawBehind
                val s = source.scale
                scale(s, s, pivot = Offset.Zero) {
                    drawLayer(layer)
                }
            },
    )
}

@Composable
private fun RowScope.GlassTabItem(
    spec: NavTabSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) IosBlue else IosLabelSecondary,
        animationSpec = tween(180),
        label = "tabTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "tabScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) spec.selectedIcon else spec.unselectedIcon,
            contentDescription = spec.label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = spec.label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
