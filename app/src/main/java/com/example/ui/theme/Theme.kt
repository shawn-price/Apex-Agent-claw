package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OpenClawColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = EmeraldLight,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = ObsidianCardElevated,
    onSecondaryContainer = CyanGlow,
    tertiary = AmberGold,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberGoldLight,
    background = ObsidianDark,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = SlateTextSecondary,
    outline = ObsidianBorder,
    error = RubyRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Use our signature cyber dark theme by default
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OpenClawColorScheme,
        typography = Typography,
        content = content
    )
}
