package com.jmreader.data

import java.io.File

/**
 * 阅读页字节缓存：`cacheDir/pages/{cid}/{page}.bin`。
 *
 * 之前这个目录只写不删，看几十话就能堆到几百 MB（系统只会在空间紧张时才清 cache）。
 * 现在：
 * - 总大小超过上限时按「最久未访问」删除，读到某页会更新它的 lastModified 当作访问时间；
 * - 写入先落 `.tmp` 再改名，避免中断留下半截文件被当成缓存命中；
 * - 一分钟内最多扫描一次目录，避免每页都遍历文件树。
 */
class PageCache(
    private val root: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Volatile
    private var lastPruneAt = 0L

    fun file(cid: Long, page: Int): File = File(dir(cid), "$page.bin")

    fun read(cid: Long, page: Int): ByteArray? = runCatching {
        val target = file(cid, page)
        if (!target.exists() || target.length() <= 0L) return null
        target.setLastModified(now())
        target.readBytes()
    }.getOrNull()

    fun write(cid: Long, page: Int, bytes: ByteArray): Boolean = runCatching {
        dir(cid).mkdirs()
        val tmp = File(dir(cid), "$page.tmp")
        tmp.writeBytes(bytes)
        val target = file(cid, page)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return false
        }
        pruneIfDue()
        true
    }.getOrDefault(false)

    fun sizeBytes(): Long = runCatching { allFiles().sumOf { it.length() } }.getOrDefault(0L)

    fun clear() {
        runCatching {
            root.deleteRecursively()
            root.mkdirs()
        }
        lastPruneAt = 0L
    }

    /** 超过上限就删到上限的 90%，避免刚好卡在边界反复扫描。 */
    fun prune(force: Boolean = false) {
        val at = now()
        if (!force && at - lastPruneAt < PRUNE_INTERVAL_MS) return
        lastPruneAt = at

        var total = sizeBytes()
        if (total <= maxBytes) return

        val target = maxBytes * 9 / 10
        for (file in allFiles().sortedBy { it.lastModified() }) {
            if (total <= target) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        removeEmptyDirs()
    }

    private fun pruneIfDue() {
        if (now() - lastPruneAt >= PRUNE_INTERVAL_MS) prune()
    }

    private fun dir(cid: Long): File = File(root, cid.toString())

    private fun allFiles(): List<File> =
        root.listFiles().orEmpty()
            .flatMap { dir -> dir.listFiles().orEmpty().asList() }
            .filter { it.isFile && it.name.endsWith(".bin") }

    private fun removeEmptyDirs() {
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.listFiles().orEmpty().isEmpty() }
            .forEach { it.delete() }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024
        private const val PRUNE_INTERVAL_MS = 60_000L
    }
}
