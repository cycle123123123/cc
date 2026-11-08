package com.jmreader.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jmreader.data.JmRepository

sealed interface Screen {
    data class Search(val query: String = "") : Screen
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
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == RootTab.HOME,
                        onClick = { tab = RootTab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "首頁") },
                        label = { Text("首頁") },
                    )
                    NavigationBarItem(
                        selected = tab == RootTab.DISCOVER,
                        onClick = { tab = RootTab.DISCOVER },
                        icon = { Icon(Icons.Filled.Explore, contentDescription = "發現") },
                        label = { Text("發現") },
                    )
                    NavigationBarItem(
                        selected = tab == RootTab.ME,
                        onClick = { tab = RootTab.ME },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
                        label = { Text("我的") },
                    )
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
                )
            }
        }
    }

    overlays.lastOrNull()?.let { current ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (current) {
                is Screen.Search -> SearchScreen(
                    repo = repo,
                    onBack = { pop() },
                    onOpen = { push(Screen.Detail(it)) },
                    initialQuery = current.query,
                )

                is Screen.Detail -> DetailScreen(
                    repo = repo,
                    albumId = current.id,
                    onBack = { pop() },
                    onRead = { cid, title -> push(Screen.Reader(current.id, cid, title)) },
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
}
