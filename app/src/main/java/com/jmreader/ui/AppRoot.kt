package com.jmreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jmreader.data.JmRepository

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val aid: Long, val cid: Long, val title: String) : Screen
}

@Composable
fun AppRoot(repo: JmRepository) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = when (val current = screen) {
            is Screen.Reader -> Screen.Detail(current.aid)
            else -> Screen.Home
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            repo = repo,
            onOpen = { screen = Screen.Detail(it) },
        )

        is Screen.Detail -> DetailScreen(
            repo = repo,
            albumId = current.id,
            onBack = { screen = Screen.Home },
            onRead = { cid, title -> screen = Screen.Reader(current.id, cid, title) },
        )

        is Screen.Reader -> ReaderScreen(
            repo = repo,
            albumId = current.aid,
            cid = current.cid,
            title = current.title,
            onBack = { screen = Screen.Detail(current.aid) },
            onOpenChapter = { newCid, newTitle -> screen = Screen.Reader(current.aid, newCid, newTitle) },
        )
    }
}
