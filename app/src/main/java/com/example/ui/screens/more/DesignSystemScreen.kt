package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseItemType
import com.example.ui.components.AppBackground
import com.example.ui.components.AppButton
import com.example.ui.components.AppButtonVariant
import com.example.ui.components.ClassCard
import com.example.ui.components.ClassStatus
import com.example.ui.components.CourseItemTypePill
import com.example.ui.components.DayChip
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.ItemRow
import com.example.ui.components.RoomPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.SectionPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TimePill
import com.example.ui.theme.UniTrackTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesignSystemScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    var sampleItemDone by remember { mutableStateOf(false) }

    AppBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "DESIGN SYSTEM",
                                style = typography.labelBadge,
                                color = colors.sand
                            )
                            Text(
                                text = "Glassmorphic Academic",
                                style = typography.screenTitle,
                                color = colors.textPrimary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("design_system_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = dimens.ScreenPadding),
                contentPadding = PaddingValues(top = dimens.Space8, bottom = dimens.Space32),
                verticalArrangement = Arrangement.spacedBy(dimens.RhythmMedium)
            ) {
                // Section 1: Color Tokens
                item {
                    SectionHeader(title = "COLOR TOKENS (SINGLE SOURCE OF TRUTH)")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.Space12)) {
                            ColorTokenRow(name = "Background", hex = "#0F1117", color = colors.background)
                            ColorTokenRow(name = "Card Fill (75% alpha)", hex = "rgba(23,27,38,0.75)", color = colors.cardFill)
                            ColorTokenRow(name = "Card Border (8% white)", hex = "rgba(255,255,255,0.08)", color = colors.cardBorder)
                            ColorTokenRow(name = "Accent Cyan (Active/Live)", hex = "#00E5FF", color = colors.accentCyan)
                            ColorTokenRow(name = "Sand (Labels/Badges)", hex = "#C9A882", color = colors.sand)
                            ColorTokenRow(name = "Info Blue (Upcoming)", hex = "#6EA8FF", color = colors.infoBlue)
                            ColorTokenRow(name = "Success Green", hex = "#34D399", color = colors.success)
                            ColorTokenRow(name = "Warning Amber", hex = "#F5B14C", color = colors.warning)
                            ColorTokenRow(name = "Danger Red", hex = "#E5484D", color = colors.danger)
                        }
                    }
                }

                // Section 2: Typography Scale (Inter Bundled)
                item {
                    SectionHeader(title = "TYPOGRAPHY SCALE (INTER)")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                            Text(text = "Screen Title (22sp SemiBold)", style = typography.screenTitle, color = colors.textPrimary)
                            Text(text = "Hero Course Title (20sp Bold)", style = typography.heroCourseTitle, color = colors.textPrimary)
                            Text(text = "Section Title (18sp SemiBold)", style = typography.sectionTitle, color = colors.textPrimary)
                            Text(text = "Body Text (14sp Regular)", style = typography.body, color = colors.textPrimary)
                            Text(text = "Meta Information (13sp Regular)", style = typography.meta, color = colors.textSecondary)
                            Text(text = "SECTION BADGE (11sp Medium Uppercase 0.6sp)", style = typography.labelBadge, color = colors.sand)
                            Text(text = "Time Tabular: 08:00 – 10:20 AM (13sp Medium tnum)", style = typography.timeText, color = colors.textPrimary)
                        }
                    }
                }

                // Section 3: Pills System
                item {
                    SectionHeader(title = "PILL SYSTEM")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.Space12)) {
                            Text(text = "Time & Room Pills", style = typography.meta, color = colors.textSecondary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(dimens.Space8)
                            ) {
                                TimePill(timeRangeText = "08:00 – 09:30 AM")
                                TimePill(timeRangeText = "10:20 AM – 12:20 PM")
                                RoomPill(roomText = "Room 3102")
                                RoomPill(roomText = "DS0302")
                                SectionPill(section = "A")
                                SectionPill(section = "V")
                            }

                            Spacer(modifier = Modifier.height(dimens.Space4))

                            Text(text = "Status Pills", style = typography.meta, color = colors.textSecondary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(dimens.Space8)
                            ) {
                                StatusPill(status = ClassStatus.LIVE_NOW)
                                StatusPill(status = ClassStatus.UPCOMING)
                                StatusPill(status = ClassStatus.DONE)
                            }

                            Spacer(modifier = Modifier.height(dimens.Space4))

                            Text(text = "Item Type Pills", style = typography.meta, color = colors.textSecondary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(dimens.Space8)
                            ) {
                                CourseItemTypePill(type = CourseItemType.QUIZ)
                                CourseItemTypePill(type = CourseItemType.ASSIGNMENT)
                                CourseItemTypePill(type = CourseItemType.EXAM_MID)
                                CourseItemTypePill(type = CourseItemType.EXAM_FINAL)
                                CourseItemTypePill(type = CourseItemType.PROJECT)
                                CourseItemTypePill(type = CourseItemType.LAB_REPORT)
                            }
                        }
                    }
                }

                // Section 4: Button Variants
                item {
                    SectionHeader(title = "APP BUTTON VARIANTS")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.Space12)) {
                            AppButton(
                                text = "Primary Cyan Button",
                                onClick = {},
                                variant = AppButtonVariant.PRIMARY,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AppButton(
                                text = "Outlined Sand Button",
                                onClick = {},
                                variant = AppButtonVariant.OUTLINED,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AppButton(
                                text = "Ghost Action Button",
                                onClick = {},
                                variant = AppButtonVariant.GHOST,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section 5: Day Chips
                item {
                    SectionHeader(title = "DAY CHIP STRIP")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DayChip(
                                dayAbbr = "MON",
                                dateNumber = "26",
                                classCount = 3,
                                isSelected = false,
                                isToday = false,
                                onClick = {}
                            )
                            DayChip(
                                dayAbbr = "TUE",
                                dateNumber = "27",
                                classCount = 2,
                                isSelected = false,
                                isToday = false,
                                onClick = {}
                            )
                            DayChip(
                                dayAbbr = "WED",
                                dateNumber = "28",
                                classCount = 4,
                                isSelected = true,
                                isToday = true,
                                onClick = {}
                            )
                            DayChip(
                                dayAbbr = "THU",
                                dateNumber = "29",
                                classCount = 1,
                                isSelected = false,
                                isToday = false,
                                onClick = {}
                            )
                            DayChip(
                                dayAbbr = "FRI",
                                dateNumber = "30",
                                classCount = 0,
                                isSelected = false,
                                isToday = false,
                                onClick = {}
                            )
                        }
                    }
                }

                // Section 6: Cards and Item Rows
                item {
                    SectionHeader(title = "CLASS CARDS (LIVE & UPCOMING)")
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.Space12)) {
                        // Live class card with cyan glow
                        ClassCard(
                            routine = ClassRoutineEntity(
                                id = 101,
                                courseCode = "CSC3201",
                                courseName = "OPERATING SYSTEMS LAB",
                                section = "D",
                                dayOfWeek = "Wednesday",
                                startTime = "08:00",
                                endTime = "10:00",
                                room = "DS0302",
                                faculty = "Dr. M. Rahman"
                            ),
                            status = ClassStatus.LIVE_NOW,
                            facultyName = "Dr. M. Rahman",
                            onCardClick = {},
                            onAddTaskClick = {}
                        )

                        // Upcoming class card
                        ClassCard(
                            routine = ClassRoutineEntity(
                                id = 102,
                                courseCode = "CSC2104",
                                courseName = "OBJECT ORIENTED PROGRAMMING 2",
                                section = "V",
                                dayOfWeek = "Wednesday",
                                startTime = "15:00",
                                endTime = "17:00",
                                room = "9303",
                                faculty = "Prof. J. Hasan"
                            ),
                            status = ClassStatus.UPCOMING,
                            facultyName = "Prof. J. Hasan",
                            onCardClick = {},
                            onAddTaskClick = {}
                        )
                    }
                }

                // Section 7: Tasks Item Row
                item {
                    SectionHeader(title = "ITEM ROW COMPACT COMPONENT")
                    ItemRow(
                        item = CourseItemEntity(
                            id = 201,
                            courseKey = "algorithms_s",
                            courseName = "ALGORITHMS",
                            section = "S",
                            type = CourseItemType.QUIZ,
                            title = "Quiz 2: Dynamic Programming",
                            dueAt = System.currentTimeMillis() + 86400000L * 2,
                            hasTime = true,
                            topics = "DP memoization, Knapsack problem",
                            status = if (sampleItemDone) CourseItemStatus.DONE else CourseItemStatus.TODO
                        ),
                        onToggleDone = { sampleItemDone = it },
                        onClick = {}
                    )
                }

                // Section 8: Empty State
                item {
                    SectionHeader(title = "EMPTY STATE COMPONENT")
                    EmptyState(
                        title = "No Classes Scheduled",
                        message = "Your routine for this day is clear. Use this time to prepare for upcoming assignments.",
                        actionButtonText = "View Routine"
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorTokenRow(name: String, hex: String, color: Color) {
    val typography = UniTrackTheme.typography
    val colors = UniTrackTheme.colors
    val dimens = UniTrackTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.Space12)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(dimens.HairlineBorder, colors.cardBorder, RoundedCornerShape(6.dp))
            )
            Text(
                text = name,
                style = typography.body,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = hex,
            style = typography.meta,
            color = colors.textSecondary
        )
    }
}
