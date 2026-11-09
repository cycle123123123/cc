package com.jmreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.JmRepository
import com.jmreader.data.model.CategoryNode

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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            IosFilterPill(
                selected = selected.isBlank(),
                label = "全部",
                onClick = { onSelect("") },
            )
        }
        items(items = categories, key = { it.slug }) { node ->
            IosFilterPill(
                selected = selected == node.slug,
                label = node.name,
                onClick = { onSelect(node.slug) },
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = SEARCH_SORTS, key = { it.first }) { sort ->
            IosFilterPill(
                selected = selected == sort.first,
                label = sort.second,
                onClick = { onSelect(sort.first) },
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
