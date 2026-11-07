package com.jmreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jmreader.ui.AppRoot
import com.jmreader.ui.JmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JmTheme {
                AppRoot(JmApplication.repository)
            }
        }
    }
}
