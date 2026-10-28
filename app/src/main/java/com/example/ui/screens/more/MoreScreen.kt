package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.UniTrackTheme

data class ShortcutItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val route: String,
    val isPrimary: Boolean = false
)

@Composable
fun MoreScreen(
    onNavigateToPortal: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToNotices: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebugLogs: () -> Unit = {},
    onNavigateToDesignSystem: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val shortcutItems = listOf(
        ShortcutItem(
            title = "AIUB Student Portal",
            subtitle = "Log in, browse routine, attendance and solve CAPTCHA",
            icon = Icons.Outlined.School,
            iconColor = colors.accentCyan,
            route = "portal",
            isPrimary = true
        ),
        ShortcutItem(
            title = "Design System Preview",
            subtitle = "Inspect glassmorphic tokens, cards, pills & buttons",
            icon = Icons.Outlined.Code,
            iconColor = colors.accentCyan,
            route = "design_system",
            isPrimary = true
        ),
        ShortcutItem(
            title = "Page Capture & HTML Samples",
            subtitle = "Saved page snapshots for inspecting portal structure",
            icon = Icons.Outlined.CameraAlt,
            iconColor = colors.sand,
            route = "capture"
        ),
        ShortcutItem(
            title = "Parser Debugger",
            subtitle = "Test CSS selectors against live captured HTML",
            icon = Icons.Outlined.BugReport,
            iconColor = colors.infoBlue,
            route = "debug"
        ),
        ShortcutItem(
            title = "Notices & Announcements",
            subtitle = "Cached university notices for offline reading",
            icon = Icons.Outlined.Notifications,
            iconColor = colors.warning,
            route = "notices"
        ),
        ShortcutItem(
            title = "Settings",
            subtitle = "Portal URL, warning thresholds, language and data cache",
            icon = Icons.Outlined.Settings,
            iconColor = colors.sand,
            route = "settings"
        ),
        ShortcutItem(
            title = "UniTrack Debug Log",
            subtitle = "Live capture events, JS extraction & background sync logs",
            icon = Icons.Outlined.BugReport,
            iconColor = colors.danger,
            route = "debug_logs"
        )
    )

    AppBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.ScreenPadding)
                .testTag("more_screen"),
            contentPadding = PaddingValues(top = dimens.Space8, bottom = dimens.Space32),
            verticalArrangement = Arrangement.spacedBy(dimens.Space12)
        ) {
            item {
                SectionHeader(title = "SHORTCUTS & TOOLS")
            }

            items(shortcutItems.size, key = { shortcutItems[it].route }) { index ->
                val item = shortcutItems[index]
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_${item.route}"),
                    contentPadding = dimens.Space16,
                    glow = item.isPrimary,
                    onClick = {
                        when (item.route) {
                            "portal" -> onNavigateToPortal()
                            "capture" -> onNavigateToCapture()
                            "debug" -> onNavigateToDebug()
                            "notices" -> onNavigateToNotices()
                            "settings" -> onNavigateToSettings()
                            "debug_logs" -> onNavigateToDebugLogs()
                            "design_system" -> onNavigateToDesignSystem()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                .background(item.iconColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(dimens.Space16))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = typography.body,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                style = typography.meta,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
