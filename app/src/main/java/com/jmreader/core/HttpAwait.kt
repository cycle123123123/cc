package com.jmreader.core

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp 的异步调用包装成可取消的挂起函数。
 *
 * 之前统一用阻塞的 `execute()`，协程取消（例如快速返回上一页）不会中断请求，
 * 请求会继续跑完、白耗流量。这里改成 enqueue + invokeOnCancellation。
 */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}
