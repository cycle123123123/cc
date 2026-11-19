package com.jmreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jmreader.data.local.AppPrefs

/** 0 = 跟隨系統，1 = 淺色，2 = 深色。 */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * iOS 風格調色板。
 *
 * 深色模式不在每個調用點判斷：`IosCardWhite` 這些常量改成讀 [JmPalette] 的 getter，
 * 由 [JmTheme] 在組合開始處寫入當前模式，各頁面原樣使用常量即可跟著切換。
 */
object JmPalette {
    @Volatile
    var dark: Boolean = false
        internal set

    val blue get() = if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val blueSoft get() = if (dark) Color(0xFF16324F) else Color(0xFFE8F2FF)
    val pink get() = if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    val groupedBg get() = if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val card get() = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val fillGray get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val searchBg get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFE3E3E8)
    val labelPrimary get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
    val labelSecondary get() = if (dark) Color(0xFF98989F) else Color(0xFF8E8E93)
    val separator get() = if (dark) Color(0xFF38383A) else Color(0xFFD1D1D6)

    /** 卡片描邊：淺色用極淡黑，深色用極淡白。 */
    val hairline get() = if (dark) Color(0x1FFFFFFF) else Color(0x12000000)

    /** 「抬升」表面：分段控件選中塊、浮起按鈕。 */
    val raised get() = if (dark) Color(0xFF3A3A3C) else Color(0xFFFFFFFF)
}

val IosBlue: Color get() = JmPalette.blue
val IosBlueSoft: Color get() = JmPalette.blueSoft
val IosPink: Color get() = JmPalette.pink
val IosGroupedBg: Color get() = JmPalette.groupedBg
val IosCardWhite: Color get() = JmPalette.card
val IosFillGray: Color get() = JmPalette.fillGray
val IosSearchBg: Color get() = JmPalette.searchBg
val IosLabelPrimary: Color get() = JmPalette.labelPrimary
val IosLabelSecondary: Color get() = JmPalette.labelSecondary
val IosSeparator: Color get() = JmPalette.separator
val IosHairline: Color get() = JmPalette.hairline
val IosRaised: Color get() = JmPalette.raised

private fun lightColors() = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueSoft,
    onPrimaryContainer = IosBlue,
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    tertiary = IosPink,
    background = IosGroupedBg,
    onBackground = IosLabelPrimary,
    surface = IosCardWhite,
    onSurface = IosLabelPrimary,
    surfaceVariant = IosFillGray,
    onSurfaceVariant = IosLabelSecondary,
    outline = IosSeparator,
    outlineVariant = IosFillGray,
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private fun darkColors() = darkColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueSoft,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    tertiary = IosPink,
    background = IosGroupedBg,
    onBackground = IosLabelPrimary,
    surface = IosCardWhite,
    onSurface = IosLabelPrimary,
    surfaceVariant = IosFillGray,
    onSurfaceVariant = IosLabelSecondary,
    outline = IosSeparator,
    outlineVariant = IosFillGray,
    error = Color(0xFFFF453A),
    onError = Color.White,
)

private fun iosTypography() = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = IosLabelPrimary,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = IosLabelPrimary,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = IosLabelPrimary,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = IosLabelPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = IosLabelPrimary,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = IosLabelPrimary,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
    ),
)

@Composable
fun JmTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs.of(context) }
    val dark = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    // 供常量 getter 讀取；必須在內容組合之前寫入
    JmPalette.dark = dark

    val view = LocalView.current
    val colorScheme = remember(dark) { if (dark) darkColors() else lightColors() }
    val typography = remember(dark) { iosTypography() }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = IosGroupedBg.toArgb()
            window.navigationBarColor = IosGroupedBg.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
