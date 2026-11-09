package com.jmreader.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.jmreader.data.update.UpdateChecker
import com.jmreader.data.update.UpdateInfo
import com.jmreader.ui.common.rememberToaster

/**
 * 应用内更新弹窗：说明、安装包大小、下载进度、下载 + 静默安装（失败回退系统安装器）。
 * 状态仍由 [AppRoot] 持有，这里只通过回调改它。
 */
@Composable
internal fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    updating: Boolean,
    progress: Int,
    updateChecker: UpdateChecker,
    scope: CoroutineScope,
    onUpdatingChange: (Boolean) -> Unit,
    onProgressChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val toast = rememberToaster()

    AlertDialog(
        onDismissRequest = { if (!updating) onDismiss() },
        title = { Text("發現新版本 v${info.version}") },
        text = {
            Column {
                Text(
                    text = info.notes.ifBlank { "本次更新沒有寫說明。" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "目前版本 $currentVersion"
                        + if (info.sizeBytes > 0) " · 安裝包 ${info.sizeBytes / 1024 / 1024} MB" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (updating) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text(
                        text = "下載中 $progress%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !updating,
                onClick = {
                    onUpdatingChange(true)
                    onProgressChange(0)
                    scope.launch {
                        runCatching {
                            val file = updateChecker.download(info.downloadUrl) { onProgressChange(it) }
                            // 有 root 就静默安装，失败再走系统安装器
                            if (!updateChecker.tryRootInstall(file)) {
                                updateChecker.install(file)
                            }
                        }.onFailure {
                            toast(
                                "更新失敗：${it.message ?: "請稍後再試"}",
                                true,
                            )
                        }
                        onUpdatingChange(false)
                        onDismiss()
                    }
                },
            ) {
                Text(if (updating) "更新中…" else "更新")
            }
        },
        dismissButton = {
            TextButton(enabled = !updating, onClick = onDismiss) { Text("稍後") }
        },
    )
}
