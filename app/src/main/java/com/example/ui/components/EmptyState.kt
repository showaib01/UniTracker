package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UniTrackTheme

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.EventBusy,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_state_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.Space20, horizontal = dimens.Space12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(dimens.Space12))

            Text(
                text = title,
                style = typography.sectionTitle,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(dimens.Space4))

            Text(
                text = message,
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            if (!actionButtonText.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(dimens.Space16))
                AppButton(
                    text = actionButtonText,
                    onClick = onActionClick,
                    variant = AppButtonVariant.PRIMARY
                )
            }
        }
    }
}
