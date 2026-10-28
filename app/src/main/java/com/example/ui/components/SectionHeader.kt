package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.UniTrackTheme

/**
 * Editorial section header:
 * - Sand uppercase label (11sp medium, 0.6sp tracking)
 * - Optional action text button (e.g. "Full Timetable", "See all")
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.Space8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = typography.sectionHeaderLabel,
            color = colors.sand,
            modifier = Modifier.testTag("section_header_${title.lowercase().replace(" ", "_")}")
        )

        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Text(
                text = actionText,
                style = typography.meta,
                color = colors.accentCyan,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = dimens.Space4, vertical = dimens.Space4)
                    .testTag("section_action_${actionText.lowercase().replace(" ", "_")}")
            )
        }
    }
}
