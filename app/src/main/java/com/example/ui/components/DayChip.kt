package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.UniTrackTheme

/**
 * DayChip for 7-day horizontal calendar strip:
 * - Weekday abbreviation ("MON", "TUE", etc.)
 * - Date number ("23", "28", etc.)
 * - Dot indicators for classes on that day (up to 3 dots)
 * - Selected day gets cyan glow border and cyan text
 * - Today has a tiny cyan indicator dot
 */
@Composable
fun DayChip(
    dayAbbr: String,
    dateNumber: String,
    classCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val shape = RoundedCornerShape(dimens.BoxRadius)
    val borderColor = if (isSelected) colors.accentCyan else colors.cardBorder

    Box(
        modifier = modifier
            .width(52.dp)
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CyanGlow, Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width
                            )
                        )
                    }
                } else Modifier
            )
            .clip(shape)
            .background(
                if (isSelected) {
                    colors.accentCyan.copy(alpha = 0.12f)
                } else {
                    colors.cardFill
                }
            )
            .border(
                width = dimens.HairlineBorder,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = dimens.Space8, horizontal = dimens.Space4)
            .testTag("day_chip_${dayAbbr.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weekday Abbreviation
            Text(
                text = dayAbbr.uppercase(),
                style = typography.labelBadge,
                color = if (isSelected) colors.accentCyan else colors.textSecondary,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Date Number
            Text(
                text = dateNumber,
                style = typography.sectionTitle,
                color = if (isSelected) colors.accentCyan else colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom indicators: Class dots or today dot
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(6.dp)
            ) {
                if (isToday) {
                    // Today marker
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accentCyan else colors.sand)
                    )
                } else if (classCount > 0) {
                    val dotsToShow = classCount.coerceAtMost(3)
                    repeat(dotsToShow) {
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.accentCyan.copy(alpha = 0.8f) else colors.textSecondary.copy(alpha = 0.6f)
                                )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}
