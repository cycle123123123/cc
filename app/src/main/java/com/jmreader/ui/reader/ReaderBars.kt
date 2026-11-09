package com.jmreader.ui.reader

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.model.ChapterEntry
import com.jmreader.ui.theme.IosBlue

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
    val accent = IosBlue
    val idle = Color.White.copy(alpha = 0.72f)
    val maxPage = (total - 1).coerceAtLeast(0).toFloat()
    var dragPage by remember { mutableFloatStateOf(index.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 外部页码变化时同步；拖动过程中不被外部值覆盖
    LaunchedEffect(index, total) {
        if (!isDragging) dragPage = index.toFloat().coerceIn(0f, maxPage)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.84f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        ) {
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
                                thumbColor = accent,
                                activeTrackColor = accent,
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
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderBarAction(
                    icon = if (isVertical) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
                    label = if (isVertical) "縱向" else "橫向",
                    tint = idle,
                    onClick = onToggleMode,
                )
                ReaderBarAction(
                    icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = "書籤",
                    tint = if (isBookmarked) accent else idle,
                    onClick = onToggleBookmark,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Dns,
                    label = "圖源",
                    tint = idle,
                    onClick = onOpenSource,
                )
                ReaderBarAction(
                    icon = Icons.Filled.Settings,
                    label = "更多設定",
                    tint = idle,
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
            .width(58.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
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
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
