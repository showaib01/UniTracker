package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.UniTrackTheme

/**
 * High-performance glassmorphic background:
 * Dark base + 2 fixed faint radial color blobs (cyan at top-left and info-blue at center-right, ~6% alpha)
 * Zero real runtime blur on content to guarantee 60fps scrolling.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = UniTrackTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                val width = size.width
                val height = size.height

                // Radial blob 1 (Cyan glow top-left)
                val blob1Radius = width * 0.75f
                val blob1Center = Offset(width * 0.15f, height * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accentCyan.copy(alpha = if (colors.isDark) 0.08f else 0.04f),
                            Color.Transparent
                        ),
                        center = blob1Center,
                        radius = blob1Radius
                    ),
                    center = blob1Center,
                    radius = blob1Radius
                )

                // Radial blob 2 (Info-blue glow mid-right)
                val blob2Radius = width * 0.85f
                val blob2Center = Offset(width * 0.90f, height * 0.45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.infoBlue.copy(alpha = if (colors.isDark) 0.06f else 0.03f),
                            Color.Transparent
                        ),
                        center = blob2Center,
                        radius = blob2Radius
                    ),
                    center = blob2Center,
                    radius = blob2Radius
                )
            }
    ) {
        content()
    }
}
