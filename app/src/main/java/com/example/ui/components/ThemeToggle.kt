package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UniTrackTheme

@Composable
fun ThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val dimens = UniTrackTheme.dimens

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            )
            .border(
                width = dimens.HairlineBorder,
                color = colors.cardBorder,
                shape = CircleShape
            )
            .clickable(onClick = onToggle)
            .testTag("theme_toggle_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = if (isDark) "Switch to Light Theme" else "Switch to Dark Theme",
            tint = if (isDark) colors.sand else colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
