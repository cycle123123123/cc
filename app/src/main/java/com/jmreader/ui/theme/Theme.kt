package com.jmreader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// iOS App Store / Apple Books 标志性灰白极简配色
val IosBlue = Color(0xFF007AFF)
val IosBlueSoft = Color(0xFFE8F2FF)
val IosPink = Color(0xFFFF2D55)
val IosGroupedBg = Color(0xFFF2F2F7)
val IosCardWhite = Color(0xFFFFFFFF)
val IosFillGray = Color(0xFFE5E5EA)
val IosSearchBg = Color(0xFFE3E3E8)
val IosLabelPrimary = Color(0xFF1C1C1E)
val IosLabelSecondary = Color(0xFF8E8E93)
val IosSeparator = Color(0xFFD1D1D6)

private val Colors = lightColorScheme(
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
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private val IosTypography = Typography(
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = IosGroupedBg.toArgb()
            window.navigationBarColor = IosCardWhite.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = Colors,
        typography = IosTypography,
        content = content,
    )
}
