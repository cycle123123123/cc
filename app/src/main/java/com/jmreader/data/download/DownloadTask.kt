package com.jmreader.data.download

import kotlinx.serialization.Serializable
/** 下载任务元数据，存到 downloads/{cid}/meta.json。 */
@Serializable
data class DownloadTask(
    val cid: Long,
    val albumId: Long,
    val title: String,
    val totalPages: Int,
    val scrambleId: Long = 0,
    val completed: Int = 0,
    /** 每页原图文件名（按页序，不含扩展名）；离线还原分条时用，旧任务为空。 */
    val keys: List<String> = emptyList(),
    val status: String = STATUS_PENDING,
    val updatedAt: Long = 0,
) {
    val isRunning: Boolean get() = status == STATUS_RUNNING
    val isDone: Boolean get() = status == STATUS_DONE
    val isPaused: Boolean get() = status == STATUS_PAUSED
    val progress: Float get() = if (totalPages <= 0) 0f else completed.toFloat() / totalPages

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}
