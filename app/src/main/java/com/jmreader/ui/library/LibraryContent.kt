package com.jmreader.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.FolderItem
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.components.IosFilterPill
import com.jmreader.ui.components.IosSegmentedControl
import com.jmreader.ui.common.PageResult
import com.jmreader.ui.common.PagedList
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.data.model.JmException
import com.jmreader.data.model.Member
import com.jmreader.data.local.ReadingHistory
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.userMessage
import com.jmreader.BuildConfig

@Composable
internal fun LibraryContent(
    repo: JmRepository,
    member: Member,
    onOpen: (Long) -> Unit,
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    var searchOpen by remember { mutableStateOf(false) }

    // 云端收藏夹列表（分页之外的数据，仍由页面维护）
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var folderId by remember { mutableStateOf("") }
    var loadedFolder by remember { mutableStateOf<String?>(null) }

    // 云端收藏 / 云端历史各一条分页流水线。
    // fetch 在调用时才读 folderId，切换收藏夹后拿到的是新值。
    val favoritesPaged = remember {
        PagedList<AlbumBrief>(keyOf = { it.id }) { page ->
            val call = runCatchingCancellable { repo.favorites(page, folderId) }
            call.exceptionOrNull()?.let { if (it is JmException.Auth) repo.auth.clearMember() }
            val result = call.getOrThrow()
            folders = result.folderList
            PageResult(result.list, result.total.toIntOrNull() ?: result.list.size)
        }
    }

    val historyPaged = remember {
        PagedList<AlbumBrief>(keyOf = { it.id }) { page ->
            val call = runCatchingCancellable { repo.history(page) }
            call.exceptionOrNull()?.let { if (it is JmException.Auth) repo.auth.clearMember() }
            val result = call.getOrThrow()
            PageResult(result.list, result.total)
        }
    }

    var showFolders by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FolderItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<AlbumBrief?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 本地閱讀歷史（第三個 tab）
    val readingHistory = remember { ReadingHistory.of(context) }
    val localHistory by readingHistory.history.collectAsState()
    var showClearHistory by remember { mutableStateOf(false) }

    LaunchedEffect(member.uid, folderId, tab) {
        if (tab == 0 && (loadedFolder != folderId || !favoritesPaged.loaded)) {
            loadedFolder = folderId
            favoritesPaged.refresh()
        }
    }

    LaunchedEffect(member.uid, tab) {
        if (tab == 1 && !historyPaged.loaded) {
            historyPaged.refresh()
        }
    }

    // 三個 tab 共用 gridState，切換時回到頂部，避免位置錯亂
    LaunchedEffect(tab) {
        gridState.scrollToItem(0)
    }

    // 本地歷史 tab 不分頁；雲端兩個 tab 各自走 PagedList
    val cloudTab = tab != 2
    val paged = if (tab == 0) favoritesPaged else historyPaged
    val list = if (cloudTab) paged.items else emptyList()
    val shown = remember(list, query) {
        if (query.isBlank()) {
            list
        } else {
            list.filter { it.name.contains(query, true) || it.author.contains(query, true) }
        }
    }
    // 首屏（列表为空）的加载 / 错误 / 空态仍然画在骨架里；触底加载和底部 loading 交给 AlbumGrid
    val firstScreen = cloudTab && shown.isEmpty()

    AlbumGrid(
        repo = repo,
        albums = if (cloudTab) shown else emptyList(),
        onOpen = onOpen,
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        loading = !firstScreen && paged.loading,
        error = if (firstScreen) null else paged.error,
        hasMore = cloudTab && paged.hasMore,
        onLoadMore = if (!cloudTab) null else ({ scope.launch { paged.loadMore() } }),
        onLongClick = if (tab == 0) ({ album: AlbumBrief -> moveTarget = album }) else null,
        applyBlockFilter = false,
        showMeta = false,
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
        header = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AccountCard(
                    member = member,
                    onLogout = { scope.launch { repo.logout() } },
                )
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
                IosSegmentedControl(
                    items = listOf("雲端收藏", "雲端歷史", "本地歷史"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == 2) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LocalHistoryHeader(
                        count = localHistory.size,
                        onClear = { showClearHistory = true },
                    )
                }
                localHistoryEntries(repo, localHistory, onOpen)
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (tab == 0) {
                            item {
                                IosFilterPill(
                                    selected = folderId.isBlank(),
                                    label = "全部",
                                    onClick = { folderId = "" },
                                )
                            }
                            items(items = folders, key = { it.fid }) { folder ->
                                IosFilterPill(
                                    selected = folderId == folder.fid,
                                    label = folder.name,
                                    onClick = { folderId = folder.fid },
                                )
                            }
                            item {
                                IosFilterPill(
                                    selected = false,
                                    label = "管理收藏夾",
                                    onClick = { showFolders = true },
                                )
                            }
                        }
                        item {
                            IosFilterPill(
                                selected = searchOpen,
                                label = if (searchOpen) "收起搜尋" else "🔍 篩選",
                                onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) query = ""
                                },
                            )
                        }
                    }
                }

                if (searchOpen) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    if (tab == 0) "搜尋收藏（已載入 ${favoritesPaged.items.size} 筆）"
                                    else "搜尋歷史（已載入 ${historyPaged.items.size} 筆）",
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                when {
                    paged.loading && shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = IosBlue)
                        }
                    }
                    paged.error != null && shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = paged.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        )
                    }
                    shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when {
                                    query.isNotBlank() -> "沒有符合的結果"
                                    tab == 0 -> "收藏夾還沒有作品"
                                    else -> "還沒有閱讀記錄"
                                },
                                fontSize = 14.sp,
                                color = IosLabelSecondary,
                            )
                        }
                    }
                }
            }
        },
    )

    if (showFolders) {
        AlertDialog(
            onDismissRequest = { showFolders = false },
            title = { Text("收藏夾") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("新收藏夾") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = newFolderName.isNotBlank(),
                            onClick = {
                                val name = newFolderName.trim()
                                scope.launch {
                                    runCatchingCancellable { repo.addFavoriteFolder(name) }
                                        .onSuccess {
                                            newFolderName = ""
                                            favoritesPaged.refresh()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                        ) { Text("新增") }
                    }
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(folder.name, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    renameTarget = folder
                                    renameText = folder.name
                                },
                            ) { Text("改名") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatchingCancellable { repo.deleteFavoriteFolder(folder.fid) }
                                            .onSuccess {
                                                if (folderId == folder.fid) folderId = ""
                                                favoritesPaged.refresh()
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                            ) { Text("刪除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolders = false }) { Text("關閉") }
            },
        )
    }

    renameTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重新命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        val name = renameText.trim()
                        scope.launch {
                            runCatchingCancellable { repo.renameFavoriteFolder(folder.fid, name) }
                                .onSuccess {
                                    renameTarget = null
                                    favoritesPaged.refresh()
                                }
                                .onFailure {
                                    Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                ) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    moveTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("移動到收藏夾") },
            text = {
                Column {
                    if (folders.isEmpty()) {
                        Text("先建立一個收藏夾")
                    }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                val aid = album.id.toLongOrNull()
                                moveTarget = null
                                if (aid != null) {
                                    scope.launch {
                                        runCatchingCancellable { repo.moveFavorite(folder.fid, aid) }
                                            .onSuccess {
                                                Toast.makeText(context, "已移動到 ${folder.name}", Toast.LENGTH_SHORT).show()
                                                favoritesPaged.refresh()
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            },
                        ) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("取消") }
            },
        )
    }

    LocalHistoryClearDialog(
        visible = showClearHistory,
        count = localHistory.size,
        onDismiss = { showClearHistory = false },
        onConfirm = {
            readingHistory.clear()
            showClearHistory = false
        },
    )
}

/**
 * 已登入的帳號卡：和未登入的登入提示卡同一套視覺
 * （12dp 圓角白卡、40dp 圓形頭像位、16/12sp 兩行文字）。
 */
@Composable
private fun AccountCard(
    member: Member,
    onLogout: () -> Unit,
) {
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
            Text(
                text = member.username.take(1).uppercase().ifBlank { "J" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = IosBlue,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = member.username.ifBlank { "已登入" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
            )
            Text(
                text = "UID ${member.uid} · 收藏 ${member.totalFavorites} · Lv.${member.level}",
                fontSize = 12.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onLogout)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "登出",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosPink,
            )
        }
    }
}
