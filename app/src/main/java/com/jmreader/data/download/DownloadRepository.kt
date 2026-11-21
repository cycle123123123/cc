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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import com.jmreader.core.AtomicFile
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

    /** 「下載全部」時限流：最多同時下 2 話，其餘排隊等許可。 */
    private val chapterGate = Semaphore(MAX_PARALLEL_CHAPTERS)

    private val api by lazy { JmRepository.of(context) }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    init {
        refresh()
        resetStaleTasks()
        // 上次进程被杀可能留下半截 .tmp，启动时清掉；正式文件由原子写保证完整
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { AtomicFile.cleanupTemp(it) }
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
            // 原子写：meta.json 写到一半被杀会变成坏 JSON，任务会从列表里静默消失
            AtomicFile.writeText(
                File(dir(updated.cid), META),
                json.encodeToString(DownloadTask.serializer(), updated),
            )
        }
        // 只更新内存里的列表，避免每下载一页都重扫所有任务的 meta.json（O(n²) IO）
        val next = _tasks.value.toMutableList()
        val index = next.indexOfFirst { it.cid == updated.cid }
        if (index >= 0) next[index] = updated else next.add(updated)
        next.sortByDescending { it.updatedAt }
        _tasks.value = next
    }

    fun dir(cid: Long): File = File(root, cid.toString())

    /** 写入走原子替换，所以「文件存在且非空」可以安全地当作该页已完成。 */
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

    /** 加入下载队列；已在下载或已完成时返回 false（方便「下載全部」统计真实数量）。 */
    fun enqueue(cid: Long, albumId: Long, title: String): Boolean {
        val existing = get(cid)
        if (existing?.isDone == true) return false
        if (jobs[cid]?.isActive == true) return false

        save(
            DownloadTask(
                cid = cid,
                albumId = albumId,
                title = title,
                totalPages = existing?.totalPages ?: 0,
                scrambleId = existing?.scrambleId ?: 0,
                completed = existing?.completed ?: 0,
                keys = existing?.keys ?: emptyList(),
                status = DownloadTask.STATUS_PENDING,
            ),
        )
        start(cid, albumId, title)
        return true
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
            // 分条还原依赖原图文件名（md5(aid+文件名)），离线时必须还原来文件名；
            // 旧任务没存 keys，用 5 位页号兜底（站点文件名就是 00001 这种）。
            images = (1..task.totalPages).map { page ->
                val key = task.keys.getOrNull(page - 1)
                    ?: page.toString().padStart(5, '0')
                PageImage(page, "local://$cid/$key")
            },
        )
    }

    private fun start(cid: Long, albumId: Long, title: String) {
        jobs[cid] = scope.launch {
            try {
                chapterGate.withPermit {
                    downloadChapter(cid, albumId, title)
                }
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
        // 记下每页原图文件名：离线打开时要用它算分条数
        val keys = comic.images.sortedBy { it.page }.map { imageKey(it.image) }
        var task = DownloadTask(
            cid = cid,
            albumId = albumId,
            title = title.ifBlank { comic.name },
            totalPages = comic.totalPage.coerceAtLeast(comic.images.size),
            scrambleId = comic.scrambleId,
            completed = comic.images.count { pageFile(cid, it.page) != null },
            keys = keys,
            status = DownloadTask.STATUS_RUNNING,
        )
        save(task)

        for (page in comic.images) {
            currentCoroutineContext().ensureActive()
            if (pageFile(cid, page.page) != null) continue

            val bytes = api.downloadBytes(page.image)
            // 先写 .tmp 再 rename：中断时不会留下被当成「已完成」的半截页文件
            AtomicFile.writeBytes(pageFileForWrite(cid, page.page), bytes)

            task = task.copy(completed = task.completed + 1)
            save(task)
        }

        save(task.copy(completed = task.totalPages, status = DownloadTask.STATUS_DONE, keys = keys))
    }

    private fun imageKey(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')

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

        /** 同時下載的章節數上限，避免「下載全部」一次起幾十個協程。 */
        private const val MAX_PARALLEL_CHAPTERS = 2

        @Volatile
        private var instance: DownloadRepository? = null

        fun of(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}
