package com.uth.memora.feature.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = SurfaceLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = PrimaryBlue,
    onSecondary = SurfaceLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnPrimaryContainerLight,
    tertiary = PrimaryBlueLight,
    onTertiary = SurfaceLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = BorderLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = StatusError,
    onError = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = KickerBlue,
    secondary = KickerBlue,
    onSecondary = BackgroundDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = KickerBlue,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = StatusError,
    onError = TextPrimaryDark
)

@Composable
fun MemoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MemoraTypography,
        content = content
    )
}
