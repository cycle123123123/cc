package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.CategoryNode
import com.jmreader.data.JmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 列表卡片要显示的补充信息：点击量 / 爱心数 / 是否日文。都来自 `/album`。 */
data class AlbumMeta(
    val views: Int = 0,
    val likes: Int = 0,
    val japanese: Boolean = false,
)

/** 同人 / 單本 / 短篇 / 韓漫… 分类筛选行，搜索页和作者页共用。 */
@Composable
fun CategoryChips(
    categories: List<CategoryNode>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected.isBlank(),
                onClick = { onSelect("") },
                label = { Text("全部") },
            )
        }
        items(items = categories, key = { it.slug }) { node ->
            FilterChip(
                selected = selected == node.slug,
                onClick = { onSelect(node.slug) },
                label = { Text(node.name) },
            )
        }
    }
}

/** 搜索页/作者页的排序选项（对应接口参数 o）。`mp` 服务端会 500，不提供。 */
internal val SEARCH_SORTS = listOf(
    "" to "默認",
    "mv" to "最多點閱",
    "tf" to "最多愛心",
)

/** 排序行，搜索页和作者页共用。 */
@Composable
fun SortChips(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = SEARCH_SORTS, key = { it.first }) { sort ->
            FilterChip(
                selected = selected == sort.first,
                onClick = { onSelect(sort.first) },
                label = { Text(sort.second) },
            )
        }
    }
}

/** `/categories` 里 type=slug 的才是作品大类（同人/單本/短篇/韓漫…）。 */
suspend fun loadWorkCategories(repo: JmRepository): List<CategoryNode> =
    runCatchingCancellable { repo.categories() }
        .getOrNull()
        ?.categories
        ?.filter { it.type == "slug" && it.slug.isNotBlank() }
        .orEmpty()

/**
 * 列表接口不返回点击量/爱心数/标签，只有 `/album` 有：
 * 卡片可见时按需补一次（并发 2，结果缓存在调用方的 map 里）。
 */
internal fun ensureAlbumMeta(
    repo: JmRepository,
    scope: CoroutineScope,
    gate: Semaphore,
    metas: MutableMap<String, AlbumMeta>,
    requested: MutableSet<String>,
    albumId: String,
) {
    if (metas[albumId] != null) return
    if (!requested.add(albumId)) return
    scope.launch {
        val aid = albumId.toLongOrNull() ?: return@launch
        gate.withPermit {
            runCatchingCancellable { repo.album(aid) }
                .onSuccess { detail ->
                    metas[albumId] = AlbumMeta(
                        views = detail.viewCount,
                        likes = detail.likes,
                        // 日文/日語是语言标签，生肉=未翻译的日文原版
                        japanese = detail.tags.any { tag ->
                            tag.contains("日語") || tag.contains("日文") || tag.contains("生肉")
                        },
                    )
                }
        }
    }
}

/** 「觀看 6.1 萬 · ♥ 329 · 日文」 */
internal fun metaBadge(meta: AlbumMeta?): String? {
    if (meta == null) return null
    val parts = buildList {
        if (meta.views > 0) add("觀看 ${formatCount(meta.views)}")
        if (meta.likes > 0) add("♥ ${formatCount(meta.likes)}")
        if (meta.japanese) add("日文")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
