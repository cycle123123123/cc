package com.jmreader.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 音量键翻页。Compose 拿不到系统音量键，交给 Activity 转发。
 * active 只在阅读器可见时为 true，避免在其他页面误吞音量键。
 */
object ReaderKeyBus {

    @Volatile
    var active: Boolean = false

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun emit(keyCode: Int) {
        _events.tryEmit(keyCode)
    }
}
