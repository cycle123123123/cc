package com.jmreader.ui.navigation

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jmreader.ui.theme.IosLabelPrimary
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

// Android 13+ AGSL 液态玻璃边缘折射着色器：模拟曲面玻璃边缘折射与轻微色散。
private const val LIQUID_REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float cornerRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0) + float2(1e-5));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    if (halfSize.x <= 0.0 || halfSize.y <= 0.0) return content.eval(coord);
    float2 centered = coord - halfSize;

    float sd = sdRoundedRect(centered, halfSize, cornerRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float t = clamp(1.0 - (-sd / max(refractionHeight, 1.0)), 0.0, 1.0);
    float d = circleMap(t) * refractionAmount;
    float gradRadius = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 normal = gradSdRoundedRect(centered, halfSize, gradRadius);
    float lenC = length(centered);
    float2 radial = lenC > 0.001 ? centered / lenC : float2(0.0);
    float2 grad = normalize(normal + depthEffect * radial);

    float2 refracted = clamp(coord + d * grad, float2(0.0), size - float2(1.0));
    if (chromaticAberration <= 0.001) {
        return content.eval(refracted);
    }
    float2 disp = grad * (d * chromaticAberration);
    float2 rCoord = clamp(refracted - disp, float2(0.0), size - float2(1.0));
    float2 bCoord = clamp(refracted + disp, float2(0.0), size - float2(1.0));
    half r = content.eval(rCoord).r;
    half4 g = content.eval(refracted);
    half b = content.eval(bCoord).b;
    return half4(r, g.g, b, g.a);
}
"""

/**
 * 悬浮透明液态玻璃底栏（酷安 / iOS 26 风格）：
 * 轻磨砂透视 + 边缘曲面折射 + 无色偏透明玻璃镜片 + 跟手拖拽拉伸。
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
        targetValue = if (dragging) 1.05f else 1f,
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
                    elevation = 14.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                )
                .clip(CircleShape),
        ) {
            GlassBackdrop(source)

            // 清透中性玻璃底膜：低遮罩保留底层色彩与形状，无任何蓝紫偏色
            val tintBrush = if (isBlurred) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.14f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.94f),
                        Color(0xFFF6F6F8).copy(alpha = 0.90f),
                    ),
                )
            }
            Box(Modifier.matchParentSize().background(tintBrush))

            // 玻璃对角高光描边（单层迎光白边，去掉灰暗外框）
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.85f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            // 选中镜片：纯中性透明玻璃浮块（拖拽时更通透，静止时柔和磨砂白）
            val lensFill = if (dragging) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.20f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.42f),
                    ),
                )
            }
            Box(
                Modifier
                    .offset(x = lensX, y = lensY)
                    .size(width = lensWidth, height = lensHeight)
                    .scale(lensScale)
                    .shadow(
                        elevation = if (dragging) 6.dp else 2.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    )
                    .clip(CircleShape)
                    .background(lensFill)
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color.Black.copy(alpha = 0.06f),
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
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_REFRACTION_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
    var cachedEffect by remember { mutableStateOf<ComposeRenderEffect?>(null) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                clip = true
                shape = CircleShape
                val w = size.width
                val h = size.height
                if (w > 0f && h > 0f) {
                    if (cachedEffect == null || cachedSize != size) {
                        cachedSize = size
                        val blurPx = 7.dp.toPx()
                        val blur = AndroidRenderEffect.createBlurEffect(
                            blurPx,
                            blurPx,
                            Shader.TileMode.CLAMP,
                        )
                        cachedEffect = if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null
                        ) {
                            shader.setFloatUniform("size", w, h)
                            shader.setFloatUniform("cornerRadius", h * 0.5f)
                            shader.setFloatUniform("refractionHeight", 13.dp.toPx())
                            shader.setFloatUniform("refractionAmount", -18.dp.toPx())
                            shader.setFloatUniform("depthEffect", 0.25f)
                            shader.setFloatUniform("chromaticAberration", 0.14f)
                            val refract = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                            AndroidRenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
                        } else {
                            blur.asComposeRenderEffect()
                        }
                    }
                    renderEffect = cachedEffect
                }
            }
            .drawBehind {
                source.redrawTick
                val layer = source.layer ?: return@drawBehind
                if (!source.hasCapture) return@drawBehind
                val s = source.scale
                if (s != 1f) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawLayer(layer)
                    }
                } else {
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
        targetValue = if (selected) IosBlue else IosLabelPrimary.copy(alpha = 0.85f),
        animationSpec = tween(180),
        label = "tabTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
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
