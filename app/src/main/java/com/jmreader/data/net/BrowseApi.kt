package com.jmreader.data.net

import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.CreatorAuthor
import com.jmreader.data.model.CreatorAuthorResponse
import com.jmreader.data.model.CreatorWork
import com.jmreader.data.model.CreatorWorkResponse
import com.jmreader.data.model.ForumPage
import com.jmreader.data.model.PromoteSection
import com.jmreader.data.model.WeekOptions
import com.jmreader.data.model.WeekPage
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import com.jmreader.data.JmRepository

/** 只读接口所需的「取 JSON 并反序列化」能力，由 [com.jmreader.data.JmRepository] 实现。 */
internal interface ApiFetcher {
    suspend fun <T> fetch(
        path: String,
        params: Map<String, String>,
        deserializer: DeserializationStrategy<T>,
    ): T
}

/**
 * 无状态只读接口：推广位、随机推荐、创作者、评论列表、周榜。
 * 拆出来只为缩短仓库文件，行为与拆分前完全一致。
 */
internal class BrowseApi(private val fetcher: ApiFetcher) {

    suspend fun promote(): List<PromoteSection> =
        fetcher.fetch("promote", emptyMap(), ListSerializer(PromoteSection.serializer()))

    suspend fun randomRecommend(): List<AlbumBrief> =
        fetcher.fetch("random_recommend", emptyMap(), ListSerializer(AlbumBrief.serializer()))

    suspend fun creatorAuthors(page: Int): List<CreatorAuthor> {
        val response = fetcher.fetch(
            "creator_author",
            mapOf("page" to page.toString()),
            CreatorAuthorResponse.serializer(),
        )
        return response.data.content
    }

    suspend fun creatorWorks(authorId: String, page: Int): List<CreatorWork> {
        val response = fetcher.fetch(
            "creator_work",
            mapOf("author_id" to authorId, "page" to page.toString()),
            CreatorWorkResponse.serializer(),
        )
        return response.data.content
    }

    suspend fun forum(aid: Long, page: Int): ForumPage =
        fetcher.fetch("forum", mapOf("aid" to aid.toString(), "page" to page.toString()), ForumPage.serializer())

    suspend fun week(): WeekOptions = fetcher.fetch("week", emptyMap(), WeekOptions.serializer())

    /** 周榜：type = manga/another，period = 期数 id（`/week` 的 categories[].id）。 */
    suspend fun weekList(type: String, period: String, page: Int): WeekPage {
        val params = mutableMapOf("page" to page.toString())
        if (type.isNotBlank()) params["type"] = type
        if (period.isNotBlank()) params["id"] = period
        return fetcher.fetch("week/filter", params, WeekPage.serializer())
    }
}
