package com.jmreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFF2789F),
    onPrimary = Color(0xFF1A0A10),
    secondary = Color(0xFFB9C4D6),
    background = Color(0xFF0F0F14),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF17171E),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF23232C),
    onSurfaceVariant = Color(0xFFB9B9C6),
)

@Composable
fun JmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
