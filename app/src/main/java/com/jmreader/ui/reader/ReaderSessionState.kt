package com.jmreader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.app.AppContainer
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.ComicRead

/**
 * 阅读器当前话的正文状态（普通类，不是 ViewModel）。
 *
 * 由 [ReaderScreen] 用 `remember(cid)` 创建。`read` 本身不带 cid，
 * 靠 `loadedCid` 保证切话瞬间上一话的图片/页码不会串到新的一话——
 * 只有 [current] 才是可以渲染的正文。
 *
 * 目录不在这里：同一作品的多话共用一份目录，见 [ReaderChaptersState]。
 */
class ReaderSessionState(
    val albumId: Long,
    val cid: Long,
    private val container: AppContainer,
) {

    private val repo get() = container.repository

    private var read by mutableStateOf<ComicRead?>(null)
    private var loadedCid by mutableStateOf<Long?>(null)

    var error by mutableStateOf<String?>(null)
        private set

    /** 属于当前 [cid] 的正文；切话后、加载完成前都是 null。 */
    val current: ComicRead? get() = read?.takeIf { loadedCid == cid }

    /** 已下载的章节先用本地元数据秒开（断网也能进），再用线上数据刷新。 */
    suspend fun load() {
        read = null
        loadedCid = null
        error = null

        val local = container.downloads.localComicRead(cid)
        if (local != null) {
            read = local
            loadedCid = cid
        }

        runCatchingCancellable { repo.comicRead(cid) }
            .onSuccess {
                read = it
                loadedCid = cid
            }
            .onFailure { throwable ->
                // 有本地数据时静默忽略线上失败，继续用离线版本
                if (local == null) error = throwable.message ?: "載入失敗"
            }
    }
}
