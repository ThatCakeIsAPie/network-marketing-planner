package com.networkmarketing.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Mist,
    onPrimaryContainer = TealDark,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFF3E6C8),
    onSecondaryContainer = Color(0xFF3F2F0B),
    tertiary = TealDark,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF3F4A45),
    outline = Color(0xFF6F7C76),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFF101412),
    onBackground = Cream,
    surface = Color(0xFF171C1A),
    onSurface = Cream,
    surfaceVariant = Color(0xFF2A3330),
    onSurfaceVariant = Color(0xFFC5D0CB),
)

@Composable
fun PlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
