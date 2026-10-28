package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing and dimension tokens based on the 4dp grid:
 * - Screen horizontal padding: 20dp
 * - Card corner radius: 16dp
 * - Small box corner radius: 12dp
 * - Pill corner radius: 999dp
 * - Vertical rhythm: 12dp / 20dp / 32dp
 * - Minimum touch target: 48dp
 * - Hairline border: 1dp
 */
object UniTrackDimens {
    val ScreenPadding = 20.dp
    val CardRadius = 16.dp
    val RadiusCard = 16.dp
    val BoxRadius = 12.dp
    val RadiusBox = 12.dp
    val PillRadius = 999.dp
    val ChipRadius = 999.dp
    val HairlineBorder = 1.dp
    val MinTouchTarget = 48.dp

    // Card internal padding
    val CardPadding = 16.dp
    val CardInnerSpacing = 12.dp

    // Vertical rhythm tokens
    val RhythmSmall = 12.dp
    val RhythmMedium = 20.dp
    val RhythmLarge = 32.dp

    // 4dp grid spacing
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space28 = 28.dp
    val Space32 = 32.dp

    // Highlight marker
    val HighlightMarkerWidth = 3.dp

    // Glow spread
    val GlowRadius = 8.dp
}
