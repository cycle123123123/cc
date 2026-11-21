package com.jmreader.core

import java.io.File
import java.io.IOException

/**
 * 原子写文件：先写同目录的 `<name>.tmp`，成功后再 rename 覆盖目标。
 *
 * 进程在写入中途被杀时，正式文件要么还是旧内容，要么不存在，不会留下半截文件
 * （下载页之前正是这个坑：`length() > 0` 就把半截文件当成已完成）。
 */
object AtomicFile {

    fun writeBytes(target: File, bytes: ByteArray) = write(target) { it.writeBytes(bytes) }

    fun writeText(target: File, text: String) = write(target) { it.writeText(text) }

    /**
     * 清理目录下残留的 `.tmp`。只用于上次进程被杀留下的垃圾；
     * 调用方应保证目录此时没有正在进行的写入（例如下载仓库启动时）。
     */
    fun cleanupTemp(dir: File) {
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(TMP_SUFFIX) }
            .forEach { it.delete() }
    }

    /** 供实现与测试复用：把 [block] 写到临时文件，成功后替换 [target]。 */
    internal fun write(target: File, block: (File) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            block(tmp)
            if (!tmp.renameTo(target)) {
                // Android/Linux 上 rename 可以直接覆盖；个别文件系统（或测试用的 Windows）
                // 目标已存在时会失败，删掉再试一次。
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("rename failed: $tmp -> $target")
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private const val TMP_SUFFIX = ".tmp"
}
