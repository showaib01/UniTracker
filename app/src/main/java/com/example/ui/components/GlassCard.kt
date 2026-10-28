package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.UniTrackTheme

/**
 * Universal GlassCard:
 * - Translucent card fill (rgba 23,27,38,0.75)
 * - 1dp hairline border (white 8% in dark, black 8% in light)
 * - Subtle top-to-bottom white gradient overlay (6% -> 0%)
 * - Optional active glow: 1dp cyan border + subtle cyan drawBehind radial glow
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    shape: Shape = RoundedCornerShape(UniTrackTheme.dimens.CardRadius),
    contentPadding: Dp = UniTrackTheme.dimens.CardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = UniTrackTheme.colors
    val dimens = UniTrackTheme.dimens

    val borderColor = if (glow) colors.accentCyan else colors.cardBorder
    val borderWidth = dimens.HairlineBorder

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .then(
                if (glow) {
                    Modifier.drawBehind {
                        // Subtle 8dp radial glow around active card
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(CyanGlow, Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension / 1.3f
                            ),
                            size = Size(size.width, size.height)
                        )
                    }
                } else Modifier
            )
            .clip(shape)
            .background(colors.cardFill)
            .drawBehind {
                // Subtle top-to-bottom highlight gradient (6% -> 0% white)
                val highlightAlpha = if (colors.isDark) 0.06f else 0.03f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = highlightAlpha),
                            Color.Transparent
                        )
                    )
                )
            }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape
            )
            .then(clickableModifier)
            .padding(contentPadding)
    ) {
        content()
    }
}
