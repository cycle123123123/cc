package com.jmreader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.local.BackupManager
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.ui.theme.IosSeparator
import com.jmreader.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import com.jmreader.ui.theme.IosHairline

/**
 * 「工具與設定」分組：未登入 / 已登入共用，四項入口保持一致。
 * [versionName] 只作為「檢查更新」行的右側附註，不影響點擊行為。
 */
@Composable
internal fun SettingsGroup(
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onStats: () -> Unit,
    onDiagnostics: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    versionName: String? = null,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val prefs = container.appPrefs
    val toast = rememberToaster()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    // 备份额外状态：SAF 选择路径后异步读写，避免主线程做大文件 IO
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = BackupManager.encode(
                    BackupManager.collect(
                        history = container.readingHistory,
                        filter = container.contentFilter,
                        stats = container.readingStats,
                        search = container.searchHistory,
                        reader = container.readerPrefs,
                    ),
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("無法寫入備份檔")
                }
            }
            toast(result.fold({ "已匯出備份" }, { "匯出失敗：${it.message ?: "未知錯誤"}" }))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: error("無法讀取備份檔")
                }
                val data = BackupManager.decode(text)
                BackupManager.apply(
                    data = data,
                    history = container.readingHistory,
                    filter = container.contentFilter,
                    stats = container.readingStats,
                    search = container.searchHistory,
                    reader = container.readerPrefs,
                )
            }
            toast(
                result.fold(
                    onSuccess = { "已恢復 $it 條數據" },
                    onFailure = { "恢復失敗：${it.message ?: "格式不正確"}" },
                ),
            )
        }
    }
    Column {
        Text(
            text = "工具與設定",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IosCardWhite)
                .border(0.5.dp, IosHairline, shape),
        ) {
            SettingRow(Icons.Filled.Download, "下載管理", IosBlue, onDownloads)
            SettingsDivider()
            SettingRow(Icons.Filled.Block, "屏蔽管理", IosBlue, onBlocks)
            SettingsDivider()
            SettingRow(Icons.Filled.Insights, "閱讀統計", IosBlue, onStats)
            SettingsDivider()
            SettingRow(Icons.Filled.BugReport, "診斷日誌", IosLabelSecondary, onDiagnostics)
            SettingsDivider()
            SettingRow(
                icon = Icons.Filled.DarkMode,
                label = "深色模式",
                tint = IosBlue,
                onClick = { showThemeDialog = true },
                trailing = themeModeLabel(prefs.themeMode),
            )
            SettingsDivider()
            SettingRow(
                icon = Icons.Filled.Lock,
                label = "隱私防窺",
                tint = IosBlue,
                onClick = {
                    val next = !prefs.privacySecure
                    prefs.privacySecure = next
                    toast(if (next) "已開啟：後台縮略圖與截屏將被隱藏" else "已關閉隱私防窺")
                },
                trailing = if (prefs.privacySecure) "開啟" else "關閉",
            )
            SettingsDivider()
            SettingRow(
                icon = Icons.Filled.Backup,
                label = "備份與恢復",
                tint = IosBlue,
                onClick = { showBackupDialog = true },
            )
            SettingsDivider()
            SettingRow(Icons.Filled.DeleteSweep, "清除快取", IosPink, onClearCache)
            SettingsDivider()
            SettingRow(
                icon = Icons.Filled.Refresh,
                label = "檢查更新",
                tint = IosBlue,
                onClick = onCheckUpdate,
                trailing = versionName?.let { "v$it" },
            )
        }

        if (showThemeDialog) {
            ThemeModeDialog(
                current = prefs.themeMode,
                onSelect = { mode ->
                    prefs.themeMode = mode
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false },
            )
        }

        if (showBackupDialog) {
            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
                title = { Text("備份與恢復") },
                text = {
                    Column {
                        Text(
                            text = "僅包含本機閱讀歷史、屏蔽規則、閱讀統計、搜索歷史、閱讀進度與書籤；不含帳號或 Cookie。",
                            fontSize = 13.sp,
                            color = IosLabelSecondary,
                            lineHeight = 18.sp,
                        )
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                exportLauncher.launch(backupFileName())
                            },
                        ) {
                            Text("匯出本機備份", color = IosBlue)
                        }
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                importLauncher.launch(arrayOf("application/json"))
                            },
                        ) {
                            Text("從備份檔恢復", color = IosBlue)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("取消", color = IosBlue)
                    }
                },
            )
        }
    }
}

private fun themeModeLabel(mode: Int): String = when (mode) {
    ThemeMode.LIGHT -> "淺色"
    ThemeMode.DARK -> "深色"
    else -> "跟隨系統"
}

/** 備份檔默認名：jmreader_backup_yyyyMMdd.json */
private fun backupFileName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        .format(java.util.Date())
    return "jmreader_backup_$stamp.json"
}

@Composable
private fun ThemeModeDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深色模式") },
        text = {
            Column {
                ThemeModeOption("跟隨系統", ThemeMode.SYSTEM, current, onSelect)
                ThemeModeOption("淺色", ThemeMode.LIGHT, current, onSelect)
                ThemeModeOption("深色", ThemeMode.DARK, current, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = IosBlue) }
        },
    )
}

@Composable
private fun ThemeModeOption(label: String, mode: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect(mode) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = IosLabelPrimary,
            modifier = Modifier.weight(1f),
        )
        if (current == mode) {
            Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IosBlue)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = IosLabelPrimary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 13.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text("›", fontSize = 18.sp, color = IosLabelSecondary)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = IosSeparator,
        modifier = Modifier.padding(start = 46.dp),
    )
}
