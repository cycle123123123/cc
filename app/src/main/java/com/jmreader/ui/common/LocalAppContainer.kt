package com.jmreader.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.jmreader.app.AppContainer

/** 应用级单例集合，[com.jmreader.app.MainActivity] 里 provide。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
