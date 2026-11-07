package com.jmreader.data

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
