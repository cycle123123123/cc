package com.jmreader.data.net

import com.jmreader.core.TtlCache
import com.jmreader.data.local.HomeCache
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.AlbumDetail
import com.jmreader.data.model.CategoryResponse
import com.jmreader.data.model.ChapterInfo
import com.jmreader.data.model.ComicRead
import com.jmreader.data.model.SearchPage
import kotlinx.serialization.builtins.ListSerializer
import com.jmreader.data.JmRepository

/**
 * 浏览类只读接口：首页、详情、章节、阅读页、搜索、分类。
 * 缓存仍由 [com.jmreader.data.JmRepository] 构造后传入，容量与 TTL 不变。
 */
internal class CatalogApi(
    private val fetcher: ApiFetcher,
    private val albumCache: TtlCache<Long, AlbumDetail>,
    private val readCache: TtlCache<Long, ComicRead>,
    private val homeCache: HomeCache,
) {

    suspend fun latest(page: Int): List<AlbumBrief> {
        val list = fetcher.fetch("latest", mapOf("page" to page.toString()), ListSerializer(AlbumBrief.serializer()))
        if (page == 1) homeCache.save(list)
        return list
    }

    /** 冷启动时先用上次的首页数据垫着，避免一直转圈。 */
    fun cachedLatest(): List<AlbumBrief>? = homeCache.load()

    suspend fun album(id: Long): AlbumDetail {
        albumCache.get(id)?.let { return it }
        val album = fetcher.fetch("album", mapOf("id" to id.toString()), AlbumDetail.serializer())
        albumCache.put(id, album)
        return album
    }

    suspend fun chapter(id: Long): ChapterInfo =
        fetcher.fetch("chapter", mapOf("id" to id.toString()), ChapterInfo.serializer())

    suspend fun comicRead(id: Long): ComicRead {
        readCache.get(id)?.let { return it }
        val comic = fetcher.fetch("comic_read", mapOf("id" to id.toString()), ComicRead.serializer())
        readCache.put(id, comic)
        return comic
    }

    /**
     * @param category 分类 slug（同人 doujin / 單本 single / 短篇 short / 韓漫 hanman…），空串表示全部
     * @param order 排序：空=默認，`mv`=最多點閱，`tf`=最多愛心（`mp` 服务端会 500，不用）
     */
    suspend fun search(query: String, page: Int, category: String = "", order: String = ""): SearchPage {
        val params = mutableMapOf("search_query" to query, "page" to page.toString())
        if (category.isNotBlank()) params["c"] = category
        if (order.isNotBlank()) params["o"] = order
        return fetcher.fetch("search", params, SearchPage.serializer())
    }

    suspend fun categories(): CategoryResponse =
        fetcher.fetch("categories", emptyMap(), CategoryResponse.serializer())

    /** 分类浏览：c = 分类 slug，o = 排序（""/mv/mp/tf/old），page 从 1 开始。 */
    suspend fun categoryList(slug: String, order: String, page: Int): SearchPage {
        val params = mutableMapOf("c" to slug, "page" to page.toString())
        if (order.isNotBlank()) params["o"] = order
        return fetcher.fetch("categories/filter", params, SearchPage.serializer())
    }
}
