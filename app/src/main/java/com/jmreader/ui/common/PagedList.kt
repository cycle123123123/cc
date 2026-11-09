package com.jmreader.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmreader.data.model.userMessage
import kotlinx.coroutines.CancellationException

/** 一页数据：列表 + 总数（部分接口没有总数，用 [Int.MAX_VALUE] 占位）。 */
data class PageResult<T>(val items: List<T>, val total: Int)

/**
 * 手写的分页列表状态：首屏、加载更多、去重、错误保留旧数据。
 *
 * 规则与原各屏实现保持一致：
 * - `loading` 时忽略 [loadMore]，但 [refresh] 可以打断（切换筛选/下拉刷新）；
 * - 服务端重复返回已有 id 视为到底（`fresh` 为空 → `hasMore = false`）；
 * - 出错只记录 [error]，不清空 [items]。
 */
class PagedList<T>(
    private val keyOf: (T) -> String,
    private val fetch: suspend (page: Int) -> PageResult<T>,
) {

    var items by mutableStateOf<List<T>>(emptyList())
        private set

    var total by mutableIntStateOf(0)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var hasMore by mutableStateOf(true)
        private set

    var loaded by mutableStateOf(false)
        private set

    private var page = 1

    /** 清空（切换筛选条件时用），下一次 [refresh] 从第 1 页重新来。 */
    fun reset() {
        items = emptyList()
        total = 0
        page = 1
        hasMore = true
        loaded = false
        error = null
    }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val result = fetch(1)
            items = result.items
            total = result.total
            page = 2
            hasMore = items.isNotEmpty() && items.size < total
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = t.userMessage()
        } finally {
            loading = false
        }
    }

    suspend fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            val result = fetch(page)
            val fresh = result.items.filter { new -> items.none { keyOf(it) == keyOf(new) } }
            items = items + fresh
            total = result.total
            hasMore = fresh.isNotEmpty() && items.size < total
            page += 1
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = t.userMessage()
        } finally {
            loading = false
        }
    }
}
