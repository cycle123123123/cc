package com.jmreader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jmreader.data.userMessage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumDetail
import com.jmreader.data.ChapterEntry
import com.jmreader.data.CommentItem
import com.jmreader.data.DownloadRepository
import com.jmreader.data.ForumPage
import com.jmreader.data.JmRepository
import kotlinx.coroutines.launch

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
    var forum by remember { mutableStateOf<ForumPage?>(null) }
    var commentText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val member by repo.auth.member.collectAsState()
    val loggedIn = member != null

    LaunchedEffect(albumId) {
        loading = true
        runCatchingCancellable { repo.album(albumId) }
            .onSuccess { album ->
                detail = album
                isFavorite = album.isFavorite
                chapters = repo.seriesChapters(album)
                    .ifEmpty { listOf(ChapterEntry(album.id, album.name.ifBlank { "開始閱讀" })) }
            }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    LaunchedEffect(albumId) {
        runCatchingCancellable { repo.forum(albumId, 1) }
            .onSuccess { forum = it }
    }

    fun refreshComments() {
        scope.launch {
            runCatchingCancellable { repo.forum(albumId, 1) }.onSuccess { forum = it }
        }
    }

    fun submitComment() {
        val text = commentText.trim()
        if (text.isEmpty() || posting) return
        if (!loggedIn) {
            Toast.makeText(context, "請先登入", Toast.LENGTH_SHORT).show()
            return
        }
        posting = true
        scope.launch {
            runCatchingCancellable { repo.postComment(albumId, text, replyTo?.cid) }
                .onSuccess {
                    commentText = ""
                    replyTo = null
                    refreshComments()
                }
                .onFailure { Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show() }
            posting = false
        }
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
                    item {
                        Header(
                            repo = repo,
                            album = album,
                            isFavorite = isFavorite,
                            onFavorite = {
                                scope.launch {
                                    runCatchingCancellable { repo.toggleFavorite(albumId) }
                                        .onSuccess { result ->
                                            isFavorite = when (result.type) {
                                                "add" -> true
                                                "remove" -> false
                                                else -> !isFavorite
                                            }
                                            Toast.makeText(
                                                context,
                                                if (isFavorite) "已加入收藏" else "已取消收藏",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                            onLike = {
                                scope.launch {
                                    runCatchingCancellable { repo.toggleLike(albumId) }
                                        .onSuccess {
                                            detail = album.copy(
                                                liked = !album.liked,
                                                likes = (album.likes + if (album.liked) -1 else 1).coerceAtLeast(0),
                                            )
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                        )
                    }

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
                        ChapterRow(
                            chapter = chapter,
                            onRead = { onRead(chapter.id, chapter.title) },
                            onDownload = {
                                DownloadRepository.of(context).enqueue(chapter.id, album.id, chapter.title)
                                Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    item {
                        Text(
                            text = "評論（${forum?.total ?: album.commentTotal}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    item {
                        Column {
                            replyTo?.let { target ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "回覆 @${target.nickname.ifBlank { target.username }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { replyTo = null }) { Text("取消") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    placeholder = { Text(if (loggedIn) "說點什麼…" else "登入後可評論") },
                                    enabled = loggedIn && !posting,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = { submitComment() },
                                    enabled = loggedIn && !posting && commentText.isNotBlank(),
                                    modifier = Modifier.padding(start = 8.dp),
                                ) {
                                    Text("發送")
                                }
                            }
                        }
                    }

                    val list = forum?.list.orEmpty()
                    if (list.isEmpty()) {
                        item {
                            Text(
                                text = "還沒有評論",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        items(items = list, key = { it.cid }) { comment ->
                            CommentRow(
                                comment = comment,
                                canDelete = loggedIn && member?.uid == comment.uid,
                                onLike = {
                                    scope.launch {
                                        runCatchingCancellable { repo.voteComment(comment.cid) }
                                            .onFailure {
                                                Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                                onReply = {
                                    replyTo = comment
                                    commentText = ""
                                },
                                onDelete = {
                                    scope.launch {
                                        runCatchingCancellable { repo.deleteComment(comment.cid) }
                                            .onSuccess { result ->
                                                Toast.makeText(
                                                    context,
                                                    if (result.status == "ok") "已刪除" else (result.msg.ifBlank { "刪除失敗" }),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                refreshComments()
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterEntry,
    onRead: () -> Unit,
    onDownload: () -> Unit,
) {
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
                    .clickable(onClick = onRead)
                    .padding(vertical = 8.dp),
            )
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "下載")
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    canDelete: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReply = comment.parentCid != "0" && comment.parentCid.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 24.dp else 0.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comment.nickname.ifBlank { comment.username },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = comment.addtime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stripHtml(comment.content),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLike) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.width(16.dp))
                Text(text = " ${comment.likes}", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onReply) {
                Icon(Icons.Filled.Reply, contentDescription = null, modifier = Modifier.width(16.dp))
                Text(text = " 回覆", style = MaterialTheme.typography.labelMedium)
            }
            if (canDelete) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(16.dp))
                    Text(text = " 刪除", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Header(
    repo: JmRepository,
    album: AlbumDetail,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
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
                text = "${album.totalPhotos} 頁",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "收藏",
                    modifier = Modifier.width(18.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isFavorite) " 已收藏" else " 收藏",
                    color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLike) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = "點讚",
                    modifier = Modifier.width(18.dp),
                    tint = if (album.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " ${album.likes}",
                    color = if (album.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** 评论内容带 HTML（<div>、<br> 等），展示前转成纯文本。 */
private fun stripHtml(raw: String): String =
    runCatchingCancellable {
        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }.getOrDefault(raw)
