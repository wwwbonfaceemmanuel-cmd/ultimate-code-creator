package com.ultimate.codecreator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CyanAccent,
    onPrimary = DarkBg,
    secondary = GreenAccent,
    onSecondary = DarkBg,
    surface = DarkSurface,
    background = DarkBg,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextMuted,
    surfaceVariant = DarkElevated,
    error = RedAccent
)

@Composable
fun UltimateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography, content = content)
}
