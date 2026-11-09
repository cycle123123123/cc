package com.jmreader.data.download

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.jmreader.data.model.ComicRead
import com.jmreader.data.model.PageImage
import com.jmreader.data.JmRepository

/**
 * 离线下载管理。目录结构：
 *   files/downloads/{cid}/meta.json
 *   files/downloads/{cid}/{page}.bin   原始（置乱）图片字节
 *
 * 只存原始字节，阅读时按 scramble_id 还原，和网络路径共用同一套解码逻辑。
 *
 * 没有用 WorkManager：实测在部分设备/模拟器上网络能力未被标记为 VALIDATED，
 * JobScheduler 会一直不调度。这里用应用内协程队列，暂停/继续由 Job 控制；
 * 进程被杀后，下次启动会把 running/pending 的任务重置为 paused，可手动继续。
 */
class DownloadRepository private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val root = File(context.filesDir, "downloads")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private val api by lazy { JmRepository.of(context) }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        refresh()
        resetStaleTasks()
    }

    fun refresh() {
        val list = runCatching {
            root.listFiles().orEmpty()
                .mapNotNull { dir -> runCatching { readMeta(File(dir, META)) }.getOrNull() }
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
        _tasks.value = list
    }

    fun get(cid: Long): DownloadTask? =
        _tasks.value.firstOrNull { it.cid == cid } ?: runCatching { readMeta(File(dir(cid), META)) }.getOrNull()

    fun save(task: DownloadTask) {
        val updated = task.copy(updatedAt = System.currentTimeMillis())
        runCatching {
            dir(updated.cid).mkdirs()
            File(dir(updated.cid), META).writeText(json.encodeToString(DownloadTask.serializer(), updated))
        }
        // 只更新内存里的列表，避免每下载一页都重扫所有任务的 meta.json（O(n²) IO）
        val next = _tasks.value.toMutableList()
        val index = next.indexOfFirst { it.cid == updated.cid }
        if (index >= 0) next[index] = updated else next.add(updated)
        next.sortByDescending { it.updatedAt }
        _tasks.value = next
    }

    fun dir(cid: Long): File = File(root, cid.toString())

    fun pageFile(cid: Long, page: Int): File? {
        val file = File(dir(cid), "$page.bin")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun pageFileForWrite(cid: Long, page: Int): File {
        dir(cid).mkdirs()
        return File(dir(cid), "$page.bin")
    }

    fun delete(cid: Long) {
        jobs.remove(cid)?.cancel()
        dir(cid).deleteRecursively()
        refresh()
    }

    fun enqueue(cid: Long, albumId: Long, title: String) {
        val existing = get(cid)
        if (existing?.isDone == true) return
        if (jobs[cid]?.isActive == true) return

        save(
            DownloadTask(
                cid = cid,
                albumId = albumId,
                title = title,
                totalPages = existing?.totalPages ?: 0,
                scrambleId = existing?.scrambleId ?: 0,
                completed = existing?.completed ?: 0,
                status = DownloadTask.STATUS_PENDING,
            ),
        )
        start(cid, albumId, title)
    }

    fun pause(cid: Long) {
        jobs.remove(cid)?.cancel()
        get(cid)?.let { save(it.copy(status = DownloadTask.STATUS_PAUSED)) }
    }

    fun resume(cid: Long) {
        val task = get(cid) ?: return
        if (task.isDone || jobs[cid]?.isActive == true) return
        save(task.copy(status = DownloadTask.STATUS_PENDING))
        start(task.cid, task.albumId, task.title)
    }

    /** 断网时用本地元数据构造 ComicRead，保证已下载章节仍能打开。 */
    fun localComicRead(cid: Long): ComicRead? {
        val task = get(cid) ?: return null
        if (task.totalPages <= 0) return null
        return ComicRead(
            id = cid,
            scrambleId = task.scrambleId,
            totalPage = task.totalPages,
            name = task.title,
            images = (1..task.totalPages).map { PageImage(it, "") },
        )
    }

    private fun start(cid: Long, albumId: Long, title: String) {
        jobs[cid] = scope.launch {
            try {
                downloadChapter(cid, albumId, title)
            } catch (_: CancellationException) {
                get(cid)?.let { save(it.copy(status = DownloadTask.STATUS_PAUSED)) }
            } catch (_: Throwable) {
                get(cid)?.let { save(it.copy(status = DownloadTask.STATUS_FAILED)) }
            } finally {
                jobs.remove(cid)
            }
        }
    }

    private suspend fun downloadChapter(cid: Long, albumId: Long, title: String) {
        val comic = api.comicRead(cid)
        var task = DownloadTask(
            cid = cid,
            albumId = albumId,
            title = title.ifBlank { comic.name },
            totalPages = comic.totalPage.coerceAtLeast(comic.images.size),
            scrambleId = comic.scrambleId,
            completed = comic.images.count { pageFile(cid, it.page) != null },
            status = DownloadTask.STATUS_RUNNING,
        )
        save(task)

        for (page in comic.images) {
            currentCoroutineContext().ensureActive()
            if (pageFile(cid, page.page) != null) continue

            val bytes = api.downloadBytes(page.image)
            pageFileForWrite(cid, page.page).writeBytes(bytes)

            task = task.copy(completed = task.completed + 1)
            save(task)
        }

        save(task.copy(completed = task.totalPages, status = DownloadTask.STATUS_DONE))
    }

    /** 进程重启后，把上次没跑完的任务标成暂停，避免永远卡在“下載中”。 */
    private fun resetStaleTasks() {
        for (task in _tasks.value.filter { it.isRunning || it.status == DownloadTask.STATUS_PENDING }) {
            save(task.copy(status = DownloadTask.STATUS_PAUSED))
        }
    }

    private fun readMeta(file: File): DownloadTask? =
        if (!file.exists()) null
        else json.decodeFromString(DownloadTask.serializer(), file.readText())

    companion object {
        private const val META = "meta.json"

        @Volatile
        private var instance: DownloadRepository? = null

        fun of(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}
