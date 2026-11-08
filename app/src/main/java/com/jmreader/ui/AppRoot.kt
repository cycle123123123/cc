package com.jmreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.jmreader.data.JmRepository

sealed interface Screen {
    data object Home : Screen
    data object Me : Screen
    data object Downloads : Screen
    data object Blocks : Screen
    data object Discover : Screen
    data class Search(val query: String = "") : Screen
    data class Author(val id: String, val name: String) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val aid: Long, val cid: Long, val title: String) : Screen
}

@Composable
fun AppRoot(repo: JmRepository) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val homeState = remember { HomeState() }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val current = stack.last()) {
        Screen.Home -> HomeScreen(
            repo = repo,
            state = homeState,
            onOpen = { push(Screen.Detail(it)) },
            onContinue = { item -> push(Screen.Reader(item.albumId, item.cid, item.title)) },
            onSearch = { push(Screen.Search()) },
            onMe = { push(Screen.Me) },
            onDiscover = { push(Screen.Discover) },
        )

        Screen.Discover -> DiscoverScreen(
            repo = repo,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
            onSearchTag = { push(Screen.Search(it)) },
            onOpenAuthor = { id, name -> push(Screen.Author(id, name)) },
        )

        is Screen.Author -> AuthorScreen(
            repo = repo,
            authorId = current.id,
            authorName = current.name,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
        )

        is Screen.Search -> SearchScreen(
            repo = repo,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
            initialQuery = current.query,
        )

        Screen.Me -> MeScreen(
            repo = repo,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
            onDownloads = { push(Screen.Downloads) },
            onBlocks = { push(Screen.Blocks) },
        )

        Screen.Blocks -> BlocksScreen(repo = repo, onBack = { pop() })

        Screen.Downloads -> DownloadsScreen(onBack = { pop() })

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
                stack[stack.lastIndex] = Screen.Reader(current.aid, newCid, newTitle)
            },
        )
    }
}
