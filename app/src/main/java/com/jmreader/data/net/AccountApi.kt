package com.jmreader.data.net

import com.jmreader.data.model.FavoritePage
import com.jmreader.data.model.FavoriteToggleResult
import com.jmreader.data.model.HistoryPage
import com.jmreader.data.model.SimpleResult
import kotlinx.serialization.json.Json

/**
 * 账号相关接口：收藏、收藏夹、云端历史、点赞、评论。
 * 写接口统一走 [authedForm] / [authedFormRaw]（就绪检查 + 静默重登），行为与拆分前一致。
 */
internal class AccountApi(
    private val fetcher: ApiFetcher,
    private val authedForm: suspend (String, Map<String, String>) -> String,
    private val authedFormRaw: suspend (String, Map<String, String>) -> Boolean,
    private val json: Json,
) {

    suspend fun favorites(page: Int): FavoritePage =
        fetcher.fetch("favorite", mapOf("page" to page.toString()), FavoritePage.serializer())

    suspend fun favorites(page: Int, folderId: String): FavoritePage {
        val params = mutableMapOf("page" to page.toString())
        if (folderId.isNotBlank()) params["folder_id"] = folderId
        return fetcher.fetch("favorite", params, FavoritePage.serializer())
    }

    suspend fun history(page: Int): HistoryPage =
        fetcher.fetch("watch_list", mapOf("page" to page.toString()), HistoryPage.serializer())

    /** 收藏 / 取消收藏（toggle），返回 type = add / remove。 */
    suspend fun toggleFavorite(aid: Long): FavoriteToggleResult {
        val body = authedForm("favorite", mapOf("aid" to aid.toString()))
        return runCatching { json.decodeFromString(FavoriteToggleResult.serializer(), body) }
            .getOrElse { FavoriteToggleResult() }
    }

    suspend fun addFavoriteFolder(name: String): SimpleResult =
        folderOperation(mapOf("type" to "add", "folder_name" to name, "folder_id" to "", "nid" to ""))

    suspend fun renameFavoriteFolder(folderId: String, name: String): SimpleResult =
        folderOperation(mapOf("type" to "edit", "folder_name" to name, "folder_id" to folderId))

    suspend fun deleteFavoriteFolder(folderId: String): SimpleResult =
        folderOperation(mapOf("type" to "del", "folder_name" to "", "folder_id" to folderId))

    suspend fun moveFavorite(folderId: String, aid: Long): SimpleResult =
        folderOperation(mapOf("type" to "move", "folder_id" to folderId, "aid" to aid.toString()))

    private suspend fun folderOperation(form: Map<String, String>): SimpleResult {
        val body = authedForm("favorite_folder", form)
        return runCatching { json.decodeFromString(SimpleResult.serializer(), body) }.getOrElse { SimpleResult() }
    }

    suspend fun deleteComment(commentId: String): SimpleResult {
        val body = authedForm("comment_delete", mapOf("comment_id" to commentId))
        return runCatching { json.decodeFromString(SimpleResult.serializer(), body) }.getOrElse { SimpleResult() }
    }

    /** 收藏 / 取消收藏。接口为 POST favorite，form 里带 aid。 */
    suspend fun addFavorite(aid: Long): Boolean {
        authedForm("favorite", mapOf("aid" to aid.toString()))
        return true
    }

    /** 点赞（切换）。接口为 POST like，form 里带 id。 */
    suspend fun toggleLike(aid: Long): Boolean {
        authedForm("like", mapOf("id" to aid.toString()))
        return true
    }

    /** 发表评论 / 回复（需要登录）。接口为 POST comment。 */
    suspend fun postComment(aid: Long, content: String, parentCommentId: String? = null): Boolean {
        val form = mutableMapOf("comment" to content, "aid" to aid.toString())
        if (!parentCommentId.isNullOrBlank()) form["comment_id"] = parentCommentId
        authedForm("comment", form)
        return true
    }

    /** 评论点赞（需要登录），返回体可能为空，只看 HTTP 是否成功。 */
    suspend fun voteComment(cid: String): Boolean =
        authedFormRaw("comment_vote", mapOf("id" to cid))
}
