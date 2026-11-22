package com.jmreader.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect

import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.navigation.AppRoot
import com.jmreader.ui.reader.ReaderKeyBus
import com.jmreader.ui.theme.JmTheme
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as JmApplication).container
        setContent {
            // 隱私防窺：開啟後多任務縮略圖與截屏都會被系統遮掉
            val secure = container.appPrefs.privacySecureState.value
            LaunchedEffect(secure) {
                if (secure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                JmTheme {
                    AppRoot(container.repository)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ReaderKeyBus.active && isVolumeKey(keyCode)) {
            ReaderKeyBus.emit(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (ReaderKeyBus.active && isVolumeKey(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
}
