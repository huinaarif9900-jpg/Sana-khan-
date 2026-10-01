package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val SanaPrimary = Color(0xFF7052FF)
val SanaPrimaryVariant = Color(0xFF8A7BFF)
val SanaSecondary = Color(0xFFFF7BB0)
val SanaTertiary = Color(0xFF38E8C6)
val SanaBackground = Color(0xFF0D0B18)
val SanaSurface = Color(0xFF171427)
val SanaSurfaceVariant = Color(0xFF1E1B33)
val SanaError = Color(0xFFFF5252)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = SanaPrimary,
    secondary = SanaSecondary,
    tertiary = SanaTertiary,
    background = SanaBackground,
    surface = SanaSurface,
    surfaceVariant = SanaSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White,
    error = SanaError
)
