package com.jmreader.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.JmRepository
import com.jmreader.data.model.userMessage
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.components.IosSegmentedControl
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val container = LocalAppContainer.current
    val toast = rememberToaster()
    val scope = rememberCoroutineScope()
    // 状态挂在 albumId 上：换作品整块重建
    val state = remember(albumId) { DetailState(albumId, container) }

    var detailTab by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val detail = state.detail
    val chapters = state.chapters
    val forum = state.forum

    val member by repo.auth.member.collectAsState()
    val loggedIn = member != null

    // 續讀：優先取本作品的 history 記錄，last 只作兜底；記錄的章節必須還在目錄裡才啟用，
    // 否則（章節下架 / cid 對不上 / 目錄還沒載入）一律降級成「開始閱讀」。
    val history by container.readingHistory.history.collectAsState()
    val lastRead by container.readingHistory.last.collectAsState()
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

    LaunchedEffect(albumId, reloadKey) { state.load() }
    LaunchedEffect(albumId) { state.refreshComments() }
    // 同作者其它作品：詳情載入後查一次，失敗或空結果整塊不渲染
    LaunchedEffect(state.detail?.id) { state.loadAuthorWorks() }

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
                state.loading -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                album == null -> ErrorState(
                    message = state.error ?: "載入失敗",
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { reloadKey++ },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        DetailHeaderCard(
                            repo = repo,
                            album = album,
                            isFavorite = state.isFavorite,
                            isSingleChapter = chapters.size <= 1,
                            firstChapter = chapters.firstOrNull(),
                            forumTotal = forum?.total ?: album.commentTotal,
                            resumeChapter = resume,
                            resumeChapterNumber = if (resumeIndex >= 0) resumeIndex + 1 else null,
                            onReadFirst = {
                                val first = chapters.firstOrNull()
                                if (first != null) {
                                    onRead(first.id, first.title)
                                } else {
                                    onRead(album.id, album.name)
                                }
                            },
                            onDownloadFirst = {
                                state.enqueueDownloadFirst()
                                toast("已加入下載")
                            },
                            onOpenAuthor = onOpenAuthor,
                            onFavorite = {
                                scope.launch {
                                    state.toggleFavorite()
                                        .onSuccess { fav ->
                                            toast(if (fav) "已加入收藏" else "已取消收藏")
                                        }
                                        .onFailure { toast(it.userMessage()) }
                                }
                            },
                            onLike = {
                                scope.launch {
                                    state.toggleLike().onFailure { toast(it.userMessage()) }
                                }
                            },
                            onBlockAuthor = { name ->
                                state.blockAuthor(name)
                                toast("已屏蔽作者 $name")
                            },
                            onContinue = resume?.let { rec -> { onRead(rec.cid, rec.title) } },
                        )
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
                            DetailInfoCard(
                                album = album,
                                onOpenAuthor = onOpenAuthor,
                                onSearchTag = onSearchTag,
                                onBlockTag = { tag ->
                                    state.blockTag(tag)
                                    toast("已屏蔽標籤：#$tag")
                                },
                            )
                        }

                        if (hasCatalog && chapters.isNotEmpty()) {
                            chapterPreviewSection(
                                chapters = chapters,
                                resumeCid = resume?.cid,
                                onRead = { chapter -> onRead(chapter.id, chapter.title) },
                                onDownload = { chapter ->
                                    state.enqueueDownload(chapter)
                                    toast("已加入下載")
                                },
                                onViewAll = { detailTab = catalogIndex },
                            )
                        }

                        if (state.authorWorks.isNotEmpty()) {
                            item(key = "same_author") {
                                SameAuthorSection(
                                    repo = repo,
                                    works = state.authorWorks,
                                    onOpen = { id -> onOpenAlbum?.invoke(id) },
                                )
                            }
                        }
                    }

                    if (hasCatalog && currentTab == catalogIndex) {
                        chapterItems(
                            chapters = chapters,
                            resumeCid = resume?.cid,
                            onRead = { chapter -> onRead(chapter.id, chapter.title) },
                            onDownload = { chapter ->
                                state.enqueueDownload(chapter)
                                toast("已加入下載")
                            },
                        )
                    }

                    if (currentTab == commentIndex) {
                        commentSection(
                            comments = forum?.list.orEmpty(),
                            commentText = state.commentText,
                            onCommentTextChange = { state.commentText = it },
                            replyTo = state.replyTo,
                            onCancelReply = { state.replyTo = null },
                            loggedIn = loggedIn,
                            posting = state.posting,
                            onSubmit = {
                                scope.launch {
                                    when (val result = state.submitComment()) {
                                        DetailState.PostResult.Skipped -> Unit
                                        DetailState.PostResult.NotLoggedIn -> toast("請先登入")
                                        DetailState.PostResult.Success -> Unit
                                        is DetailState.PostResult.Failure -> toast(result.message)
                                    }
                                }
                            },
                            canDelete = { comment -> loggedIn && member?.uid == comment.uid },
                            onLike = { comment ->
                                scope.launch {
                                    state.voteComment(comment.cid)
                                        .onFailure { toast(it.userMessage()) }
                                }
                            },
                            onReply = { comment ->
                                state.replyTo = comment
                                state.commentText = ""
                            },
                            onDelete = { comment ->
                                scope.launch {
                                    state.deleteComment(comment.cid)
                                        .onSuccess { result ->
                                            toast(
                                                if (result.status == "ok") "已刪除"
                                                else (result.msg.ifBlank { "刪除失敗" }),
                                            )
                                            scope.launch { state.refreshComments() }
                                        }
                                        .onFailure { toast(it.userMessage()) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
