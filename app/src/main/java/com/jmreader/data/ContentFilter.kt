package com.jmreader.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BlockRules(
    val keywords: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val hideFavoritedAlbums: Boolean = true,
    val hideFavoritedAuthors: Boolean = false,
    val favoritedAlbums: List<String> = emptyList(),
    val favoritedAuthors: List<String> = emptyList(),
    val syncedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = keywords.isEmpty() && authors.isEmpty() && albums.isEmpty()

    /** 预编译匹配器：避免每个列表项都线性扫 1000+ 条收藏 id / 作者。 */
    val matcher: Matcher by lazy(LazyThreadSafetyMode.PUBLICATION) { Matcher(this) }
}

/**
 * [BlockRules] 的匹配加速版本：
 * - 作品 id 走 HashSet，O(1)；
 * - 标题/作者/分类/标签只在有关键词时才拼一次小写串；
 * - 作者规则和收藏作者仍是子串匹配，但提前小写化、去空。
 */
class Matcher(rules: BlockRules) {

    private val albums: Set<String> = rules.albums.toHashSet()

    private val keywords: List<String> = rules.keywords
        .mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }

    private val authors: List<String> = rules.authors
        .mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }

    private val favoriteAlbums: Set<String> =
        if (rules.hideFavoritedAlbums) rules.favoritedAlbums.toHashSet() else emptySet()

    private val favoriteAuthors: List<String> = if (rules.hideFavoritedAuthors) {
        rules.favoritedAuthors.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }
    } else {
        emptyList()
    }

    fun matches(
        id: String?,
        name: String,
        author: String,
        category: String?,
        tags: List<String>,
    ): Boolean {
        if (!id.isNullOrBlank()) {
            if (albums.contains(id)) return true
            if (favoriteAlbums.contains(id)) return true
        }

        val lowerAuthor = author.lowercase()
        if (favoriteAuthors.isNotEmpty() && lowerAuthor.isNotEmpty()) {
            for (favorite in favoriteAuthors) {
                if (lowerAuthor.contains(favorite) || favorite.contains(lowerAuthor)) return true
            }
        }
        if (authors.isNotEmpty()) {
            for (blocked in authors) {
                if (lowerAuthor.contains(blocked)) return true
            }
        }
        if (keywords.isNotEmpty()) {
            val haystack = buildString {
                append(name.lowercase())
                append('\n')
                append(lowerAuthor)
                append('\n')
                if (!category.isNullOrBlank()) {
                    append(category.lowercase())
                    append('\n')
                }
                for (tag in tags) {
                    append(tag.lowercase())
                    append('\n')
                }
            }
            for (keyword in keywords) {
                if (haystack.contains(keyword)) return true
            }
        }
        return false
    }
}

/**
 * 本地内容屏蔽：关键词（标题/作者/分类/标签）、作者、作品 id。
 * 对所有列表生效，包括收藏。
 */
class ContentFilter private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jmreader_blocks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<BlockRules> = _rules.asStateFlow()

    private fun load(): BlockRules =
        prefs.getString("rules", null)
            ?.let { runCatching { json.decodeFromString(BlockRules.serializer(), it) }.getOrNull() }
            ?: BlockRules()

    private fun save(rules: BlockRules) {
        _rules.value = rules
        prefs.edit().putString("rules", json.encodeToString(BlockRules.serializer(), rules)).apply()
    }

    fun addKeyword(value: String) {
        val v = value.trim()
        if (v.isNotEmpty()) save(_rules.value.copy(keywords = (_rules.value.keywords + v).distinct()))
    }

    fun removeKeyword(value: String) = save(_rules.value.copy(keywords = _rules.value.keywords - value))

    fun addAuthor(value: String) {
        val v = value.trim()
        if (v.isNotEmpty()) save(_rules.value.copy(authors = (_rules.value.authors + v).distinct()))
    }

    fun removeAuthor(value: String) = save(_rules.value.copy(authors = _rules.value.authors - value))

    fun addAlbum(value: String) {
        val v = value.trim()
        if (v.isNotEmpty()) save(_rules.value.copy(albums = (_rules.value.albums + v).distinct()))
    }

    fun removeAlbum(value: String) = save(_rules.value.copy(albums = _rules.value.albums - value))

    fun setHideFavoritedAlbums(value: Boolean) =
        save(_rules.value.copy(hideFavoritedAlbums = value))

    fun setHideFavoritedAuthors(value: Boolean) =
        save(_rules.value.copy(hideFavoritedAuthors = value))

    /** 收藏/取消收藏时同步本地去重集合。 */
    fun recordFavorite(id: String, author: String, add: Boolean) {
        val albums = _rules.value.favoritedAlbums.toMutableSet()
        val authors = _rules.value.favoritedAuthors.toMutableSet()
        if (add) {
            albums.add(id)
            if (author.isNotBlank()) authors.add(author.trim())
        } else {
            albums.remove(id)
        }
        save(_rules.value.copy(favoritedAlbums = albums.toList(), favoritedAuthors = authors.toList()))
    }

    /**
     * 翻页拉取收藏，生成「已收藏作品 / 作者」去重集合。
     * 最多 60 页（约 1200 部）；每批并发 4 页，避免串行几十次请求耗上一分钟。
     */
    suspend fun syncFromFavorites(
        repo: JmRepository,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = coroutineScope {
        val albums = mutableSetOf<String>()
        val authors = mutableSetOf<String>()

        fun collect(list: List<AlbumBrief>) {
            list.forEach { album ->
                albums.add(album.id)
                if (album.author.isNotBlank()) authors.add(album.author.trim())
            }
        }

        val first = repo.favorites(1, "")
        val total = first.total.toIntOrNull() ?: first.list.size
        collect(first.list)
        onProgress(albums.size, total)

        var page = 2
        var emptyPages = 0
        while (page <= MAX_PAGES && albums.size < total && emptyPages < BATCH) {
            val batch = (page until minOf(page + BATCH, MAX_PAGES + 1)).toList()
            val results = batch
                .map { p -> async { runCatching { repo.favorites(p, "") }.getOrNull() } }
                .awaitAll()
            for (result in results) {
                val list = result?.list ?: continue
                if (list.isEmpty()) {
                    emptyPages++
                } else {
                    emptyPages = 0
                    collect(list)
                }
            }
            onProgress(albums.size, total)
            page += BATCH
        }

        save(
            _rules.value.copy(
                favoritedAlbums = albums.toList(),
                favoritedAuthors = authors.toList(),
                syncedAt = System.currentTimeMillis(),
            ),
        )
        albums.size
    }

    companion object {
        private const val MAX_PAGES = 60
        private const val BATCH = 4

        @Volatile
        private var instance: ContentFilter? = null

        fun of(context: Context): ContentFilter =
            instance ?: synchronized(this) {
                instance ?: ContentFilter(context.applicationContext).also { instance = it }
            }

        fun matches(album: AlbumBrief, rules: BlockRules): Boolean = matches(
            id = album.id,
            name = album.name,
            author = album.author,
            category = album.category?.title,
            tags = emptyList(),
            rules = rules,
        )

        fun matches(
            id: String?,
            name: String,
            author: String,
            category: String?,
            tags: List<String>,
            rules: BlockRules,
        ): Boolean = rules.matcher.matches(id, name, author, category, tags)
    }
}
