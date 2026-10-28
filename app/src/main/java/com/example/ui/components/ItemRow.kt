package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

/**
 * Compact ItemRow for quiz/assignment/exam items:
 * - Type pill (QUIZ, ASSIGNMENT, etc.)
 * - Title and course info
 * - Due time badge
 * - Circular checkmark toggle
 */
@Composable
fun ItemRow(
    item: CourseItemEntity,
    onToggleDone: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val isDone = item.status == CourseItemStatus.DONE
    val dueInfo = DateTimeUtils.formatDueBadgeText(item.dueAt, item.hasTime)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.BoxRadius))
            .background(colors.cardFill)
            .border(
                width = dimens.HairlineBorder,
                color = colors.cardBorder,
                shape = RoundedCornerShape(dimens.BoxRadius)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.Space12, vertical = dimens.Space12)
            .testTag("item_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular checkmark toggle button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isDone) colors.success.copy(alpha = 0.2f) else colors.cardFill
                )
                .border(
                    width = dimens.HairlineBorder,
                    color = if (isDone) colors.success else colors.cardBorder,
                    shape = CircleShape
                )
                .clickable { onToggleDone(!isDone) }
                .testTag("item_toggle_${item.id}"),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = colors.success,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(dimens.Space12))

        // Details Column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
            ) {
                CourseItemTypePill(type = item.type)

                Text(
                    text = item.courseName,
                    style = typography.meta,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Text(
                text = item.title,
                style = typography.body,
                color = if (isDone) colors.textSecondary else colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Due text
            if (dueInfo.formattedDateTime.isNotBlank()) {
                val dueColor = when (dueInfo.urgency) {
                    "OVERDUE" -> colors.danger
                    "URGENT" -> colors.warning
                    else -> colors.textSecondary
                }
                Text(
                    text = "Due ${dueInfo.formattedDateTime} (${dueInfo.label})",
                    style = typography.meta,
                    color = dueColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
