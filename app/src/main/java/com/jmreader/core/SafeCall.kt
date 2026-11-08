package com.jmreader.core

import kotlinx.coroutines.CancellationException

/**
 * 和 runCatching 一样，但把 CancellationException 原样抛回去。
 *
 * Compose 的 LaunchedEffect 在 key 变化/离屏时会取消协程，如果被 runCatching 吞掉，
 * 会把 "The coroutine scope left the composition" 当成业务错误显示出来。
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
