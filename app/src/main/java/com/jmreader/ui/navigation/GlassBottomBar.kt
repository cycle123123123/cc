package com.jmreader.ui.navigation

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosLabelSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS 26 风格的悬浮玻璃底栏：
 * 白色胶囊 + 真实背景模糊 + 会滑动/拉伸的选中镜片。
 */
@Composable
internal fun GlassBottomBar(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    source: BackdropSource,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        Triple(RootTab.HOME, "首頁", Icons.Filled.Home),
        Triple(RootTab.DISCOVER, "發現", Icons.Filled.Explore),
        Triple(RootTab.ME, "我的", Icons.Filled.Person),
    )
    val shape = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(selected.ordinal) }


    // 用「第几个 tab」作为动画单位，避免 px/dp 混算
    val lens = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) {
        if (dragging) return@LaunchedEffect
        committed = selected.ordinal
        lens.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        )
    }

    val progress = lens.value
    val velocityDp = with(density) { lens.velocity.toDp().value }
    // 速度 -> 拉伸量（dp），停下自然回落到基础宽度
    val stretch = (abs(velocityDp) * 0.02f).coerceIn(0f, 20f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BottomBarMetrics.HorizontalMargin)
            .navigationBarsPadding()
            .padding(bottom = BottomBarMetrics.BottomMargin),
    ) {
        val itemWidth: Dp = maxWidth / tabs.size
        val lensWidth: Dp = BottomBarMetrics.LensWidth + stretch.dp
        val lensHeight: Dp = BottomBarMetrics.Height - 14.dp
        // 镜片中心对齐当前 item 的中心
        val itemCenter = itemWidth * progress + itemWidth / 2
        val lensX = itemCenter - lensWidth / 2
        val lensY = (BottomBarMetrics.Height - lensHeight) / 2

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarMetrics.Height)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    source.barTopInWindow = bounds.top
                    source.barHeightPx = bounds.height
                }
                .pointerInput(itemWidth, tabs.size) {
                    val itemPx = itemWidth.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            committed = selected.ordinal
                            scope.launch { lens.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (lens.value + dragAmount / itemPx)
                                .coerceIn(0f, (tabs.size - 1).toFloat())
                            scope.launch { lens.snapTo(next) }
                            // 镜片中心越过两格中线就立即切页（内容跟着变，镜片继续跟手）
                            val near = next.roundToInt().coerceIn(0, tabs.size - 1)
                            if (near != committed) {
                                committed = near
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tabs[near].first)
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            val target = lens.value.roundToInt().coerceIn(0, tabs.size - 1)
                            // 无论是否换了 tab，都要把镜片吸附到目标格，
                            // 否则停在两格之间时 selected 没变化，镜片会卡住。
                            scope.launch {
                                lens.animateTo(
                                    target.toFloat(),
                                    spring(dampingRatio = 0.55f, stiffness = 500f),
                                )
                            }
                            if (target != committed) {
                                committed = target
                                onSelect(tabs[target].first)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                lens.animateTo(
                                    committed.toFloat(),
                                    spring(dampingRatio = 0.55f, stiffness = 500f),
                                )
                            }
                        },
                    )
                }
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(shape),
        ) {
            GlassBackdrop(source.image)

            // 白色染色：有模糊图时更透，没有时更实（保证可读性）
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = if (source.image == null) 0.88f else 0.62f)),
            )
            // 高光描边
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.5.dp, Color.White.copy(alpha = 0.60f), shape),
            )

            // 选中镜片
            Box(
                Modifier
                    .offset(x = lensX, y = lensY)
                    .size(width = lensWidth, height = lensHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(IosBlue.copy(alpha = 0.14f))
                    .border(0.75.dp, IosBlue.copy(alpha = 0.22f), RoundedCornerShape(percent = 50)),
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEach { (rootTab, label, icon) ->
                    GlassTabItem(
                        label = label,
                        icon = icon,
                        selected = selected == rootTab,
                        onClick = { onSelect(rootTab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.GlassBackdrop(image: ImageBitmap?) {
    if (image == null) return
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(16.dp)
    } else {
        Modifier
    }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .matchParentSize()
            .then(blurModifier)
            .alpha(0.96f),
    )
}

@Composable
private fun RowScope.GlassTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) IosBlue else IosLabelSecondary,
        animationSpec = tween(180),
        label = "tabTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
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
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
