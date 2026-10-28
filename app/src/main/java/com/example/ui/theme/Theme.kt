package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccentCyan,
    onPrimary = DarkBackground,
    primaryContainer = DarkCardFill,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkSand,
    onSecondary = DarkBackground,
    secondaryContainer = DarkCardFill,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkInfoBlue,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardFill,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCardFill,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder,
    outlineVariant = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccentCyan,
    onPrimary = LightBackground,
    primaryContainer = LightCardFill,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightSand,
    onSecondary = LightBackground,
    secondaryContainer = LightCardFill,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightInfoBlue,
    onTertiary = LightBackground,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightCardFill,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardFill,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    outlineVariant = LightCardBorder
)

val DarkUniTrackColors = UniTrackColors(
    background = DarkBackground,
    cardFill = DarkCardFill,
    cardBorder = DarkCardBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    accentCyan = DarkAccentCyan,
    sand = DarkSand,
    infoBlue = DarkInfoBlue,
    success = DarkSuccess,
    warning = DarkWarning,
    danger = DarkDanger,
    surfaceSheet = DarkSurfaceSheet,
    surfaceField = DarkSurfaceField,
    scrim = DarkScrim,
    isDark = true
)

val LightUniTrackColors = UniTrackColors(
    background = LightBackground,
    cardFill = LightCardFill,
    cardBorder = LightCardBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    accentCyan = LightAccentCyan,
    sand = LightSand,
    infoBlue = LightInfoBlue,
    success = LightSuccess,
    warning = LightWarning,
    danger = LightDanger,
    surfaceSheet = LightSurfaceSheet,
    surfaceField = LightSurfaceField,
    scrim = LightScrim,
    isDark = false
)

object UniTrackTheme {
    val colors: UniTrackColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUniTrackColors.current

    val typography: UniTrackTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalUniTrackTypography.current

    val dimens = UniTrackDimens
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Dark by default as required
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) DarkUniTrackColors else LightUniTrackColors
    val customTypography = UniTrackTypography()

    CompositionLocalProvider(
        LocalUniTrackColors provides customColors,
        LocalUniTrackTypography provides customTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
