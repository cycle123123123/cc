package com.jmreader.data.local

import com.jmreader.core.formatCount
import com.jmreader.data.JmRepository
import com.jmreader.core.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 列表卡片要显示的补充信息：点击量 / 爱心数 / 页数 / 是否日文。都来自 `/album`。 */
data class AlbumMeta(
    val views: Int = 0,
    val likes: Int = 0,
    val pages: Int = 0,
    val japanese: Boolean = false,
)

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
                        pages = detail.totalPhotos,
                        // 日文/日語是语言标签，生肉=未翻译的日文原版
                        japanese = detail.tags.any { tag ->
                            tag.contains("日語") || tag.contains("日文") || tag.contains("生肉")
                        },
                    )
                }
        }
    }
}

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
