package com.jmreader.ui.common

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 统一的长/短 Toast：`toast("...")` 默认 SHORT，`toast("...", true)` 为 LONG。
 * 只替换调用方式，文案与时长与原来一致。
 */
class Toaster(private val context: Context) {
    operator fun invoke(message: String, long: Boolean = false) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberToaster(): Toaster {
    val context = LocalContext.current
    return remember(context) { Toaster(context) }
}
