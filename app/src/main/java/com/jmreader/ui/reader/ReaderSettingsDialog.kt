package com.jmreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("閱讀設定") },
        text = {
            Column {
                Text("亮度", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("暗", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = if (brightness < 0f) 0.5f else brightness,
                        onValueChange = onBrightness,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("亮", style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("翻頁動畫", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = animate, onCheckedChange = onAnimate)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("閱讀方向", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !rtl,
                            onClick = { onRtl(false) },
                            label = { Text("左→右") },
                        )
                        FilterChip(
                            selected = rtl,
                            onClick = { onRtl(true) },
                            label = { Text("右→左") },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("放大時允許點擊翻頁", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = tapWhenZoomed, onCheckedChange = onTapWhenZoomed)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("方向鎖定", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = lockOrientation, onCheckedChange = onLockOrientation)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text(
                    text = if (total > 0) "第 ${index + 1} / $total 頁" else "第 0 頁",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = jump.coerceIn(0f, (total - 1).coerceAtLeast(1).toFloat()),
                    onValueChange = { jump = it },
                    onValueChangeFinished = { onJump(jump.toInt()) },
                    valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                )

                val bookmarkPage = bookmark
                if (bookmarkPage == null) {
                    TextButton(onClick = onAddBookmark) { Text("當前頁加入書籤") }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "第 ${bookmarkPage + 1} 頁",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onJumpBookmark(bookmarkPage) }) { Text("跳轉") }
                        TextButton(onClick = onRemoveBookmark) { Text("移除") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        },
    )
}
