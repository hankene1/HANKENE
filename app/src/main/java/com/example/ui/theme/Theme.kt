package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanBiometric,
    onPrimary = NavyPrimary,
    primaryContainer = Color(0xFF003554),
    onPrimaryContainer = Color(0xFFCAF0F8),
    secondary = EmeraldVerified,
    onSecondary = NavyPrimary,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = AmberWarning,
    background = NavyPrimary,
    onBackground = Color(0xFFE2E8F0),
    surface = NavySurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = CrimsonAlert
)

private val LightColorScheme = lightColorScheme(
    primary = CyanBiometricDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3DC),
    onPrimaryContainer = Color(0xFF001D3D),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    background = LightBackground,
    onBackground = DarkTextPrimary,
    surface = LightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = Color(0xFFDC2626)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
