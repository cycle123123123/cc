package com.jmreader.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.app.AppContainer
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.AlbumDetail
import com.jmreader.data.model.ChapterEntry
import com.jmreader.data.model.CommentItem
import com.jmreader.data.model.ForumPage
import com.jmreader.data.model.SimpleResult
import com.jmreader.data.model.userMessage

/**
 * 详情页的状态与动作集合（普通类，不是 ViewModel）。
 *
 * 由 [DetailScreen] 用 `remember(albumId)` 创建，随作品切换而重建；
 * 所有网络/仓库调用都收在这里，内部不弹 Toast，是否提示由屏幕层决定。
 */
class DetailState(
    val albumId: Long,
    private val container: AppContainer,
) {

    private val repo get() = container.repository

    var detail by mutableStateOf<AlbumDetail?>(null)
        private set
    var chapters by mutableStateOf<List<ChapterEntry>>(emptyList())
        private set
    var forum by mutableStateOf<ForumPage?>(null)
        private set
    var isFavorite by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var commentText by mutableStateOf("")
    var replyTo by mutableStateOf<CommentItem?>(null)
    var posting by mutableStateOf(false)
        private set
    /** 同作者其它作品；加载失败或空结果时保持为空列表（整块不渲染）。 */
    var authorWorks by mutableStateOf<List<AlbumBrief>>(emptyList())
        private set

    /** 提交评论的结果：区分「直接忽略」「未登录」「成功」「失败」。 */
    sealed interface PostResult {
        data object Skipped : PostResult
        data object NotLoggedIn : PostResult
        data object Success : PostResult
        data class Failure(val message: String) : PostResult
    }

    /** 加载作品详情与目录；失败写入 [error]（页面自行展示，不弹 Toast）。 */
    suspend fun load(): Result<AlbumDetail> {
        loading = true
        error = null
        val result = runCatchingCancellable { repo.album(albumId) }
        result
            .onSuccess { album ->
                detail = album
                isFavorite = album.isFavorite
                chapters = repo.seriesChapters(album)
                    .ifEmpty { listOf(ChapterEntry(album.id, album.name.ifBlank { "開始閱讀" })) }
            }
            .onFailure { error = it.userMessage() }
        loading = false
        return result
    }

    /** 拉取第一页评论；失败静默（与原逻辑一致）。 */
    suspend fun refreshComments(): Result<ForumPage> =
        runCatchingCancellable { repo.forum(albumId, 1) }
            .onSuccess { forum = it }

    /** 同作者其它作品：取前 8 个，排除自己；失败或空结果整块不渲染。 */
    suspend fun loadAuthorWorks() {
        val current = detail ?: return
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

    /** 切换收藏；成功后返回新的收藏状态，供屏幕层选择提示文案。 */
    suspend fun toggleFavorite(): Result<Boolean> {
        val result = runCatchingCancellable { repo.toggleFavorite(albumId) }
        result.onSuccess { toggled ->
            isFavorite = when (toggled.type) {
                "add" -> true
                "remove" -> false
                else -> !isFavorite
            }
            container.contentFilter.recordFavorite(
                albumId.toString(),
                detail?.author?.joinToString(" ") ?: "",
                isFavorite,
            )
        }
        return result.map { isFavorite }
    }

    /** 点赞；失败由屏幕层提示。 */
    suspend fun toggleLike(): Result<Unit> {
        val album = detail ?: return Result.success(Unit)
        return runCatchingCancellable { repo.toggleLike(albumId) }
            .map {
                detail = album.copy(
                    liked = !album.liked,
                    likes = (album.likes + if (album.liked) -1 else 1).coerceAtLeast(0),
                )
            }
    }

    /** 发表评论；成功后清空输入、退出回复态并刷新评论列表。 */
    suspend fun submitComment(): PostResult {
        val text = commentText.trim()
        if (text.isEmpty() || posting) return PostResult.Skipped
        if (container.repository.auth.member.value == null) return PostResult.NotLoggedIn
        posting = true
        val result = runCatchingCancellable { repo.postComment(albumId, text, replyTo?.cid) }
        posting = false
        return result.fold(
            onSuccess = {
                commentText = ""
                replyTo = null
                refreshComments()
                PostResult.Success
            },
            onFailure = { PostResult.Failure(it.userMessage()) },
        )
    }

    /** 删除评论；成功结果交由屏幕层提示，之后自行调用 [refreshComments]。 */
    suspend fun deleteComment(id: String): Result<SimpleResult> =
        runCatchingCancellable { repo.deleteComment(id) }

    /** 给评论点赞；失败由屏幕层提示。 */
    suspend fun voteComment(id: String): Result<Boolean> =
        runCatchingCancellable { repo.voteComment(id) }

    fun enqueueDownload(chapter: ChapterEntry) {
        container.downloads.enqueue(chapter.id, albumId, chapter.title)
    }

    /** 单话本的下载入口：有目录用第一话，否则退回作品本身。 */
    fun enqueueDownloadFirst() {
        val album = detail ?: return
        val first = chapters.firstOrNull()
        if (first != null) {
            container.downloads.enqueue(first.id, album.id, first.title)
        } else {
            container.downloads.enqueue(album.id, album.id, album.name)
        }
    }

    fun blockTag(tag: String) {
        container.contentFilter.addKeyword(tag)
    }

    fun blockTags(tags: Collection<String>) {
        tags.forEach { container.contentFilter.addKeyword(it) }
    }

    fun blockAuthor(name: String) {
        container.contentFilter.addAuthor(name)
    }
}
