package com.allvideodownloader.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F1E2),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF3E5F58),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC1E8DE),
    onSecondaryContainer = Color(0xFF001B16),
    tertiary = Color(0xFF3B4E7A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AF0D2),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF9BFFE6),
    secondary = Color(0xFFA5CCC2),
    onSecondary = Color(0xFF0B342C),
    secondaryContainer = Color(0xFF264942),
    onSecondaryContainer = Color(0xFFC1E8DE),
    tertiary = Color(0xFFB4C5FF),
    onTertiary = Color(0xFF04214B),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE5E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE5E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun AllVideoDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colours where the platform supports them. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
