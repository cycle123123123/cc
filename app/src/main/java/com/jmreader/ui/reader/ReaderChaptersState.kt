package com.jmreader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.jmreader.app.AppContainer
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.ChapterEntry
import com.jmreader.data.model.SeriesParser
/**
 * 作品目录，与 cid 无关：同一作品只加载一次。
 *
 * 必须按 albumId 保活——切到下一话时 cid 变了但目录不变；
 * 如果把它塞进 per-cid 的 [ReaderSessionState]，切话后目录会变空，
 * 上一話/下一話按钮和末尾「下一話」衔接条都会消失。
 */
class ReaderChaptersState(
    private val albumId: Long,
    private val container: AppContainer,
) {

    private val repo get() = container.repository

    var chapters by mutableStateOf<List<ChapterEntry>>(emptyList())
        private set

    /**
     * 優先取 /album 的 series：它一定是數組，且和詳情頁目錄同源。
     * /chapter 的 series 在多話作品裡可能只是「總話數」（數字），
     * 解析為空會導致上一話/下一話永久禁用。
     */
    suspend fun load() {
        val fromAlbum = runCatchingCancellable { repo.seriesChapters(repo.album(albumId)) }
            .getOrNull()
            .orEmpty()
        chapters = if (fromAlbum.isNotEmpty()) {
            fromAlbum
        } else {
            runCatchingCancellable { SeriesParser.parse(repo.chapter(albumId).series) }
                .getOrNull()
                .orEmpty()
        }
    }
}
