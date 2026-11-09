package com.jmreader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.imageLoader
import com.jmreader.data.AlbumBrief
import com.jmreader.data.JmRepository
import com.jmreader.data.LastRead
import com.jmreader.data.ReadingHistory
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val member by repo.auth.member.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCacheDialog by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var clearing by remember { mutableStateOf(false) }
    var accountVisible by remember { mutableStateOf(true) }
    var showLoginDialog by remember { mutableStateOf(false) }

    fun openCacheDialog() {
        showCacheDialog = true
        scope.launch { cacheBytes = repo.cacheSizeBytes() }
    }

    // 登入成功後自動收起彈窗
    LaunchedEffect(member) {
        if (member != null) showLoginDialog = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f),
            )
            if (member != null) {
                IconButton(onClick = { accountVisible = !accountVisible }) {
                    Icon(
                        if (accountVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (accountVisible) "隱藏帳號資訊" else "顯示帳號資訊",
                        tint = IosBlue,
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (member == null) {
                LoggedOutLibrary(
                    repo = repo,
                    onOpen = onOpen,
                    onDownloads = onDownloads,
                    onBlocks = onBlocks,
                    onClearCache = { openCacheDialog() },
                    onCheckUpdate = onCheckUpdate,
                    onLogin = { showLoginDialog = true },
                )
            } else {
                LibraryContent(
                    repo = repo,
                    member = member!!,
                    onOpen = onOpen,
                    accountVisible = accountVisible,
                    onDownloads = onDownloads,
                    onBlocks = onBlocks,
                    onClearCache = { openCacheDialog() },
                    onCheckUpdate = onCheckUpdate,
                )
            }
        }
    }

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { if (!clearing) showCacheDialog = false },
            title = { Text("清除快取") },
            text = {
                Text("目前占用 ${formatCacheSize(cacheBytes)}。包含封面、閱讀頁和介面快取，清除後再打開會重新下載。")
            },
            confirmButton = {
                TextButton(
                    enabled = !clearing,
                    onClick = {
                        clearing = true
                        scope.launch {
                            repo.clearCaches()
                            runCatching {
                                context.imageLoader.memoryCache?.clear()
                                context.imageLoader.diskCache?.clear()
                            }
                            cacheBytes = repo.cacheSizeBytes()
                            clearing = false
                            showCacheDialog = false
                            Toast.makeText(context, "已清除快取", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(if (clearing) "清除中…" else "清除") }
            },
            dismissButton = {
                TextButton(enabled = !clearing, onClick = { showCacheDialog = false }) { Text("取消") }
            },
        )
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("登入 JM 帳號") },
            text = { LoginForm(repo, compact = true) },
            confirmButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("關閉") }
            },
        )
    }
}

/** 未登入时的「我的」页：登录卡片 + 工具分组 + 本地阅读历史。 */
@Composable
private fun LoggedOutLibrary(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    val readingHistory = remember { ReadingHistory.of(context) }
    val localHistory by readingHistory.history.collectAsState()
    val gridState = rememberLazyGridState()
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LoginPromptCard(onLogin = onLogin)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroup(
                onDownloads = onDownloads,
                onBlocks = onBlocks,
                onClearCache = onClearCache,
                onCheckUpdate = onCheckUpdate,
                versionName = com.jmreader.BuildConfig.VERSION_NAME,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LocalHistoryHeader(
                count = localHistory.size,
                onClear = { showClearConfirm = true },
            )
        }
        localHistoryEntries(repo, localHistory, onOpen)
    }

    LocalHistoryClearDialog(
        visible = showClearConfirm,
        count = localHistory.size,
        onDismiss = { showClearConfirm = false },
        onConfirm = {
            readingHistory.clear()
            showClearConfirm = false
        },
    )
}

@Composable
private fun LoginPromptCard(onLogin: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(IosBlueSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = IosBlue,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = "尚未登入 JM 帳號",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
            )
            Text(
                text = "登入以同步雲端收藏與歷史",
                fontSize = 12.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(IosBlue)
                .clickable(onClick = onLogin)
                .padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            Text(
                text = "登入",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/**
 * 「工具與設定」分組：未登入 / 已登入共用，四項入口保持一致。
 * [versionName] 只作為「檢查更新」行的右側附註，不影響點擊行為。
 */
@Composable
internal fun SettingsGroup(
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    versionName: String? = null,
) {
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
                .border(0.5.dp, Color(0x12000000), shape),
        ) {
            SettingRow(Icons.Filled.Download, "下載管理", IosBlue, onDownloads)
            SettingsDivider()
            SettingRow(Icons.Filled.Block, "屏蔽管理", IosBlue, onBlocks)
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

/** 「本地閱讀歷史」小节标题，右侧是清空按钮。 */
@Composable
internal fun LocalHistoryHeader(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "本地閱讀歷史",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelSecondary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("清空", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IosPink)
            }
        }
    }
}

/** 本地历史网格项：3 列，和云端收藏/历史共用 [AlbumCard]。 */
internal fun LazyGridScope.localHistoryEntries(
    repo: JmRepository,
    history: List<LastRead>,
    onOpen: (Long) -> Unit,
) {
    if (history.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("還沒有本地閱讀記錄", fontSize = 14.sp, color = IosLabelSecondary)
            }
        }
    } else {
        items(items = history, key = { "local_${it.albumId}" }) { entry ->
            AlbumCard(
                repo = repo,
                album = entry.asAlbumBrief(),
                onClick = { onOpen(entry.albumId) },
                badge = "第 ${entry.page + 1} 頁",
                compact = true,
            )
        }
    }
}

/**
 * LastRead 只有 id/标题，刚好够 AlbumCard 用：封面由 album.id 拼出，
 * 作者等字段留空。
 */
internal fun LastRead.asAlbumBrief(): AlbumBrief = AlbumBrief(
    id = albumId.toString(),
    name = title.ifBlank { "未命名作品" },
)

@Composable
internal fun LocalHistoryClearDialog(
    visible: Boolean,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空本地閱讀歷史") },
        text = {
            Text(
                if (count > 0) {
                    "將刪除本機保存的 $count 條閱讀記錄，雲端歷史不受影響。"
                } else {
                    "本機目前沒有閱讀記錄。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清空", color = IosPink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
