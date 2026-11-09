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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.FolderItem
import com.jmreader.data.JmException
import com.jmreader.data.JmRepository
import com.jmreader.data.Member
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch

@Composable
internal fun LibraryContent(
    repo: JmRepository,
    member: Member,
    onOpen: (Long) -> Unit,
    accountVisible: Boolean,
    onCheckUpdate: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    // 收藏
    var favorites by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var folderId by remember { mutableStateOf("") }
    var favoritesPage by remember { mutableIntStateOf(1) }
    var favoritesTotal by remember { mutableIntStateOf(0) }
    var favoritesLoading by remember { mutableStateOf(false) }
    var favoritesError by remember { mutableStateOf<String?>(null) }
    var favoritesLoaded by remember { mutableStateOf(false) }
    var loadedFolder by remember { mutableStateOf<String?>(null) }

    // 歷史
    var historyItems by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var historyPage by remember { mutableIntStateOf(1) }
    var historyTotal by remember { mutableIntStateOf(0) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var historyLoaded by remember { mutableStateOf(false) }

    var showFolders by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FolderItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<AlbumBrief?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun loadFavorites(selected: String, reset: Boolean) {
        favoritesLoading = true
        favoritesError = null
        val target = if (reset) 1 else favoritesPage
        runCatchingCancellable { repo.favorites(target, selected) }
            .onSuccess { result ->
                favorites = if (reset) {
                    result.list
                } else {
                    favorites + result.list.filter { item -> favorites.none { it.id == item.id } }
                }
                folders = result.folderList
                favoritesTotal = result.total.toIntOrNull() ?: result.list.size
                favoritesPage = if (reset) 2 else favoritesPage + 1
                favoritesLoaded = true
            }
            .onFailure {
                if (it is JmException.Auth) repo.auth.clearMember()
                favoritesError = it.userMessage()
            }
        favoritesLoading = false
    }

    suspend fun loadHistory(reset: Boolean) {
        historyLoading = true
        historyError = null
        val target = if (reset) 1 else historyPage
        runCatchingCancellable { repo.history(target) }
            .onSuccess { result ->
                historyItems = if (reset) {
                    result.list
                } else {
                    historyItems + result.list.filter { item -> historyItems.none { it.id == item.id } }
                }
                historyTotal = result.total
                historyPage = if (reset) 2 else historyPage + 1
                historyLoaded = true
            }
            .onFailure {
                if (it is JmException.Auth) repo.auth.clearMember()
                historyError = it.userMessage()
            }
        historyLoading = false
    }

    LaunchedEffect(member.uid, folderId, tab) {
        if (tab == 0 && (loadedFolder != folderId || !favoritesLoaded)) {
            loadedFolder = folderId
            favoritesPage = 1
            favoritesLoaded = false
            loadFavorites(folderId, true)
        }
    }

    LaunchedEffect(member.uid, tab) {
        if (tab == 1 && !historyLoaded) {
            historyPage = 1
            loadHistory(true)
        }
    }

    val list = if (tab == 0) favorites else historyItems
    val currentLoading = if (tab == 0) favoritesLoading else historyLoading
    val currentError = if (tab == 0) favoritesError else historyError
    val currentTotal = if (tab == 0) favoritesTotal else historyTotal

    val shown = remember(list, query, sortByName) {
        list
            .filter { query.isBlank() || it.name.contains(query, true) || it.author.contains(query, true) }
            .let { if (sortByName) it.sortedBy { album -> album.name } else it }
    }

    Column(Modifier.fillMaxSize()) {
        if (accountVisible) {
            val cardShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(cardShape)
                    .background(IosCardWhite)
                    .border(0.5.dp, Color(0x12000000), cardShape)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IosBlueSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = member.username.take(1).uppercase().ifBlank { "J" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = IosBlue,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                ) {
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
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "v${com.jmreader.BuildConfig.VERSION_NAME} · 點此檢查更新",
                        fontSize = 11.sp,
                        color = IosBlue,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clickable(onClick = onCheckUpdate),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(IosGroupedBg)
                        .clickable { scope.launch { repo.logout() } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "登出",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        IosSegmentedControl(
            items = listOf("收藏", "閱讀歷史"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
            item {
                IosFilterPill(
                    selected = sortByName,
                    label = if (sortByName) "排序：名稱↓" else "排序：默認",
                    onClick = { sortByName = !sortByName },
                )
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        if (tab == 0) "搜尋收藏（已載入 ${favorites.size} 筆）"
                        else "搜尋歷史（已載入 ${historyItems.size} 筆）",
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                currentLoading && shown.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))
                currentError != null && shown.isEmpty() -> Text(
                    text = currentError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                shown.isEmpty() -> Text(
                    text = when {
                        query.isNotBlank() -> "沒有符合的結果"
                        tab == 0 -> "收藏夾還沒有作品"
                        else -> "還沒有閱讀記錄"
                    },
                    color = IosLabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = shown, key = { "${tab}_${it.id}" }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            onLongClick = if (tab == 0) {
                                { moveTarget = album }
                            } else {
                                null
                            },
                        )
                    }
                    if (shown.size < currentTotal) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (currentLoading) {
                                    CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (tab == 0) loadFavorites(folderId, false) else loadHistory(false)
                                            }
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = IosCardWhite,
                                            contentColor = IosBlue,
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                                    ) {
                                        Text(
                                            "載入更多（${shown.size}/$currentTotal）",
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
                                            loadFavorites(folderId, true)
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
                                                loadFavorites(folderId, true)
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
                                    loadFavorites(folderId, true)
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
                                                loadFavorites(folderId, true)
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
}
