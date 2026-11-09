package com.jmreader.data.local

import androidx.compose.runtime.mutableStateMapOf
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.JmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 全局的卡片角标缓存（点击量 / 爱心 / 页数 / 是否日文）。
 *
 * 列表接口不返回这些字段，只有 `/album` 有：卡片可见时按需补一次（并发 2）。
 * 原来的 `ensureAlbumMeta` 把状态挂在各自的屏幕里，这里统一成应用级单例，
 * 读取用 [get]，请求用 [request]。
 */
object AlbumMetaCache {

    private val metas = mutableStateMapOf<String, AlbumMeta>()
    private val requested = mutableSetOf<String>()
    private val gate = Semaphore(2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    operator fun get(albumId: String): AlbumMeta? = metas[albumId]

    fun request(repo: JmRepository, albumId: String) {
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
}
