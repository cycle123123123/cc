package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jmreader.data.AlbumDetail
import com.jmreader.data.ChapterEntry
import com.jmreader.data.DownloadRepository
import com.jmreader.data.JmRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: JmRepository,
    albumId: Long,
    onBack: () -> Unit,
    onRead: (Long, String) -> Unit,
) {
    var detail by remember { mutableStateOf<AlbumDetail?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(albumId) {
        loading = true
        runCatching { repo.album(albumId) }
            .onSuccess { album ->
                detail = album
                chapters = repo.seriesChapters(album)
                    .ifEmpty { listOf(ChapterEntry(album.id, album.name.ifBlank { "開始閱讀" })) }
            }
            .onFailure { error = it.message ?: "載入失敗" }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val album = detail
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                album == null -> Text(
                    text = error ?: "載入失敗",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Header(repo = repo, album = album) }

                    if (album.description.isNotBlank()) {
                        item {
                            Text(
                                text = album.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        Text(
                            text = "目錄（${chapters.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    items(items = chapters, key = { it.id }) { chapter ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = chapter.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onRead(chapter.id, chapter.title) }
                                        .padding(vertical = 8.dp),
                                )
                                IconButton(
                                    onClick = {
                                        DownloadRepository.of(context)
                                            .enqueue(chapter.id, album.id, chapter.title)
                                        Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "下載")
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(repo: JmRepository, album: AlbumDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
            model = repo.coverUrl(album.id.toString()),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (album.author.isNotEmpty()) {
                Text(
                    text = album.author.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${album.totalPhotos} 頁 · ${album.likes} 喜歡",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (album.tags.isNotEmpty()) {
                Text(
                    text = album.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
