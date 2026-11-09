package com.jmreader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.jmreader.data.JmRepository
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
            IconButton(
                onClick = {
                    showCacheDialog = true
                    scope.launch { cacheBytes = repo.cacheSizeBytes() }
                },
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "清除快取", tint = IosBlue)
            }
            IconButton(onClick = onBlocks) {
                Icon(Icons.Filled.Block, contentDescription = "屏蔽管理", tint = IosBlue)
            }
            IconButton(onClick = onDownloads) {
                Icon(Icons.Filled.Download, contentDescription = "下載管理", tint = IosBlue)
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (member == null) {
                LoginForm(repo)
            } else {
                LibraryContent(repo, member!!, onOpen, accountVisible, onCheckUpdate)
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
}
