package com.jmreader.data

import android.content.Context
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
     * 最多 60 页（约 1200 部），避免收藏特别多时同步过久。
     */
    suspend fun syncFromFavorites(
        repo: JmRepository,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val albums = mutableSetOf<String>()
        val authors = mutableSetOf<String>()
        var page = 1
        var total = 0

        while (page <= 60) {
            val result = repo.favorites(page, "")
            if (page == 1) total = result.total.toIntOrNull() ?: result.list.size
            if (result.list.isEmpty()) break
            result.list.forEach { album ->
                albums.add(album.id)
                if (album.author.isNotBlank()) authors.add(album.author.trim())
            }
            onProgress(albums.size, total)
            page++
            if (albums.size >= total) break
        }

        save(
            _rules.value.copy(
                favoritedAlbums = albums.toList(),
                favoritedAuthors = authors.toList(),
                syncedAt = System.currentTimeMillis(),
            ),
        )
        return albums.size
    }

    companion object {
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
        ): Boolean {
            if (!id.isNullOrBlank() && rules.albums.contains(id)) return true

            // 已经收藏过的作品不再出现在浏览列表里
            if (rules.hideFavoritedAlbums && !id.isNullOrBlank() && rules.favoritedAlbums.contains(id)) {
                return true
            }
            if (rules.hideFavoritedAuthors && author.isNotBlank()) {
                val hit = rules.favoritedAuthors.any { favoriteAuthor ->
                    favoriteAuthor.isNotBlank() &&
                        (author.contains(favoriteAuthor, true) || favoriteAuthor.contains(author, true))
                }
                if (hit) return true
            }

            val haystacks = buildList {
                if (name.isNotBlank()) add(name)
                if (author.isNotBlank()) add(author)
                if (!category.isNullOrBlank()) add(category)
                addAll(tags)
            }
            if (rules.keywords.any { keyword -> keyword.isNotBlank() && haystacks.any { it.contains(keyword, true) } }) {
                return true
            }
            return rules.authors.any { blocked -> blocked.isNotBlank() && author.contains(blocked, true) }
        }
    }
}
