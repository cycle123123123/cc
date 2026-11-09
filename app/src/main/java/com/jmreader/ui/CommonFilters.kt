package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** iOS 风格胶囊筛选项（无边框、无打勾图标、紧凑圆润） */
@Composable
fun IosFilterPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) IosBlue else IosCardWhite
    val fg = if (selected) Color.White else IosLabelPrimary
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .then(
                if (!selected) {
                    Modifier.border(0.5.dp, Color(0x18000000), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
    }
}

/** iOS 经典分段控制器（浅灰底槽 + 纯白圆角滑块） */
@Composable
fun IosSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE3E3E8))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier
                                .shadow(1.5.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                        } else {
                            Modifier.clip(RoundedCornerShape(8.dp))
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) IosLabelPrimary else IosLabelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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

/** 列表加载失败时的统一空态：中文提示 + 重试。 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("重試") }
        }
    }
}

/** 「觀看 6.1 萬 · ♥ 329 · 日文」 */
internal fun metaBadge(meta: AlbumMeta?): String? {
    if (meta == null) return null
    val parts = buildList {
        if (meta.views > 0) add("👁 ${formatCount(meta.views)}")
        if (meta.likes > 0) add("♥ ${formatCount(meta.likes)}")
        if (meta.japanese) add("日文")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
