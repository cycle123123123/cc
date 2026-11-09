package com.jmreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmreader.data.JmRepository
import com.jmreader.data.UpdateChecker
import com.jmreader.data.UpdateCheckResult
import com.jmreader.data.UpdateInfo
import kotlinx.coroutines.launch

sealed interface Screen {
    data class Search(val query: String = "", val state: SearchState = SearchState()) : Screen
    data class AuthorWorks(val name: String, val state: AuthorWorksState = AuthorWorksState()) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val aid: Long, val cid: Long, val title: String) : Screen
    data object Downloads : Screen
    data object Blocks : Screen
}

private enum class RootTab { HOME, DISCOVER, ME }

@Composable
fun AppRoot(repo: JmRepository) {
    var tab by remember { mutableStateOf(RootTab.HOME) }
    val overlays = remember { mutableStateListOf<Screen>() }
    val homeState = remember { HomeState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 应用内更新：启动时查一次（6 小时内不重复查）
    val updateChecker = remember { UpdateChecker.of(context) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        update = updateChecker.checkIfDue()
    }

    fun toast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
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

                is Screen.Reader -> ReaderScreen(
                    repo = repo,
                    albumId = current.aid,
                    cid = current.cid,
                    title = current.title,
                    onBack = { pop() },
                    onOpenChapter = { newCid, newTitle ->
                        overlays[overlays.lastIndex] = Screen.Reader(current.aid, newCid, newTitle)
                    },
                )

                Screen.Downloads -> DownloadsScreen(onBack = { pop() })

                Screen.Blocks -> BlocksScreen(repo = repo, onBack = { pop() })
            }
        }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!updating) update = null },
            title = { Text("發現新版本 v${info.version}") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = info.notes.ifBlank { "本次更新沒有寫說明。" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "目前版本 ${updateChecker.currentVersion}"
                        + if (info.sizeBytes > 0) " · 安裝包 ${info.sizeBytes / 1024 / 1024} MB" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (updating) {
                        LinearProgressIndicator(
                            progress = { updateProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        Text(
                            text = "下載中 $updateProgress%",
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
                        updating = true
                        updateProgress = 0
                        scope.launch {
                            runCatching {
                                val file = updateChecker.download(info.downloadUrl) { updateProgress = it }
                                // 有 root 就静默安装，失败再走系统安装器
                                if (!updateChecker.tryRootInstall(file)) {
                                    updateChecker.install(file)
                                }
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "更新失敗：${it.message ?: "請稍後再試"}",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                            updating = false
                            update = null
                        }
                    },
                ) {
                    Text(if (updating) "更新中…" else "更新")
                }
            },
            dismissButton = {
                TextButton(enabled = !updating, onClick = { update = null }) { Text("稍後") }
            },
        )
    }
}
