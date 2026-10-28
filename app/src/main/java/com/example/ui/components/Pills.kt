package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UniTrackTheme

/**
 * Universal Pill system for Time, Room, Status, Type, and Section
 */

@Composable
fun TimePill(
    timeRangeText: String,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    Text(
        text = timeRangeText,
        style = typography.timeText,
        color = colors.textPrimary,
        modifier = modifier
            .background(
                color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .padding(horizontal = dimens.Space8, vertical = dimens.Space4)
    )
}

@Composable
fun RoomPill(
    roomText: String,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    Text(
        text = if (roomText.startsWith("Room", ignoreCase = true)) roomText else "Room $roomText",
        style = typography.meta,
        color = colors.sand,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(
                color = colors.sand.copy(alpha = 0.12f),
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .padding(horizontal = dimens.Space8, vertical = dimens.Space4)
    )
}

enum class ClassStatus {
    UPCOMING,
    LIVE_NOW,
    DONE
}

@Composable
fun StatusPill(
    status: ClassStatus,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val (bgColor, textColor, label) = when (status) {
        ClassStatus.LIVE_NOW -> Triple(
            colors.accentCyan.copy(alpha = 0.15f),
            colors.accentCyan,
            "LIVE NOW"
        )
        ClassStatus.UPCOMING -> Triple(
            colors.infoBlue.copy(alpha = 0.15f),
            colors.infoBlue,
            "UPCOMING"
        )
        ClassStatus.DONE -> Triple(
            colors.success.copy(alpha = 0.12f),
            colors.success,
            "DONE"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .then(
                if (status == ClassStatus.LIVE_NOW) {
                    Modifier.border(
                        width = dimens.HairlineBorder,
                        color = colors.accentCyan.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(dimens.PillRadius)
                    )
                } else Modifier
            )
            .padding(horizontal = dimens.Space8, vertical = dimens.Space4)
    ) {
        if (status == ClassStatus.LIVE_NOW) {
            Box(
                modifier = Modifier
                    .padding(end = dimens.Space4)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.accentCyan)
            )
        }
        Text(
            text = label,
            style = typography.labelBadge,
            color = textColor
        )
    }
}

@Composable
fun SectionPill(
    section: String,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val display = if (section.startsWith("[")) section else "[$section]"

    Text(
        text = display,
        style = typography.meta,
        color = colors.textSecondary,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(
                color = if (colors.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .border(
                width = dimens.HairlineBorder,
                color = colors.cardBorder,
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .padding(horizontal = dimens.Space8, vertical = dimens.Space4)
    )
}

@Composable
fun CourseItemTypePill(
    type: String,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val (bgColor, textColor) = when (type.uppercase()) {
        "QUIZ" -> colors.accentCyan.copy(alpha = 0.15f) to colors.accentCyan
        "ASSIGNMENT" -> colors.infoBlue.copy(alpha = 0.15f) to colors.infoBlue
        "EXAM_MID", "EXAM_FINAL" -> colors.sand.copy(alpha = 0.18f) to colors.sand
        "PROJECT" -> colors.warning.copy(alpha = 0.15f) to colors.warning
        "PRESENTATION" -> colors.accentCyan.copy(alpha = 0.12f) to colors.accentCyan
        "LAB_REPORT" -> colors.infoBlue.copy(alpha = 0.12f) to colors.infoBlue
        else -> colors.textSecondary.copy(alpha = 0.12f) to colors.textPrimary
    }

    Text(
        text = com.example.data.local.entity.CourseItemType.displayName(type),
        style = typography.labelBadge,
        color = textColor,
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(dimens.PillRadius)
            )
            .padding(horizontal = dimens.Space8, vertical = dimens.Space4)
    )
}
