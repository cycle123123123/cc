package com.jmreader.data.local

import com.jmreader.core.formatCount

/** 列表卡片要显示的补充信息：点击量 / 爱心数 / 页数 / 是否日文。都来自 `/album`。 */
data class AlbumMeta(
    val views: Int = 0,
    val likes: Int = 0,
    val pages: Int = 0,
    val japanese: Boolean = false,
)

/** 底部角标：「👁 6.1 萬 · ♥ 329」 */
internal fun metaBadge(meta: AlbumMeta?): String? {
    if (meta == null) return null
    val parts = buildList {
        if (meta.views > 0) add("👁 ${formatCount(meta.views)}")
        if (meta.likes > 0) add("♥ ${formatCount(meta.likes)}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** 右上角角标：「日文 · 296P」/「296P」/「日文」 */
internal fun metaTopBadge(meta: AlbumMeta?): String? {
    if (meta == null) return null
    return when {
        meta.japanese && meta.pages > 0 -> "日文 · ${meta.pages}P"
        meta.pages > 0 -> "${meta.pages}P"
        meta.japanese -> "日文"
        else -> null
    }
}
