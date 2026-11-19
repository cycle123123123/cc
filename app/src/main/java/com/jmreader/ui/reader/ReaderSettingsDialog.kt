package com.jmreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.core.JmConfig
import com.jmreader.ui.components.IosSwitch

private val SheetBg = Color(0xF51C1C1E)
private val SheetCard = Color(0xFF2C2C2E)
private val SheetCardBorder = Color(0x22FFFFFF)
private val SheetDivider = Color(0xFF3A3A3C)
private val SheetSecondary = Color(0xFF98989F)
private val SheetBlue = Color(0xFF0A84FF)

/**
 * iOS 风格底部半屏阅读设置抽屉：调整亮度、方向、开关时可实时透出背后的漫画页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsDialog(
    brightness: Float,
    onBrightness: (Float) -> Unit,
    animate: Boolean,
    onAnimate: (Boolean) -> Unit,
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
    tapWhenZoomed: Boolean,
    onTapWhenZoomed: (Boolean) -> Unit,
    lockOrientation: Boolean,
    onLockOrientation: (Boolean) -> Unit,
    index: Int,
    total: Int,
    onJump: (Int) -> Unit,
    bookmark: Int?,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: () -> Unit,
    onJumpBookmark: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var jump by remember(index, total) { mutableFloatStateOf(index.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.28f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "閱讀設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("完成", color = SheetBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── 亮度調節 ──
            SheetSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("螢幕亮度", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
                    if (brightness >= 0f) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x330A84FF))
                                .clickable { onBrightness(-1f) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("系統默認", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetBlue)
                        }
                    } else {
                        Text("跟隨系統", fontSize = 12.sp, color = SheetSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("暗", fontSize = 12.sp, color = SheetSecondary)
                    Slider(
                        value = if (brightness < 0f) 0.5f else brightness,
                        onValueChange = onBrightness,
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = SheetBlue,
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("亮", fontSize = 12.sp, color = SheetSecondary)
                }
            }

            // ── 翻頁方向 & 開關分組 ──
            SheetSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("橫向閱讀方向", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    DarkSegmentedToggle(
                        left = "左 → 右",
                        right = "右 → 左",
                        rightSelected = rtl,
                        onSelectRight = onRtl,
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = SheetDivider)
                SheetSwitchRow("翻頁動畫", animate, onAnimate)
                HorizontalDivider(thickness = 0.5.dp, color = SheetDivider)
                SheetSwitchRow("放大時允許點擊翻頁", tapWhenZoomed, onTapWhenZoomed)
                HorizontalDivider(thickness = 0.5.dp, color = SheetDivider)
                SheetSwitchRow("鎖定螢幕方向", lockOrientation, onLockOrientation)
            }

            // ── 頁碼跳轉與書籤 ──
            SheetSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("快速跳頁", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text(
                        text = if (total > 0) "第 ${jump.toInt().coerceIn(0, total - 1) + 1} / $total 頁" else "第 0 頁",
                        fontSize = 12.sp,
                        color = SheetSecondary,
                    )
                }
                Slider(
                    value = jump.coerceIn(0f, (total - 1).coerceAtLeast(1).toFloat()),
                    onValueChange = { jump = it },
                    onValueChangeFinished = { onJump(jump.toInt()) },
                    valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = SheetBlue,
                        activeTrackColor = SheetBlue,
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                    ),
                )
                HorizontalDivider(thickness = 0.5.dp, color = SheetDivider)
                Spacer(Modifier.height(6.dp))
                val bookmarkPage = bookmark
                if (bookmarkPage == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAddBookmark)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("將當前頁加入書籤", color = SheetBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已存書籤：第 ${bookmarkPage + 1} 頁",
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onJumpBookmark(bookmarkPage) }) {
                            Text("跳轉", color = SheetBlue, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onRemoveBookmark) {
                            Text("移除", color = Color(0xFFFF453A))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底欄「圖源」彈出抽屜，統一成深色 iOS 底部卡片形式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSourceSheet(
    shard: Int,
    onPick: (Int) -> Unit,
    onBenchmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.28f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
        ) {
            Text(
                text = "切換圖片線路",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            val cardShape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(SheetCard)
                    .border(0.5.dp, SheetCardBorder, cardShape),
            ) {
                SourceOptionRow(label = "自動選擇", selected = shard == 0, onClick = { onPick(0) })
                HorizontalDivider(thickness = 0.5.dp, color = SheetDivider, modifier = Modifier.padding(start = 14.dp))
                SourceOptionRow(label = "一鍵測速選最優", selected = shard == 5, onClick = onBenchmark)
                JmConfig.IMAGE_SHARDS.forEachIndexed { index, _ ->
                    val value = index + 1
                    HorizontalDivider(thickness = 0.5.dp, color = SheetDivider, modifier = Modifier.padding(start = 14.dp))
                    SourceOptionRow(
                        label = "圖源線路 $value",
                        selected = shard == value,
                        onClick = { onPick(value) },
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            ) {
                Text("取消", color = SheetBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SourceOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (selected) SheetBlue else Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetBlue)
        }
    }
}

@Composable
private fun SheetSectionCard(content: @Composable () -> Unit) {
    val cardShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(SheetCard)
            .border(0.5.dp, SheetCardBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SheetSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DarkSegmentedToggle(
    left: String,
    right: String,
    rightSelected: Boolean,
    onSelectRight: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF1C1C1E))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to left, true to right).forEach { (value, label) ->
            val selected = rightSelected == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Color(0xFF48484A) else Color.Transparent)
                    .clickable { onSelectRight(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) Color.White else SheetSecondary,
                )
            }
        }
    }
}
