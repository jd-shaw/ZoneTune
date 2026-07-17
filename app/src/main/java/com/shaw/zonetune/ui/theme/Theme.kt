package com.shaw.zonetune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = StudioSteel,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8EC),
    onPrimaryContainer = StudioInk,
    secondary = StudioSteel,
    onSecondary = Color.White,
    background = StudioBackground,
    onBackground = StudioInk,
    surface = StudioSurface,
    onSurface = StudioInk,
    surfaceVariant = Color(0xFFEBEEF2),
    onSurfaceVariant = StudioMute,
    outline = StudioLine,
    outlineVariant = StudioLine,
    error = StudioError,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = StudioSteelDark,
    onPrimary = StudioBackgroundDark,
    primaryContainer = Color(0xFF1E3A42),
    onPrimaryContainer = StudioInkDark,
    secondary = StudioSteelDark,
    onSecondary = StudioBackgroundDark,
    background = StudioBackgroundDark,
    onBackground = StudioInkDark,
    surface = StudioSurfaceDark,
    onSurface = StudioInkDark,
    surfaceVariant = Color(0xFF242A33),
    onSurfaceVariant = StudioMuteDark,
    outline = StudioLineDark,
    outlineVariant = StudioLineDark,
    error = Color(0xFFF97066),
    onError = StudioBackgroundDark,
)

@Composable
fun ZoneTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
