package com.jmreader.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.navigation.AppRoot
import com.jmreader.ui.reader.ReaderKeyBus
import com.jmreader.ui.theme.JmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as JmApplication).container
        setContent {
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
