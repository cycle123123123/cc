package com.jmreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmreader.data.model.ChapterEntry
private val ReaderAccentBlue = Color(0xFF0A84FF)
private val ReaderBlueSoft = Color(0x330A84FF)

/**
 * 悬浮深色磨砂玻璃顶栏：与首页液态玻璃底栏呼应。
 */
@Composable
internal fun ReaderTopBar(
    title: String,
    sourceLabel: String,
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xDE242428), Color(0xCC161618)),
                ),
            )
            .border(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f)),
                ),
                shape = CircleShape,
            )
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(ReaderBlueSoft)
                .clickable(onClick = onOpenSource)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = sourceLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReaderAccentBlue,
                maxLines = 1,
            )
        }
    }
}

/**
 * 悬浮深色玻璃控制舱底栏：上排上一话/进度条/下一话，下排 4 个圆角胶囊快捷按钮。
 */
@Composable
internal fun ReaderBottomBar(
    index: Int,
    total: Int,
    previous: ChapterEntry?,
    next: ChapterEntry?,
    hasChapters: Boolean,
    onPrevious: (ChapterEntry?) -> Unit,
    onNext: (ChapterEntry?) -> Unit,
    onJump: (Int) -> Unit,
    isVertical: Boolean,
    onToggleMode: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val idle = Color.White.copy(alpha = 0.78f)
    val maxPage = (total - 1).coerceAtLeast(0).toFloat()
    var dragPage by remember { mutableFloatStateOf(index.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 外部页码变化时同步；拖动过程中不被外部值覆盖
    LaunchedEffect(index, total) {
        if (!isDragging) dragPage = index.toFloat().coerceIn(0f, maxPage)
    }

    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, shape, clip = false)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE6242428), Color(0xD9141416)),
                    ),
                )
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f)),
                    ),
                    shape = shape,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (isDragging && total > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 4.dp)
                        .clip(CircleShape)
                        .background(ReaderBlueSoft)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "第 ${dragPage.toInt().coerceIn(0, total - 1) + 1} / $total 頁",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            // ── 第一行：進度條 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderChapterButton(
                    text = "上一話",
                    enabled = hasChapters && previous != null,
                    onClick = { onPrevious(previous) },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (total > 1) {
                        Slider(
                            value = dragPage.coerceIn(0f, maxPage),
                            onValueChange = {
                                isDragging = true
                                dragPage = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onJump(dragPage.toInt().coerceIn(0, total - 1))
                            },
                            valueRange = 0f..maxPage,
                            colors = SliderDefaults.colors(
                                thumbColor = ReaderAccentBlue,
                                activeTrackColor = ReaderAccentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = if (total > 0) {
                            "${dragPage.toInt().coerceIn(0, total - 1) + 1} / $total"
                        } else {
                            "— / —"
                        },
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                }

                ReaderChapterButton(
                    text = "下一話",
                    enabled = hasChapters && next != null,
                    onClick = { onNext(next) },
                )
            }

            // ── 第二行：快捷按鈕 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderBarAction(
                    icon = if (isVertical) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
                    label = if (isVertical) "縱向" else "橫向",
                    tint = idle,
                    active = false,
                    onClick = onToggleMode,
                )
                ReaderBarAction(
                    icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = "書籤",
                    tint = if (isBookmarked) ReaderAccentBlue else idle,
                    active = isBookmarked,
                    onClick = onToggleBookmark,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Dns,
                    label = "圖源",
                    tint = idle,
                    active = false,
                    onClick = onOpenSource,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Settings,
                    label = "設定",
                    tint = idle,
                    active = false,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ReaderChapterButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.28f),
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.ReaderBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) ReaderBlueSoft else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
