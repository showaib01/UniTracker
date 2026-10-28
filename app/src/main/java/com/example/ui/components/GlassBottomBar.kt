package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.UniTrackTheme

data class BottomNavItem(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val labelText: String
)

/**
 * GlassBottomBar:
 * Translucent bottom bar with 4 items: Home, Routine, Tasks, Settings
 * Active item gets cyan icon + label + glow indicator dot
 */
@Composable
fun GlassBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val items = listOf(
        Screen.Today to "Home",
        Screen.Routine to "Routine",
        Screen.Tasks to "Tasks",
        Screen.More to "Settings"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.Space16, vertical = dimens.Space8)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardFill)
                .drawBehind {
                    // Subtle top-to-bottom white gradient overlay
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (colors.isDark) 0.07f else 0.04f),
                                Color.Transparent
                            )
                        )
                    )
                }
                .border(
                    width = dimens.HairlineBorder,
                    color = colors.cardBorder,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(vertical = dimens.Space8, horizontal = dimens.Space4),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, label) ->
                val selected = currentRoute == screen.route
                val iconColor by animateColorAsState(
                    targetValue = if (selected) colors.accentCyan else colors.textSecondary,
                    label = "nav_icon_color"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(screen.route) }
                        )
                        .padding(vertical = dimens.Space4)
                        .testTag("nav_item_${screen.route}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (selected) {
                                    Modifier.drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(CyanGlow, Color.Transparent),
                                                center = Offset(size.width / 2f, size.height / 2f),
                                                radius = size.width / 1.1f
                                            )
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        screen.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = label,
                        style = typography.labelBadge,
                        color = iconColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Glow dot indicator for active tab
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(colors.accentCyan)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
            }
        }
    }
}
