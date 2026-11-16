package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.delay

/**
 * AppTopBar from design reference:
 * - Outer container background extends behind status bar.
 * - Content starts below status bar via windowInsetsPadding(WindowInsets.statusBars).
 * - Left: two-line title (small secondary subtitle, large screen title) without overlap.
 * - Right:
 *     - Glass live clock pill ("Wednesday | 12:19 PM" ticking)
 *     - Bell icon with overdue/due-today badge
 *     - Moon/sun theme toggle
 *     - Vertically centered with 8dp spacing.
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: String = "UniTracker",
    subtitle: String = "Academic Routine",
    badgeCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    isDark: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    var liveClockText by remember { mutableStateOf(DateTimeUtils.formatLiveClock()) }

    LaunchedEffect(Unit) {
        while (true) {
            liveClockText = DateTimeUtils.formatLiveClock()
            delay(30_000L) // Refresh every 30 seconds
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Two-line Title (Subtitle above Title, single line, no overlap)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("app_top_bar_title_column")
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = typography.meta,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = title,
                    style = typography.screenTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(dimens.Space8))

            // Right Action Items (Clock pill, Bell, Theme toggle on same row with 8dp spacing)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                modifier = Modifier.testTag("app_top_bar_actions")
            ) {
                // Live clock pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimens.PillRadius))
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)
                        )
                        .border(
                            width = dimens.HairlineBorder,
                            color = colors.cardBorder,
                            shape = RoundedCornerShape(dimens.PillRadius)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("live_clock_pill")
                ) {
                    Text(
                        text = liveClockText,
                        style = typography.timeText,
                        color = colors.textPrimary,
                        fontSize = 11.sp
                    )
                }

                // Bell icon with badge
                BadgedIcon(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = "Notifications & Reminders",
                    badgeCount = badgeCount,
                    onClick = onNotificationsClick,
                    testTag = "top_bar_bell_icon"
                )

                // Theme toggle button
                ThemeToggle(
                    isDark = isDark,
                    onToggle = onThemeToggle
                )
            }
        }
    }
}
