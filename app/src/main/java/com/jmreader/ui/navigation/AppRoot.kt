package com.jmreader.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jmreader.ui.search.AuthorWorksScreen
import com.jmreader.ui.library.BlocksScreen
import com.jmreader.ui.detail.DetailScreen
import com.jmreader.ui.discover.DiscoverScreen
import com.jmreader.ui.library.DownloadsScreen
import com.jmreader.ui.home.HomeScreen
import com.jmreader.ui.home.HomeState
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSeparator
import com.jmreader.ui.library.LibraryScreen
import com.jmreader.ui.reader.ReaderScreen
import com.jmreader.ui.search.SearchScreen
import com.jmreader.data.update.UpdateCheckResult
import com.jmreader.data.update.UpdateInfo
import com.jmreader.data.JmRepository
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.rememberToaster

@Composable
fun AppRoot(repo: JmRepository) {
    var tab by remember { mutableStateOf(RootTab.HOME) }
    val overlays = remember { mutableStateListOf<Screen>() }
    val homeState = remember { HomeState() }
    val scope = rememberCoroutineScope()
    val container = LocalAppContainer.current
    val toaster = rememberToaster()

    // 应用内更新：启动时查一次（6 小时内不重复查）
    val updateChecker = container.updateChecker
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        update = updateChecker.checkIfDue()
    }

    fun toast(text: String) {
        toaster(text, false)
    }

    fun checkUpdate(manual: Boolean) {
        scope.launch {
            if (!manual) {
                update = updateChecker.checkIfDue()
                return@launch
            }
            // 手动检查绕过 15 分钟 TTL 与 HTTP 缓存，并把失败和「已是最新」区分开
            when (val result = updateChecker.checkManual()) {
                is UpdateCheckResult.Available -> update = result.info
                UpdateCheckResult.UpToDate -> {
                    update = null
                    toast("已是最新版本 v${updateChecker.currentVersion}")
                }
                is UpdateCheckResult.Failed -> {
                    val detail = result.message?.takeIf { it.isNotBlank() }
                    toast(if (detail != null) "檢查更新失敗：$detail" else "檢查更新失敗，請檢查網路後重試")
                }
            }
        }
    }

    fun push(screen: Screen) {
        overlays.add(screen)
    }

    fun pop() {
        if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex)
    }

    BackHandler(enabled = overlays.isNotEmpty() || tab != RootTab.HOME) {
        if (overlays.isNotEmpty()) pop() else tab = RootTab.HOME
    }

    Scaffold(
        bottomBar = {
            if (overlays.isEmpty()) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 0.5.dp,
                        color = IosSeparator,
                    )
                    NavigationBar(
                        containerColor = IosCardWhite,
                        tonalElevation = 0.dp,
                    ) {
                        val itemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = IosBlue,
                            selectedTextColor = IosBlue,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = IosLabelSecondary,
                            unselectedTextColor = IosLabelSecondary,
                        )
                        NavigationBarItem(
                            selected = tab == RootTab.HOME,
                            onClick = { tab = RootTab.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "首頁") },
                            label = { Text("首頁") },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = tab == RootTab.DISCOVER,
                            onClick = { tab = RootTab.DISCOVER },
                            icon = { Icon(Icons.Filled.Explore, contentDescription = "發現") },
                            label = { Text("發現") },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = tab == RootTab.ME,
                            onClick = { tab = RootTab.ME },
                            icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
                            label = { Text("我的") },
                            colors = itemColors,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                RootTab.HOME -> HomeScreen(
                    repo = repo,
                    state = homeState,
                    onOpen = { push(Screen.Detail(it)) },
                    onContinue = { item -> push(Screen.Reader(item.albumId, item.cid, item.title)) },
                    onSearch = { push(Screen.Search()) },
                )

                RootTab.DISCOVER -> DiscoverScreen(
                    repo = repo,
                    onOpen = { push(Screen.Detail(it)) },
                    onSearchTag = { push(Screen.Search(it)) },
                )

                RootTab.ME -> LibraryScreen(
                    repo = repo,
                    onOpen = { push(Screen.Detail(it)) },
                    onDownloads = { push(Screen.Downloads) },
                    onBlocks = { push(Screen.Blocks) },
                    onCheckUpdate = { checkUpdate(true) },
                )
            }
        }
    }

    overlays.lastOrNull()?.let { current ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (current) {
                is Screen.Search -> SearchScreen(
                    repo = repo,
                    state = current.state,
                    onBack = { pop() },
                    onOpen = { push(Screen.Detail(it)) },
                    initialQuery = current.query,
                )

                is Screen.AuthorWorks -> AuthorWorksScreen(
                    repo = repo,
                    authorName = current.name,
                    state = current.state,
                    onBack = { pop() },
                    onOpen = { push(Screen.Detail(it)) },
                )

                is Screen.Detail -> DetailScreen(
                    repo = repo,
                    albumId = current.id,
                    onBack = { pop() },
                    onRead = { cid, title -> push(Screen.Reader(current.id, cid, title)) },
                    onOpenAuthor = { name -> push(Screen.AuthorWorks(name)) },
                    onSearchTag = { tag -> push(Screen.Search(tag)) },
                    onOpenAlbum = { id -> push(Screen.Detail(id)) },
                )

                is Screen.Reader -> key(current.cid) {
                    // 換話時整塊重建：章節、頁碼、圖片緩存、書籤全部按新 cid 重新初始化，
                    // 避免沿用上一話的滾動位置（曾出現「第 3 話第 40 頁 → 第 4 話第 40 頁」）。
                    ReaderScreen(
                        repo = repo,
                        albumId = current.aid,
                        cid = current.cid,
                        title = current.title,
                        onBack = { pop() },
                        onOpenChapter = { newCid, newTitle ->
                            overlays[overlays.lastIndex] = Screen.Reader(current.aid, newCid, newTitle)
                        },
                    )
                }

                Screen.Downloads -> DownloadsScreen(onBack = { pop() })

                Screen.Blocks -> BlocksScreen(repo = repo, onBack = { pop() })
            }
        }
    }

    update?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = updateChecker.currentVersion,
            updating = updating,
            progress = updateProgress,
            updateChecker = updateChecker,
            scope = scope,
            onUpdatingChange = { updating = it },
            onProgressChange = { updateProgress = it },
            onDismiss = { update = null },
        )
    }
}
