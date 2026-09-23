package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    DARK,
    LIGHT,
    COZY
}

data class ProtocolPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val faintForeground: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentForeground: Color,
    val success: Color,
    val danger: Color,
    val shadow: Color
)

val DarkPalette = ProtocolPalette(
    background = Color(0xFF0B0E0D),
    surface = Color(0xFF121715),
    surfaceRaised = Color(0xFF18201D),
    foreground = Color(0xFFF3F6F0),
    mutedForeground = Color(0xFF9AA8A0),
    faintForeground = Color(0xFF627067),
    border = Color(0xFF27332E),
    accent = Color(0xFFC6F36B),
    accentSoft = Color(0xFF26351D),
    accentForeground = Color(0xFF10150E),
    success = Color(0xFFC6F36B),
    danger = Color(0xFFFF8D7A),
    shadow = Color(0xFF000000)
)

val LightPalette = ProtocolPalette(
    background = Color(0xFFF7F9F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF0F4ED),
    foreground = Color(0xFF142019),
    mutedForeground = Color(0xFF66766B),
    faintForeground = Color(0xFF9AA89E),
    border = Color(0xFFDCE6DD),
    accent = Color(0xFF739D2F),
    accentSoft = Color(0xFFE5F0D1),
    accentForeground = Color(0xFFFFFFFF),
    success = Color(0xFF739D2F),
    danger = Color(0xFFBD5547),
    shadow = Color(0xFF819182)
)

val CozyPalette = ProtocolPalette(
    background = Color(0xFFF4EEE3),
    surface = Color(0xFFFBF8F1),
    surfaceRaised = Color(0xFFEEE4D4),
    foreground = Color(0xFF3A3026),
    mutedForeground = Color(0xFF897968),
    faintForeground = Color(0xFFB1A18E),
    border = Color(0xFFE2D5C3),
    accent = Color(0xFFB8744A),
    accentSoft = Color(0xFFF1DED0),
    accentForeground = Color(0xFFFFF4EB),
    success = Color(0xFF7C9561),
    danger = Color(0xFFB96154),
    shadow = Color(0xFFAA967C)
)
