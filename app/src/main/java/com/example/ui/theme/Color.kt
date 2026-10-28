package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==========================================
// DARK THEME TOKENS (Default)
// ==========================================
val DarkBackground = Color(0xFF0F1117)
val DarkCardFill = Color(0xBF171B26)     // rgba(23, 27, 38, 0.75)
val DarkCardBorder = Color(0x14FFFFFF)   // white 8%
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFA0AEC0)
val DarkAccentCyan = Color(0xFF00E5FF)   // active/selected/live only
val DarkSand = Color(0xFFC9A882)         // section labels, badges, room pills
val DarkInfoBlue = Color(0xFF6EA8FF)     // UPCOMING tag
val DarkSuccess = Color(0xFF34D399)
val DarkWarning = Color(0xFFF5B14C)
val DarkDanger = Color(0xFFE5484D)

// Opaque surface tokens for modals, sheets, dialogs, and fields
val DarkSurfaceSheet = Color(0xFF151922) // Opaque modal sheet background
val DarkSurfaceField = Color(0xFF1C212C) // Input and unselected chip background
val DarkScrim = Color(0x99000000)        // Scrim black 60%

// ==========================================
// LIGHT THEME TOKENS (Derived for toggle)
// ==========================================
val LightBackground = Color(0xFFF4F6FA)
val LightCardFill = Color(0xCCFFFFFF)    // white 80%
val LightCardBorder = Color(0x14000000)  // black 8%
val LightTextPrimary = Color(0xFF0F1117)
val LightTextSecondary = Color(0xFF5B6472)
val LightAccentCyan = Color(0xFF0097A7)
val LightSand = Color(0xFF8A6A3B)
val LightInfoBlue = Color(0xFF2563EB)
val LightSuccess = Color(0xFF059669)
val LightWarning = Color(0xFFD97706)
val LightDanger = Color(0xFFDC2626)

// Opaque surface tokens for modals, sheets, dialogs, and fields
val LightSurfaceSheet = Color(0xFFFFFFFF) // Opaque modal sheet background
val LightSurfaceField = Color(0xFFF1F4F9) // Input and unselected chip background
val LightScrim = Color(0x52000000)        // Scrim black 32%

// Active glow colors (subtle radial glow behind selected elements)
val CyanGlow = Color(0x2E00E5FF) // cyan at 18% alpha

// Legacy brand aliases for backward compatibility
val AiubNavy = Color(0xFF14305C)
val AiubGold = Color(0xFFC9A227)

/**
 * Single source of truth semantic color tokens accessible via UniTrackTheme.colors
 */
@Immutable
data class UniTrackColors(
    val background: Color,
    val cardFill: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentCyan: Color,
    val sand: Color,
    val infoBlue: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val surfaceSheet: Color,
    val surfaceField: Color,
    val scrim: Color,
    val isDark: Boolean,

    // Aliases to seamlessly support remaining screens without compilation issues
    val surface: Color = cardFill,
    val ink: Color = textPrimary,
    val secondary: Color = textSecondary,
    val hairline: Color = cardBorder,
    val accentNavy: Color = accentCyan,
    val highlightGold: Color = sand,
    val green: Color = success,
    val amber: Color = warning,
    val red: Color = danger,
    val statusSuccess: Color = success,
    val statusWarning: Color = warning,
    val statusDanger: Color = danger
)

val LocalUniTrackColors = staticCompositionLocalOf {
    UniTrackColors(
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
}
