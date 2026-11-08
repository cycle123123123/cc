package com.jmreader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class Named(
    val id: String = "",
    val title: String = "",
)

@Serializable
data class AlbumBrief(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    @SerialName("update_at") val updateAt: Long = 0,
    val category: Named? = null,
)

@Serializable
data class AlbumDetail(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    @SerialName("total_photos") val totalPhotos: Int = 0,
    val author: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("series_id") val seriesId: String = "0",
    val series: JsonElement = JsonNull,
    val likes: Int = 0,
    val liked: Boolean = false,
    @SerialName("comment_total") val commentTotal: Int = 0,
)

@Serializable
data class ChapterInfo(
    val id: Long = 0,
    val name: String = "",
    val images: List<String> = emptyList(),
    val series: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonNull,
)

@Serializable
data class PageImage(
    val page: Int = 0,
    val image: String = "",
)

@Serializable
data class ComicRead(
    val id: Long = 0,
    @SerialName("scramble_id") val scrambleId: Long = 0,
    @SerialName("total_page") val totalPage: Int = 0,
    val name: String = "",
    val images: List<PageImage> = emptyList(),
)

@Serializable
data class AppSetting(
    @SerialName("img_host") val imgHost: String = "",
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("main_web_host") val mainWebHost: String = "",
    @SerialName("jm3_version") val jm3Version: String = "",
    @SerialName("jm3_download_url") val jm3DownloadUrl: String = "",
    @SerialName("jm3_version_info") val jm3VersionInfo: String = "",
)

/** 阅读器里的一话。单本作品只有一条。 */
data class ChapterEntry(
    val id: Long,
    val title: String,
    val sort: Int? = null,
)

@Serializable
data class Member(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val s: String = "",
    val jwttoken: String = "",
    @SerialName("album_favorites") val totalFavorites: Int = 0,
    val level: Int = 0,
)

@Serializable
data class FavoritePage(
    val list: List<AlbumBrief> = emptyList(),
    val total: String = "",
    val count: Int = 0,
)

@Serializable
data class HistoryPage(
    val list: List<AlbumBrief> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SearchPage(
    val total: Int = 0,
    val content: List<AlbumBrief> = emptyList(),
)

@Serializable
data class WeekOptions(
    val categories: List<WeekCategory> = emptyList(),
    val type: List<WeekType> = emptyList(),
)

@Serializable
data class WeekCategory(
    val id: String = "",
    val title: String = "",
    val time: String = "",
)

@Serializable
data class WeekType(
    val id: String = "",
    val title: String = "",
)

@Serializable
data class WeekPage(
    val total: Int = 0,
    val list: List<AlbumBrief> = emptyList(),
)

@Serializable
data class CategoryBlock(
    val title: String = "",
    val content: List<String> = emptyList(),
)

@Serializable
data class SubCategory(
    @SerialName("CID") val cid: String = "",
    val name: String = "",
    val slug: String = "",
)

@Serializable
data class CategoryNode(
    val name: String = "",
    val slug: String = "",
    val type: String = "",
    @SerialName("sub_categories") val subCategories: List<SubCategory> = emptyList(),
)

@Serializable
data class CategoryResponse(
    val categories: List<CategoryNode> = emptyList(),
    val blocks: List<CategoryBlock> = emptyList(),
)

@Serializable
data class CommentItem(
    @SerialName("CID") val cid: String = "",
    val username: String = "",
    val nickname: String = "",
    val content: String = "",
    val likes: String = "0",
    val addtime: String = "",
    @SerialName("parent_CID") val parentCid: String = "0",
)

@Serializable
data class ForumPage(
    val list: List<CommentItem> = emptyList(),
    val total: Int = 0,
)
