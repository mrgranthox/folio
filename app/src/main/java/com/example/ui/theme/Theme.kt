package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = M3PrimaryDark,
    onPrimary = M3OnPrimaryDark,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = TealAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = M3SurfaceDark,
    onBackground = M3OnSurfaceDark,
    surface = M3SurfaceDark,
    onSurface = M3OnSurfaceDark,
    surfaceVariant = M3SurfaceContainerDark,
    onSurfaceVariant = M3OnSurfaceVariantDark,
    surfaceContainer = M3SurfaceContainerDark,
    surfaceContainerHigh = M3SurfaceContainerHighDark,
    outline = M3OutlineDark,
    error = DebitRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = M3PrimaryLight,
    onPrimary = M3OnPrimaryLight,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = M3BackgroundLight,
    onBackground = M3OnSurfaceLight,
    surface = M3SurfaceLight,
    onSurface = M3OnSurfaceLight,
    surfaceVariant = M3SurfaceContainerHighLight,
    onSurfaceVariant = M3OnSurfaceVariantLight,
    surfaceContainer = M3SurfaceContainerLight,
    surfaceContainerHigh = M3SurfaceContainerHighLight,
    outline = M3OutlineLight,
    error = DebitRed,
    onError = Color.White
)

@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
