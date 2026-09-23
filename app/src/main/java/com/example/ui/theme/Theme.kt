package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalProtocolPalette = staticCompositionLocalOf { DarkPalette }

object ProtocolTheme {
    val palette: ProtocolPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalProtocolPalette.current
}

@Composable
fun ProtocolAppTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val palette = when (mode) {
        ThemeMode.DARK -> DarkPalette
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.COZY -> CozyPalette
    }

    val materialColorScheme = if (mode == ThemeMode.DARK) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentForeground,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.mutedForeground,
            outline = palette.border
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentForeground,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.mutedForeground,
            outline = palette.border
        )
    }

    CompositionLocalProvider(LocalProtocolPalette provides palette) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
