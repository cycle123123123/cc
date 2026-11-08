package com.jmreader.ui

import android.widget.Toast
import coil.imageLoader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.FolderItem
import com.jmreader.data.JmException
import com.jmreader.data.JmRepository
import com.jmreader.data.Member
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch


/**
 * 「我的」：账号 + 收藏 / 历史。收藏与历史各自独立分页，避免切 tab 时互相串数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = { accountVisible = !accountVisible }) {
                        Icon(
                            if (accountVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (accountVisible) "隱藏帳號資訊" else "顯示帳號資訊",
                        )
                    }
                    IconButton(
                        onClick = {
                            showCacheDialog = true
                            scope.launch { cacheBytes = repo.cacheSizeBytes() }
                        },
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "清除快取")
                    }
                    IconButton(onClick = onBlocks) {
                        Icon(Icons.Filled.Block, contentDescription = "屏蔽管理")
                    }
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Filled.Download, contentDescription = "下載管理")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
