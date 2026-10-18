package com.seb.sdpr.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary             = PlexOrange,
    onPrimary           = Color(0xFF111111),
    primaryContainer    = Color(0xFF2A2000),
    onPrimaryContainer  = PlexOrange,
    secondary           = PlexSurface3,
    onSecondary         = PlexTextPrimary,
    background          = PlexBackground,
    onBackground        = PlexTextPrimary,
    surface             = PlexSurface,
    onSurface           = PlexTextPrimary,
    surfaceVariant      = PlexSurface2,
    onSurfaceVariant    = PlexTextSecondary,
    outline             = PlexBorder,
    outlineVariant      = PlexBorderLight,
    error               = ErrorRed
)

@Composable
fun PlexValetTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PlexBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
