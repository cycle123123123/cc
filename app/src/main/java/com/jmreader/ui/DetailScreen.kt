package com.jmreader.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.AlbumDetail
import com.jmreader.data.ChapterEntry
import com.jmreader.data.CommentItem
import com.jmreader.data.ContentFilter
import com.jmreader.data.DownloadRepository
import com.jmreader.data.ForumPage
import com.jmreader.data.JmRepository
import com.jmreader.data.ReadingHistory
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    repo: JmRepository,
    albumId: Long,
    onBack: () -> Unit,
    onRead: (Long, String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenAlbum: ((Long) -> Unit)? = null,
) {
    var showTagBlock by remember { mutableStateOf(false) }
    var tagsToBlock by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detail by remember { mutableStateOf<AlbumDetail?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntry>>(emptyList()) }
    var forum by remember { mutableStateOf<ForumPage?>(null) }
    var commentText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var detailTab by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val member by repo.auth.member.collectAsState()
    val loggedIn = member != null

    // 續讀：優先取本作品的 history 記錄，last 只作兜底；記錄的章節必須還在目錄裡才啟用，
    // 否則（章節下架 / cid 對不上 / 目錄還沒載入）一律降級成「開始閱讀」。
    val readingHistory = remember { ReadingHistory.of(context) }
    val history by readingHistory.history.collectAsState()
    val lastRead by readingHistory.last.collectAsState()
    val resume = remember(history, lastRead, chapters, albumId) {
        val record = history.firstOrNull { it.albumId == albumId }
            ?: lastRead?.takeIf { it.albumId == albumId }
        record?.takeIf { rec -> chapters.any { it.id == rec.cid } }
    }
    val resumeIndex = remember(resume, chapters) {
        resume?.let { rec -> chapters.indexOfFirst { it.id == rec.cid } } ?: -1
    }

    // 分頁結構由話數決定：單話本沒有「目錄」頁；索引一律由 tabs 解析，不硬編碼。
    val hasCatalog = chapters.size > 1
    val tabs = buildList {
        add("詳情")
        if (hasCatalog) add("目錄（${chapters.size}）")
        add("評論（${forum?.total ?: detail?.commentTotal ?: 0}）")
    }
    val catalogIndex = if (hasCatalog) 1 else -1
    val commentIndex = tabs.lastIndex
    val currentTab = detailTab.coerceIn(0, tabs.lastIndex)

    // chapters 從 1 話變成多話（或相反）時，把越界的選中項收回「詳情」
    LaunchedEffect(tabs.size) {
        if (detailTab > tabs.lastIndex) detailTab = 0
    }

    // 同作者其它作品：進詳情頁載入一次，失敗或空結果整塊不渲染
    var authorWorks by remember(detail?.id) { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    LaunchedEffect(detail?.id) {
        val current = detail ?: return@LaunchedEffect
        val authorName = current.author.joinToString(" ").trim()
        if (authorName.isNotBlank()) {
            runCatchingCancellable { repo.search(authorName, 1) }
                .onSuccess { page ->
                    authorWorks = page.content
                        .filter { it.id.isNotBlank() && it.id != current.id.toString() }
                        .take(8)
                }
        }
    }

    LaunchedEffect(albumId, reloadKey) {
        loading = true
        error = null
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
        containerColor = IosGroupedBg,
        topBar = {
            TopAppBar(
                title = { Text("作品詳情", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBlue,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosGroupedBg,
                    titleContentColor = IosLabelPrimary,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val album = detail
            when {
                loading -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                album == null -> ErrorState(
                    message = error ?: "載入失敗",
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { reloadKey++ },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(IosCardWhite)
                                .border(0.5.dp, Color(0x12000000), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                        ) {
                            Header(
                                repo = repo,
                                album = album,
                                isFavorite = isFavorite,
                                isSingleChapter = chapters.size <= 1,
                                onReadFirst = {
                                    val first = chapters.firstOrNull()
                                    if (first != null) {
                                        onRead(first.id, first.title)
                                    } else {
                                        onRead(album.id, album.name)
                                    }
                                },
                                onDownloadFirst = {
                                    val first = chapters.firstOrNull()
                                    if (first != null) {
                                        DownloadRepository.of(context).enqueue(first.id, album.id, first.title)
                                        Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                                    } else {
                                        DownloadRepository.of(context).enqueue(album.id, album.id, album.name)
                                        Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenAuthor = onOpenAuthor,
                                onFavorite = {
                                    scope.launch {
                                        runCatchingCancellable { repo.toggleFavorite(albumId) }
                                            .onSuccess { result ->
                                                isFavorite = when (result.type) {
                                                    "add" -> true
                                                    "remove" -> false
                                                    else -> !isFavorite
                                                }
                                                ContentFilter.of(context).recordFavorite(
                                                    albumId.toString(),
                                                    album.author.joinToString(" "),
                                                    isFavorite,
                                                )
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
                                onBlockTags = {
                                    tagsToBlock = emptySet()
                                    showTagBlock = true
                                },
                                resumeChapter = resume,
                                resumeChapterNumber = if (resumeIndex >= 0) resumeIndex + 1 else null,
                                onContinue = resume?.let { rec -> { onRead(rec.cid, rec.title) } },
                            )

                            DetailStatsRow(
                                pages = album.totalPhotos.toString(),
                                views = formatCount(album.viewCount),
                                likes = formatCount(album.likes),
                                comments = formatCount(forum?.total ?: album.commentTotal),
                                modifier = Modifier.padding(top = 14.dp),
                            )

                            chapters.firstOrNull()?.let { first ->
                                PreviewStrip(
                                    repo = repo,
                                    albumId = album.id,
                                    chapterId = first.id,
                                )
                            }
                        }
                    }

                    item(key = "tabs") {
                        IosSegmentedControl(
                            items = tabs,
                            selectedIndex = currentTab,
                            onSelect = { detailTab = it },
                        )
                    }

                    if (currentTab == 0) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IosCardWhite)
                                    .border(0.5.dp, Color(0x12000000), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (album.author.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "作者",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosLabelSecondary,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            // 接口会把作者名按空格拆成多项（如 ["STUDIO","J"]），这里合并回一个
                                            val authorName = album.author.joinToString(" ").trim()
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(IosBlueSoft)
                                                    .clickable { onOpenAuthor(authorName) }
                                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                            ) {
                                                Text(
                                                    text = authorName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = IosBlue,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (album.tags.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "標籤",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosLabelSecondary,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val tagFilter = ContentFilter.of(context)
                                            album.tags.forEach { tag ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(IosGroupedBg)
                                                        .combinedClickable(
                                                            onClick = { onSearchTag(tag) },
                                                            onLongClick = {
                                                                tagFilter.addKeyword(tag)
                                                                Toast.makeText(
                                                                    context,
                                                                    "已屏蔽標籤：#$tag",
                                                                    Toast.LENGTH_SHORT,
                                                                ).show()
                                                            },
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                                ) {
                                                    Text(
                                                        text = "#$tag",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = IosLabelPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (album.description.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "簡介",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosLabelSecondary,
                                        )
                                        Text(
                                            text = album.description,
                                            fontSize = 13.sp,
                                            color = IosLabelPrimary,
                                            lineHeight = 19.sp,
                                        )
                                    }
                                }
                            }
                        }

                        if (hasCatalog && chapters.isNotEmpty()) {
                            item {
                                Text(
                                    text = "章節列表（共 ${chapters.size} 話）",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IosLabelPrimary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(items = chapters.take(5), key = { "preview_ch_${it.id}" }) { chapter ->
                                ChapterRow(
                                    chapter = chapter,
                                    onRead = { onRead(chapter.id, chapter.title) },
                                    isResume = resume != null && chapter.id == resume.cid,
                                    onDownload = {
                                        DownloadRepository.of(context).enqueue(chapter.id, album.id, chapter.title)
                                        Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                            if (chapters.size > 5) {
                                item {
                                    TextButton(
                                        onClick = { detailTab = catalogIndex },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("查看全部 ${chapters.size} 話 ›", color = IosBlue, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        if (authorWorks.isNotEmpty()) {
                            item(key = "same_author") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(IosCardWhite)
                                        .border(0.5.dp, Color(0x12000000), RoundedCornerShape(14.dp))
                                        .padding(vertical = 14.dp),
                                ) {
                                    Text(
                                        text = "同作者作品",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IosLabelPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        items(items = authorWorks, key = { it.id }) { work ->
                                            Box(Modifier.width(96.dp)) {
                                                AlbumCard(
                                                    repo = repo,
                                                    album = work,
                                                    onClick = {
                                                        work.id.toLongOrNull()?.let { id ->
                                                            onOpenAlbum?.invoke(id)
                                                        }
                                                    },
                                                    compact = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasCatalog && currentTab == catalogIndex) {
                        items(items = chapters, key = { it.id }) { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                onRead = { onRead(chapter.id, chapter.title) },
                                isResume = resume != null && chapter.id == resume.cid,
                                onDownload = {
                                    DownloadRepository.of(context).enqueue(chapter.id, album.id, chapter.title)
                                    Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }

                    if (currentTab == commentIndex) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IosCardWhite)
                                    .border(0.5.dp, Color(0x12000000), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                            ) {
                                replyTo?.let { target ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "回覆 @${target.nickname.ifBlank { target.username }}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IosBlue,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { replyTo = null }) { Text("取消") }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { commentText = it },
                                        placeholder = { Text(if (loggedIn) "撰寫評論…" else "登入後可發表評論") },
                                        enabled = loggedIn && !posting,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = { submitComment() },
                                        enabled = loggedIn && !posting && commentText.isNotBlank(),
                                        shape = CircleShape,
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
                                    color = IosLabelSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 16.dp),
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
        if (showTagBlock) {
            detail?.let { album ->
                AlertDialog(
                    onDismissRequest = { showTagBlock = false },
                    title = { Text("屏蔽標籤") },
                    text = {
                        Column {
                            album.tags.forEach { tag ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = tagsToBlock.contains(tag),
                                        onCheckedChange = { checked ->
                                            tagsToBlock = if (checked) tagsToBlock + tag else tagsToBlock - tag
                                        },
                                    )
                                    Text(tag)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val filter = ContentFilter.of(context)
                                tagsToBlock.forEach { filter.addKeyword(it) }
                                Toast.makeText(context, "已屏蔽 ${tagsToBlock.size} 個標籤", Toast.LENGTH_SHORT).show()
                                showTagBlock = false
                            },
                        ) { Text("確定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTagBlock = false }) { Text("取消") }
                    },
                )
            }
        }
    }
}
