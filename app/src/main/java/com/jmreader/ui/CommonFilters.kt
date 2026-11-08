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

/** `/categories` 里 type=slug 的才是作品大类（同人/單本/短篇/韓漫…）。 */
suspend fun loadWorkCategories(repo: JmRepository): List<CategoryNode> =
    runCatchingCancellable { repo.categories() }
        .getOrNull()
        ?.categories
        ?.filter { it.type == "slug" && it.slug.isNotBlank() }
        .orEmpty()

/** 列表接口不返回点击量，只有 /album 有：卡片可见时按需补一次（并发 2、结果缓存）。 */
internal fun ensureViewCount(
    repo: JmRepository,
    scope: CoroutineScope,
    gate: Semaphore,
    views: MutableMap<String, Int>,
    requested: MutableSet<String>,
    albumId: String,
) {
    if (views[albumId] != null) return
    if (!requested.add(albumId)) return
    scope.launch {
        val aid = albumId.toLongOrNull() ?: return@launch
        gate.withPermit {
            runCatchingCancellable { repo.album(aid) }
                .onSuccess { detail ->
                    if (detail.viewCount > 0) views[albumId] = detail.viewCount
                }
        }
    }
}

internal fun viewBadge(views: Map<String, Int>, albumId: String): String? =
    views[albumId]?.takeIf { it > 0 }?.let { "觀看 ${formatCount(it)}" }
