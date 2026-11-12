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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosLabelSecondary
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

    val lens = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) {
        lens.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        )
    }

    // 读 value 让动画期间每帧重组，velocity 才能驱动拉伸
    val progress = lens.value
    val velocity = lens.velocity

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BottomBarMetrics.HorizontalMargin)
            .navigationBarsPadding()
            .padding(bottom = BottomBarMetrics.BottomMargin),
    ) {
        val barWidthPx = constraints.maxWidth.toFloat()
        val itemWidthPx = barWidthPx / tabs.size
        val baseLensPx = with(density) { BottomBarMetrics.LensWidth.toPx() }
        val barHeightPx = with(density) { BottomBarMetrics.Height.toPx() }

        // 速度 -> 拉伸量（px）；停下后自然回到基础宽度
        val stretch = (abs(velocity) * 0.06f).coerceIn(0f, 56f)
        val lensWidthPx = baseLensPx + stretch
        val lensHeightPx = (barHeightPx - with(density) { 12.dp.toPx() } - stretch * 0.12f)
            .coerceAtLeast(with(density) { 40.dp.toPx() })
        val lensX = (itemWidthPx * progress - stretch / 2f)
            .coerceIn(0f, (barWidthPx - lensWidthPx).coerceAtLeast(0f))
        val lensY = (barHeightPx - lensHeightPx) / 2f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarMetrics.Height)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    source.barTopInWindow = bounds.top
                    source.barHeightPx = bounds.height
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
                    .background(Color.White.copy(alpha = if (source.image == null) 0.88f else 0.60f)),
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
                    .offset { IntOffset(lensX.roundToInt(), lensY.roundToInt()) }
                    .size(
                        width = with(density) { lensWidthPx.toDp() },
                        height = with(density) { lensHeightPx.toDp() },
                    )
                    .clip(RoundedCornerShape(percent = 50))
                    .background(IosBlue.copy(alpha = 0.13f))
                    .border(0.5.dp, IosBlue.copy(alpha = 0.20f), RoundedCornerShape(percent = 50)),
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
        // 1/4 采样本身就足够糊，旧系统不再叠加昂贵的模糊
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

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
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
