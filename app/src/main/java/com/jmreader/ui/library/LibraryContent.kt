package com.jmreader.ui.library

import com.jmreader.ui.navigation.BottomBarMetrics

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.jmreader.ui.common.RefreshPolicy
import com.jmreader.ui.common.rememberPagedList
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.data.model.JmException
import com.jmreader.data.model.Member
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.userMessage
import com.jmreader.BuildConfig
import com.jmreader.ui.theme.IosHairline

@Composable
internal fun LibraryContent(
    repo: JmRepository,
    state: LibraryState,
    member: Member,
    onOpen: (Long) -> Unit,
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onStats: () -> Unit,
    onDiagnostics: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var tab by state.tab
    var query by state.query
    // 三个子 tab 共用一个滚动状态：切换时**不发** scrollToItem，位置原地不动
    val gridState = state.gridState
    var searchOpen by state.searchOpen

    // 云端收藏夹列表（分页之外的数据，仍由页面维护）
    var folders by state.folders
    var folderId by state.folderId
    var loadedFolder by state.loadedFolder

    // 云端收藏 / 云端历史各一条分页流水线，实例挂在 state 上，切 tab 不重建。
    // fetch 在调用时才读 folderId，切换收藏夹后拿到的是新值。
    val favoritesPaged = rememberPagedList(
        repo, slot = { state.favoritesPaged }, store = { state.favoritesPaged = it }) {
        PagedList<AlbumBrief>(keyOf = { it.id }) { page ->
            val call = runCatchingCancellable { repo.favorites(page, folderId) }
            call.exceptionOrNull()?.let { if (it is JmException.Auth) repo.auth.clearMember() }
            val result = call.getOrThrow()
            folders = result.folderList
            PageResult(result.list, result.total.toIntOrNull() ?: result.list.size)
        }
    }

    val historyPaged = rememberPagedList(
        repo, slot = { state.historyPaged }, store = { state.historyPaged = it }) {
        PagedList<AlbumBrief>(keyOf = { it.id }) { page ->
            val call = runCatchingCancellable { repo.history(page) }
            call.exceptionOrNull()?.let { if (it is JmException.Auth) repo.auth.clearMember() }
            val result = call.getOrThrow()
            PageResult(result.list, result.total)
        }
    }

    // 收藏/历史的写操作与过期刷新都走这两个入口，顺带更新保鲜时间戳
    suspend fun refreshFavorites() {
        favoritesPaged.refresh()
        if (favoritesPaged.error == null) state.favoritesAt = System.currentTimeMillis()
    }

    suspend fun refreshHistory() {
        historyPaged.refresh()
        if (historyPaged.error == null) state.historyAt = System.currentTimeMillis()
    }

    var showFolders by state.showFolders
    var newFolderName by state.newFolderName
    var renameTarget by state.renameTarget
    var renameText by state.renameText
    var moveTarget by state.moveTarget
    val scope = rememberCoroutineScope()
    val container = LocalAppContainer.current
    val toast = rememberToaster()

    // 追更提醒（只查韓漫連載），數字來自本地緩存
    val watchSnapshot by container.favoriteWatch.snapshot.collectAsState()
    val readingHistory = container.readingHistory
    val localHistory by readingHistory.history.collectAsState()
    var showClearHistory by state.showClearHistory
    // 换账号（含登出后再登录）时清空上一个账号的数据，避免看到别人的收藏
    LaunchedEffect(member.uid) {
        if (state.uid != member.uid) {
            state.uid = member.uid
            state.favoritesPaged?.reset()
            state.historyPaged?.reset()
            state.favoritesAt = 0L
            state.historyAt = 0L
            state.folders.value = emptyList()
            state.loadedFolder.value = null
        }
    }

    // 只在「收藏夹变了 / 首次进入 / 过了保鲜期」时请求，切回本页不重复拉
    LaunchedEffect(member.uid, folderId, tab) {
        val stale = RefreshPolicy.isStale(
            state.favoritesAt,
            System.currentTimeMillis(),
            RefreshPolicy.LibraryTtlMs,
        )
        if (tab == 0 && (loadedFolder != folderId || !favoritesPaged.loaded || stale)) {
            loadedFolder = folderId
            refreshFavorites()
        }
    }

    LaunchedEffect(member.uid, tab) {
        val stale = RefreshPolicy.isStale(
            state.historyAt,
            System.currentTimeMillis(),
            RefreshPolicy.LibraryTtlMs,
        )
        if (tab == 1 && (!historyPaged.loaded || stale)) {
            refreshHistory()
        }
    }

    // 三個 tab 各自的滚动位置由 gridStates 保留，无需再手动 scrollToItem(0)

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
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = BottomBarMetrics.ContentInset),
        header = {
            if (watchSnapshot.updates.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WatchUpdatesCard(
                        updates = watchSnapshot.updates,
                        onOpen = { id ->
                            container.favoriteWatch.acknowledge(id)
                            onOpen(id)
                        },
                        onDismiss = { container.favoriteWatch.clearUpdates() },
                    )
                }
            }

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
                    onStats = onStats,
                    onDiagnostics = onDiagnostics,
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

    FolderManageDialog(
        visible = showFolders,
        folders = folders,
        newName = newFolderName,
        onNewNameChange = { newFolderName = it },
        onAdd = { name ->
            scope.launch {
                runCatchingCancellable { repo.addFavoriteFolder(name) }
                    .onSuccess {
                        newFolderName = ""
                        refreshFavorites()
                    }
                    .onFailure { toast(it.userMessage()) }
            }
        },
        onRenameClick = { folder ->
            renameTarget = folder
            renameText = folder.name
        },
        onDeleteClick = { folder ->
            scope.launch {
                runCatchingCancellable { repo.deleteFavoriteFolder(folder.fid) }
                    .onSuccess {
                        if (folderId == folder.fid) folderId = ""
                        refreshFavorites()
                    }
                    .onFailure { toast(it.userMessage()) }
            }
        },
        onDismiss = { showFolders = false },
    )

    FolderRenameDialog(
        folder = renameTarget,
        text = renameText,
        onTextChange = { renameText = it },
        onConfirm = { name ->
            val folder = renameTarget ?: return@FolderRenameDialog
            scope.launch {
                runCatchingCancellable { repo.renameFavoriteFolder(folder.fid, name) }
                    .onSuccess {
                        renameTarget = null
                        refreshFavorites()
                    }
                    .onFailure { toast(it.userMessage()) }
            }
        },
        onDismiss = { renameTarget = null },
    )

    moveTarget?.let { album ->
        MoveToFolderDialog(
            folders = folders,
            onPick = { folder ->
                val aid = album.id.toLongOrNull()
                moveTarget = null
                if (aid != null) {
                    scope.launch {
                        runCatchingCancellable { repo.moveFavorite(folder.fid, aid) }
                            .onSuccess {
                                toast("已移動到 ${folder.name}")
                                refreshFavorites()
                            }
                            .onFailure { toast(it.userMessage()) }
                    }
                }
            },
            onDismiss = { moveTarget = null },
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
            .border(0.5.dp, IosHairline, shape)
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
