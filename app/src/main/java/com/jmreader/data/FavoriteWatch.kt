package com.jmreader.data

import android.content.Context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
/** 一本被追更的韓漫。`resolved` 表示已經拉過詳情、確認過是否連載。 */
@Serializable
data class WatchItem(
    val id: String,
    val name: String,
    val updateAt: Long = 0L,
    val serial: Boolean = false,
    val chapterCount: Int = 0,
    val latestCid: Long = 0L,
    val latestTitle: String = "",
    val resolved: Boolean = false,
)

/** 一次檢查发现的更新。 */
@Serializable
data class WatchUpdate(
    val albumId: Long,
    val name: String,
    val newChapters: Int,
    val latestTitle: String,
)

data class WatchSnapshot(
    val items: List<WatchItem> = emptyList(),
    val updates: List<WatchUpdate> = emptyList(),
    val lastCheckAt: Long = 0L,
    val checking: Boolean = false,
    val error: String? = null,
) {
    val pendingCount: Int get() = updates.size
}

/**
 * 只追「韓漫 + 連載」的更新檢查。
 *
 * 為什麼不是全量：
 * - 收藏裡大量是同人/短篇，不會有新章節，查了純浪費流量；
 * - 韓漫連載才是「會一直出下一話」的類型。
 *
 * 分類直接來自收藏接口的 `category`（無需額外請求）；是否連載必須看詳情裡的
 * `tags` 是否含「連載」，所以首次會為每本韓漫補一次詳情，之後只比對
 * `update_at` / 章節數 / 最新章節 id，不再重複拉詳情。
 */
class FavoriteWatch private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jmreader_watch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<WatchSnapshot> = _snapshot.asStateFlow()

    /** 自動檢查的間隔：6 小時。手動檢查不受限制。 */
    fun isDue(now: Long = System.currentTimeMillis()): Boolean =
        now - _snapshot.value.lastCheckAt >= CHECK_INTERVAL_MS

    /**
     * 掃描收藏並比對更新。
     *
     * @param detailBudget 本次最多補多少本詳情（首次建立清單時限制請求量）。
     */
    suspend fun check(
        repo: JmRepository,
        force: Boolean = false,
        detailBudget: Int = DEFAULT_DETAIL_BUDGET,
    ): WatchSnapshot {
        val start = _snapshot.value
        if (!force && !isDue()) return start
        _snapshot.value = start.copy(checking = true, error = null)

        return try {
            val favorites = fetchFavorites(repo)
            val hanman = favorites
                .filter { it.category?.title == HANMAN }
                .sortedByDescending { it.updateAt }

            val previous = start.items.associateBy { it.id }
            val next = ArrayList<WatchItem>(hanman.size)
            val updates = ArrayList<WatchUpdate>()
            var budget = detailBudget

            for (brief in hanman) {
                val aid = brief.id.toLongOrNull() ?: continue
                val old = previous[brief.id]
                val knownChanged = old != null && brief.updateAt != old.updateAt
                val needDetail = old == null || !old.resolved || knownChanged

                if (needDetail && budget > 0) {
                    budget--
                    val detail = runCatching { repo.album(aid) }.getOrNull()
                    if (detail != null) {
                        val chapters = repo.seriesChapters(detail)
                        val latest = chapters.lastOrNull()
                        val serial = isSerial(detail.tags, chapters.size)
                        val item = WatchItem(
                            id = brief.id,
                            name = brief.name,
                            updateAt = brief.updateAt,
                            serial = serial,
                            chapterCount = chapters.size,
                            latestCid = latest?.id ?: 0L,
                            latestTitle = latest?.title.orEmpty(),
                            resolved = true,
                        )
                        if (serial && old != null && old.resolved) {
                            val delta = chapters.size - old.chapterCount
                            if (delta > 0 || (latest?.id ?: 0L) != old.latestCid) {
                                updates += WatchUpdate(
                                    albumId = aid,
                                    name = brief.name,
                                    newChapters = delta.coerceAtLeast(0),
                                    latestTitle = latest?.title.orEmpty(),
                                )
                            }
                        }
                        next += item
                        continue
                    }
                }

                next += old?.copy(name = brief.name, updateAt = brief.updateAt)
                    ?: WatchItem(id = brief.id, name = brief.name, updateAt = brief.updateAt)
            }

            val result = WatchSnapshot(
                items = next,
                // 未被「知道了」的旧提醒要保留，否则 6 小时后自动检查就把它冲掉了
                updates = (
                    updates + start.updates.filter { old -> updates.none { it.albumId == old.albumId } }
                    ).sortedByDescending { it.albumId },
                lastCheckAt = System.currentTimeMillis(),
                checking = false,
                error = null,
            )
            persist(result)
            _snapshot.value = result
            result
        } catch (t: Throwable) {
            val failed = _snapshot.value.copy(checking = false, error = t.message ?: "檢查失敗")
            _snapshot.value = failed
            failed
        }
    }

    /** 用户看過提醒後清掉，避免一直掛著。 */
    fun clearUpdates() {
        val current = _snapshot.value
        if (current.updates.isEmpty()) return
        persist(current.copy(updates = emptyList()))
        _snapshot.value = current.copy(updates = emptyList())
    }

    /** 打开某一本后只清掉它自己的提醒。 */
    fun acknowledge(albumId: Long) {
        val current = _snapshot.value
        val remaining = current.updates.filterNot { it.albumId == albumId }
        if (remaining.size == current.updates.size) return
        persist(current.copy(updates = remaining))
        _snapshot.value = current.copy(updates = remaining)
    }

    private suspend fun fetchFavorites(repo: JmRepository) = buildList {
        var page = 1
        while (page <= MAX_PAGES) {
            val response = repo.favorites(page, "")
            addAll(response.list)
            val reachedEnd = response.list.isEmpty() ||
                response.list.size < PAGE_SIZE ||
                size >= (response.total.toIntOrNull() ?: 0)
            if (reachedEnd) break
            page++
        }
    }

    private fun persist(value: WatchSnapshot) {
        val stored = Stored(
            items = value.items,
            updates = value.updates,
            lastCheckAt = value.lastCheckAt,
        )
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(Stored.serializer(), stored)).apply()
        }
    }

    private fun load(): WatchSnapshot {
        val raw = prefs.getString(KEY, null) ?: return WatchSnapshot()
        val stored = runCatching { json.decodeFromString(Stored.serializer(), raw) }.getOrNull()
            ?: return WatchSnapshot()
        return WatchSnapshot(
            items = stored.items,
            updates = stored.updates,
            lastCheckAt = stored.lastCheckAt,
        )
    }

    @Serializable
    private data class Stored(
        val items: List<WatchItem> = emptyList(),
        val updates: List<WatchUpdate> = emptyList(),
        val lastCheckAt: Long = 0L,
    )

    companion object {
        /** 韓漫在接口裡的分類名。 */
        const val HANMAN = "韓漫"

        private const val KEY = "watch_v1"
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val DEFAULT_DETAIL_BUDGET = 30
        private const val MAX_PAGES = 12
        private const val PAGE_SIZE = 40

        /** 有「連載」標籤，或章節數多於 1 話（有些條目沒打標籤）。 */
        internal fun isSerial(tags: List<String>, chapterCount: Int): Boolean =
            chapterCount > 1 || tags.any { it == "連載" }

        @Volatile
        private var instance: FavoriteWatch? = null

        fun of(context: Context): FavoriteWatch =
            instance ?: synchronized(this) {
                instance ?: FavoriteWatch(context.applicationContext).also { instance = it }
            }
    }
}
