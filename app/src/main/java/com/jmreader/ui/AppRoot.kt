package com.jmreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.jmreader.data.JmRepository

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Me : Screen
    data object Downloads : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val aid: Long, val cid: Long, val title: String) : Screen
}

@Composable
fun AppRoot(repo: JmRepository) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

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
            onOpen = { push(Screen.Detail(it)) },
            onSearch = { push(Screen.Search) },
            onMe = { push(Screen.Me) },
        )

        Screen.Search -> SearchScreen(
            repo = repo,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
        )

        Screen.Me -> MeScreen(
            repo = repo,
            onBack = { pop() },
            onOpen = { push(Screen.Detail(it)) },
            onDownloads = { push(Screen.Downloads) },
        )

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
