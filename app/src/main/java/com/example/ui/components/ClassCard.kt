package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClassRoutineEntity
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

/**
 * ClassCard from the design reference screenshot:
 * - Top row: Time range pill + Duration/Type ("1h 30m · Theory/Lab") + Status Tag (UPCOMING / LIVE NOW / DONE)
 * - Middle: Course Name (20sp bold) + Section pill + Room pill
 * - Bottom row: "+ Add Task" button + Faculty avatar/name (if available)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassCard(
    routine: ClassRoutineEntity,
    status: ClassStatus,
    onCardClick: () -> Unit,
    onAddTaskClick: () -> Unit,
    facultyName: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val isLive = status == ClassStatus.LIVE_NOW
    val timeRange12h = DateTimeUtils.formatTimeRange12h(routine.startTime, routine.endTime)

    // Calculate duration text
    val isLab = routine.courseName.contains("LAB", ignoreCase = true)
    val durationType = "${calculateDuration(routine.startTime, routine.endTime)} · ${if (isLab) "Lab" else "Theory"}"

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("class_card_${routine.id}"),
        glow = isLive,
        onClick = onCardClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: Time Range, Duration/Type, and Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    TimePill(timeRangeText = timeRange12h)

                    Text(
                        text = durationType,
                        style = typography.meta,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                StatusPill(status = status)
            }

            Spacer(modifier = Modifier.height(dimens.Space12))

            // Middle: Course Name
            Text(
                text = routine.courseName,
                style = typography.heroCourseTitle,
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(dimens.Space8))

            // Section & Room Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                verticalArrangement = Arrangement.spacedBy(dimens.Space8),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (routine.section.isNotBlank()) {
                    SectionPill(section = routine.section)
                }
                if (routine.room.isNotBlank()) {
                    RoomPill(roomText = routine.room)
                }
            }

            Spacer(modifier = Modifier.height(dimens.Space12))

            // Bottom Row: "+ Add Task" button and Faculty Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+ Add Task" pill action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimens.PillRadius))
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                        )
                        .clickable(onClick = onAddTaskClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("class_card_add_task_${routine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Task",
                        tint = colors.accentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.Space4))
                    Text(
                        text = "Add Task",
                        style = typography.labelBadge,
                        color = colors.accentCyan,
                        fontSize = 11.sp
                    )
                }

                // Faculty information (if available)
                if (!facultyName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space4)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.sand.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = facultyName.take(1).uppercase(),
                                style = typography.labelBadge,
                                color = colors.sand,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = facultyName,
                            style = typography.meta,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun calculateDuration(startTime: String, endTime: String): String {
    return try {
        val sMinutes = parseTimeToMinutes(startTime)
        val eMinutes = parseTimeToMinutes(endTime)
        val diff = (eMinutes - sMinutes).coerceAtLeast(0)
        val hours = diff / 60
        val mins = diff % 60
        when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    } catch (e: Exception) {
        "1h 30m"
    }
}

private fun parseTimeToMinutes(timeStr: String): Int {
    val cleaned = timeStr.trim().uppercase()
    val isPm = cleaned.contains("PM")
    val isAm = cleaned.contains("AM")
    val digits = cleaned.replace("AM", "").replace("PM", "").trim()
    val parts = digits.split(":")
    var hour = parts[0].toIntOrNull() ?: 8
    val min = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0

    if (isPm && hour < 12) hour += 12
    if (isAm && hour == 12) hour = 0

    return hour * 60 + min
}
