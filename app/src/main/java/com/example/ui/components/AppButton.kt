package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.UniTrackTheme

enum class AppButtonVariant {
    PRIMARY,   // Cyan background with dark text
    OUTLINED,  // Sand border with sand text
    GHOST      // Transparent background with cyan/secondary text
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    enabled: Boolean = true,
    testTag: String = "app_button_${text.lowercase().replace(" ", "_")}"
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    when (variant) {
        AppButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(dimens.BoxRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCyan,
                    contentColor = DarkBackground, // Deep ink for strong contrast on cyan
                    disabledContainerColor = colors.accentCyan.copy(alpha = 0.3f),
                    disabledContentColor = DarkBackground.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = dimens.Space16, vertical = dimens.Space12),
                modifier = modifier
                    .defaultMinSize(minHeight = dimens.MinTouchTarget)
                    .testTag(testTag)
            ) {
                Text(
                    text = text,
                    style = typography.body,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        AppButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(dimens.BoxRadius),
                border = BorderStroke(dimens.HairlineBorder, colors.sand),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.sand
                ),
                contentPadding = PaddingValues(horizontal = dimens.Space16, vertical = dimens.Space12),
                modifier = modifier
                    .defaultMinSize(minHeight = dimens.MinTouchTarget)
                    .testTag(testTag)
            ) {
                Text(
                    text = text,
                    style = typography.body,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        AppButtonVariant.GHOST -> {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(dimens.BoxRadius),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.accentCyan
                ),
                contentPadding = PaddingValues(horizontal = dimens.Space12, vertical = dimens.Space8),
                modifier = modifier
                    .defaultMinSize(minHeight = dimens.MinTouchTarget)
                    .testTag(testTag)
            ) {
                Text(
                    text = text,
                    style = typography.body,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
