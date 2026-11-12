package com.jmreader.ui.library

import com.jmreader.ui.navigation.BottomBarMetrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import kotlinx.coroutines.launch
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.formatCacheSize
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.data.JmRepository
import com.jmreader.BuildConfig

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
    val toast = rememberToaster()
    val scope = rememberCoroutineScope()
    var showCacheDialog by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var clearing by remember { mutableStateOf(false) }
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
                            toast("已清除快取")
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
    val container = LocalAppContainer.current
    val readingHistory = container.readingHistory
    val localHistory by readingHistory.history.collectAsState()
    val gridState = rememberLazyGridState()
    var showClearConfirm by remember { mutableStateOf(false) }

    AlbumGrid(
        repo = repo,
        // 本地历史仍由 localHistoryEntries 渲染（保留「第 N 頁」角标和单行标题）。
        // 因此网格自身的 albums 留空，内容全部通过 header 复用同一套 LazyGridScope 扩展。
        albums = emptyList(),
        onOpen = onOpen,
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        applyBlockFilter = false,
        showMeta = false,
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = BottomBarMetrics.ContentInset),
        header = {
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
        },
    )

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
