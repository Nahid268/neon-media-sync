package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonBackgroundDarker,
    primaryContainer = Color(0x3300F2FE),
    onPrimaryContainer = NeonCyan,
    secondary = NeonBlue,
    onSecondary = NeonBackgroundDarker,
    secondaryContainer = Color(0x334FACFE),
    onSecondaryContainer = NeonBlue,
    tertiary = NeonPink,
    onTertiary = NeonBackgroundDarker,
    tertiaryContainer = Color(0x33FF007F),
    onTertiaryContainer = NeonPink,
    background = NeonBackground,
    onBackground = NeonTextPrimary,
    surface = NeonSurface,
    onSurface = NeonTextPrimary,
    surfaceVariant = NeonSurfaceGlassLight,
    onSurfaceVariant = NeonTextSecondary,
    outline = NeonBorderCyan,
    error = NeonError,
    onError = NeonBackgroundDarker
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = Typography,
        content = content
    )
}
