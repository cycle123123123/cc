package com.jmreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmreader.data.ChapterEntry
import com.jmreader.data.ComicRead
import com.jmreader.data.JmRepository
import com.jmreader.data.SeriesParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    repo: JmRepository,
    albumId: Long,
    cid: Long,
    title: String,
    onBack: () -> Unit = {},
    onOpenChapter: (Long, String) -> Unit = { _, _ -> },
) {
    var read by remember { mutableStateOf<ComicRead?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cid) {
        runCatching { repo.comicRead(cid) }
            .onSuccess { read = it }
            .onFailure { error = it.message ?: "載入失敗" }
    }

    LaunchedEffect(albumId) {
        runCatching { repo.chapter(albumId) }
            .onSuccess { chapters = SeriesParser.parse(it.series) }
    }

    val index = chapters.indexOfFirst { it.id == cid }
    val previous = if (index > 0) chapters[index - 1] else null
    val next = if (index >= 0 && index + 1 < chapters.size) chapters[index + 1] else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
        bottomBar = {
            if (chapters.size > 1) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = previous != null,
                            onClick = { previous?.let { onOpenChapter(it.id, it.title) } },
                        ) { Text("上一話") }

                        Text(
                            text = if (index >= 0) "${index + 1} / ${chapters.size}" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )

                        TextButton(
                            enabled = next != null,
                            onClick = { next?.let { onOpenChapter(it.id, it.title) } },
                        ) { Text("下一話") }
                    }
                }
            }
        },
    ) { padding ->
        val data = read
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            when {
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                data == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items = data.images, key = { it.page }) { page ->
                        PageItem(
                            repo = repo,
                            url = page.image,
                            aid = data.id,
                            scrambleId = data.scrambleId,
                            page = page.page,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageItem(
    repo: JmRepository,
    url: String,
    aid: Long,
    scrambleId: Long,
    page: Int,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val targetWidth = with(density) { screenWidthDp.dp.roundToPx() }

    var failed by remember(url) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = runCatching { repo.loadPage(url, aid, scrambleId, targetWidth) }
            .onFailure { failed = true }
            .getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "第 $page 頁",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )

            failed -> Text(
                text = "第 $page 頁載入失敗",
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodySmall,
            )

            else -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
