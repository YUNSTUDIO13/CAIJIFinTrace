package com.example.zengchubao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Gray50,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue900,
    secondary = Blue100,
    onSecondary = Blue800,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue700,
    tertiary = Emerald500,
    onTertiary = Gray50,
    tertiaryContainer = Emerald50,
    onTertiaryContainer = Color(0xFF064E3B),
    error = Red500,
    onError = Gray50,
    errorContainer = Red50,
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF0F4F8),
    onBackground = Gray900,
    surface = Gray50,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,
    outline = Gray300,
    outlineVariant = Gray200,
    inverseSurface = Gray800,
    inverseOnSurface = Gray50
)

@Composable
fun ZengChuBaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = ZengChuBaoTypography,
        content = content
    )
}
