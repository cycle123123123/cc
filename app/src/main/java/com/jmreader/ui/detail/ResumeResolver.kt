package com.jmreader.ui.detail

import com.jmreader.data.local.LastRead
import com.jmreader.data.model.ChapterEntry
/**
 * 详情页「續讀」按钮的落点：
 * 优先本作品的 history 记录，[lastRead] 只作兜底；
 * 记录对应的章节必须还在目录里，否则（章节下架 / cid 对不上 / 目录未加载）返回 null。
 */
internal object ResumeResolver {

    fun resolve(
        history: List<LastRead>,
        lastRead: LastRead?,
        chapters: List<ChapterEntry>,
        albumId: Long,
    ): LastRead? {
        val record = history.firstOrNull { it.albumId == albumId }
            ?: lastRead?.takeIf { it.albumId == albumId }
        return record?.takeIf { rec -> chapters.any { it.id == rec.cid } }
    }

    fun indexOf(chapters: List<ChapterEntry>, record: LastRead?): Int =
        record?.let { rec -> chapters.indexOfFirst { it.id == rec.cid } } ?: -1
}
