package com.example.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.CourseSettingEntity
import com.example.ui.components.AddEditCourseItemSheet
import com.example.ui.components.AppBackground
import com.example.ui.components.ClassCard
import com.example.ui.components.ClassStatus
import com.example.ui.components.DayChip
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.ItemRow
import com.example.ui.components.RoomPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.SectionPill
import com.example.ui.components.TimePill
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNavigateToPortal: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRoutine: () -> Unit = {},
    viewModel: TodayViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin"
    )

    AppBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.ScreenPadding),
            contentPadding = PaddingValues(top = dimens.Space8, bottom = dimens.Space32),
            verticalArrangement = Arrangement.spacedBy(dimens.RhythmMedium)
        ) {
            // Sync status header banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.Space4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        if (uiState.isOffline) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = "Offline Mode",
                                tint = colors.warning,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Offline · ${uiState.lastSyncedText}",
                                style = typography.meta,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = uiState.lastSyncedText,
                                style = typography.meta,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Sync action button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(dimens.PillRadius))
                            .background(colors.surfaceField)
                            .clickable(enabled = !uiState.isSyncing) {
                                if (uiState.isSessionActive) {
                                    viewModel.triggerSync()
                                } else {
                                    onNavigateToPortal()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("today_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Sync Routine",
                            tint = if (uiState.isSyncing) colors.accentCyan else colors.textSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .then(if (uiState.isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                        Spacer(modifier = Modifier.width(dimens.Space4))
                        Text(
                            text = if (uiState.isSyncing) "Syncing..." else "Sync",
                            style = typography.labelBadge,
                            color = if (uiState.isSyncing) colors.accentCyan else colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Sync message toast (if any)
            uiState.syncMessage?.let { msg ->
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = dimens.Space12
                    ) {
                        Text(
                            text = msg,
                            style = typography.meta,
                            color = colors.accentCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SEGMENT 1: HERO CARD
            // LIVE NOW (with progress bar, remaining time, room) OR NEXT CLASS TODAY (sand badge, countdown pill, room) OR "No more classes today" with tomorrow's first class.
            item {
                when {
                    uiState.liveNowClass != null -> {
                        val live = uiState.liveNowClass!!
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            glow = true,
                            onClick = { viewModel.selectClass(live) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colors.accentCyan)
                                        )
                                        Text(
                                            text = "LIVE NOW",
                                            style = typography.labelBadge,
                                            color = colors.accentCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }

                                    TimePill(
                                        timeRangeText = DateTimeUtils.formatTimeRange12h(live.startTime, live.endTime)
                                    )
                                }

                                Spacer(modifier = Modifier.height(dimens.Space12))

                                Text(
                                    text = live.courseName,
                                    style = typography.heroCourseTitle,
                                    color = colors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(dimens.Space8))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (live.section.isNotBlank()) {
                                        SectionPill(section = live.section)
                                    }
                                    if (live.room.isNotBlank()) {
                                        RoomPill(roomText = live.room)
                                    }
                                }

                                Spacer(modifier = Modifier.height(dimens.Space16))

                                // Progress Bar and remaining time
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LinearProgressIndicator(
                                        progress = { uiState.liveNowProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = colors.accentCyan,
                                        trackColor = colors.surfaceField
                                    )

                                    Spacer(modifier = Modifier.height(dimens.Space8))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${uiState.liveNowRemainingMinutes}m remaining",
                                            style = typography.meta,
                                            color = colors.accentCyan,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Ends at ${DateTimeUtils.formatTime12h(live.endTime)}",
                                            style = typography.meta,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    uiState.upNextClass != null -> {
                        val next = uiState.upNextClass!!
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectClass(next) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "NEXT CLASS TODAY",
                                        style = typography.labelBadge,
                                        color = colors.sand,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )

                                    val countdownText = if (uiState.upNextMinutes >= 60) {
                                        "in ${uiState.upNextMinutes / 60}h ${uiState.upNextMinutes % 60}m"
                                    } else {
                                        "in ${uiState.upNextMinutes}m"
                                    }

                                    Text(
                                        text = countdownText,
                                        style = typography.labelBadge,
                                        color = colors.infoBlue,
                                        modifier = Modifier
                                            .background(
                                                color = colors.infoBlue.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(dimens.PillRadius)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(dimens.Space12))

                                Text(
                                    text = next.courseName,
                                    style = typography.heroCourseTitle,
                                    color = colors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(dimens.Space8))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimePill(timeRangeText = DateTimeUtils.formatTimeRange12h(next.startTime, next.endTime))
                                    if (next.section.isNotBlank()) {
                                        SectionPill(section = next.section)
                                    }
                                    if (next.room.isNotBlank()) {
                                        RoomPill(roomText = next.room)
                                    }
                                }
                            }
                        }
                    }

                    uiState.firstClassTomorrow != null -> {
                        val tomorrow = uiState.firstClassTomorrow!!
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "NO MORE CLASSES TODAY",
                                    style = typography.labelBadge,
                                    color = colors.sand,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )

                                Spacer(modifier = Modifier.height(dimens.Space8))

                                Text(
                                    text = "First class tomorrow at ${DateTimeUtils.formatTime12h(tomorrow.startTime)}",
                                    style = typography.body,
                                    color = colors.textSecondary
                                )

                                Spacer(modifier = Modifier.height(dimens.Space8))

                                Text(
                                    text = tomorrow.courseName,
                                    style = typography.sectionTitle,
                                    color = colors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(dimens.Space8))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tomorrow.section.isNotBlank()) {
                                        SectionPill(section = tomorrow.section)
                                    }
                                    if (tomorrow.room.isNotBlank()) {
                                        RoomPill(roomText = tomorrow.room)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "ALL CAUGHT UP",
                                    style = typography.labelBadge,
                                    color = colors.sand,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(dimens.Space8))
                                Text(
                                    text = "No classes scheduled for today or tomorrow. Enjoy your break!",
                                    style = typography.body,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // SEGMENT 2: TODAY'S SCHEDULE CARD
            // header with sand title "TODAY'S SCHEDULE", full date, Previous/Next day buttons, "TODAY" jump tag, and the horizontal 7-day DayChip row.
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = dimens.Space16
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header row with title, date, and prev/next controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isViewingToday) "TODAY'S SCHEDULE" else "${uiState.selectedDay.uppercase()} SCHEDULE",
                                    style = typography.sectionHeaderLabel,
                                    color = colors.sand
                                )
                                Text(
                                    text = uiState.selectedDayHeadline,
                                    style = typography.meta,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.Space4)
                            ) {
                                // Previous day button
                                IconButton(
                                    onClick = { viewModel.selectPreviousDay() },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("schedule_prev_day")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronLeft,
                                        contentDescription = "Previous Day",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // "TODAY" jump pill
                                if (!uiState.isViewingToday) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(dimens.PillRadius))
                                            .background(colors.accentCyan.copy(alpha = 0.15f))
                                            .border(
                                                width = dimens.HairlineBorder,
                                                color = colors.accentCyan.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(dimens.PillRadius)
                                            )
                                            .clickable { viewModel.selectToday() }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("schedule_jump_today")
                                    ) {
                                        Text(
                                            text = "TODAY",
                                            style = typography.labelBadge,
                                            color = colors.accentCyan,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // Next day button
                                IconButton(
                                    onClick = { viewModel.selectNextDay() },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("schedule_next_day")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = "Next Day",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(dimens.Space16))

                        // Horizontal 7-Day DayChip row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                        ) {
                            uiState.weekDays.forEach { dayItem ->
                                val isSelected = dayItem.dayOfWeek.equals(uiState.selectedDay, ignoreCase = true)
                                DayChip(
                                    dayAbbr = dayItem.dayAbbr,
                                    dateNumber = dayItem.dateNumber,
                                    classCount = dayItem.classCount,
                                    isSelected = isSelected,
                                    isToday = dayItem.isToday,
                                    onClick = { viewModel.selectDay(dayItem.dayOfWeek) }
                                )
                            }
                        }
                    }
                }
            }

            // SEGMENT 3: CLASSES LIST FOR SELECTED DAY
            item {
                SectionHeader(
                    title = if (uiState.isViewingToday) {
                        "Today's Classes (${uiState.classesForSelectedDay.size})"
                    } else {
                        "${uiState.selectedDay}'s Classes (${uiState.classesForSelectedDay.size})"
                    },
                    actionText = "Full Timetable",
                    onActionClick = onNavigateToRoutine
                )
            }

            if (uiState.classesForSelectedDay.isNotEmpty()) {
                items(uiState.classesForSelectedDay, key = { it.id }) { routine ->
                    val courseKey = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                    val faculty = uiState.facultyNamesByCourseKey[courseKey]

                    val status = if (uiState.isViewingToday) {
                        val timing = DateTimeUtils.getClassTimingStatus(routine.startTime, routine.endTime)
                        when (timing.first) {
                            "IN_PROGRESS" -> ClassStatus.LIVE_NOW
                            "PAST" -> ClassStatus.DONE
                            else -> ClassStatus.UPCOMING
                        }
                    } else {
                        ClassStatus.UPCOMING
                    }

                    ClassCard(
                        routine = routine,
                        status = status,
                        facultyName = faculty,
                        onCardClick = { viewModel.selectClass(routine) },
                        onAddTaskClick = { viewModel.openAddItemForCourse(courseKey) }
                    )
                }
            } else {
                item {
                    EmptyState(
                        title = "No classes scheduled",
                        message = "No classes scheduled for ${uiState.selectedDay}.",
                        icon = Icons.Outlined.EventBusy
                    )
                }
            }

            // SEGMENT 4: "DUE SOON" CARD
            // next 3 items (quiz/assignment/exam) with a "See all" button that jumps to Tasks tab.
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = dimens.Space16
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "DUE SOON",
                            actionText = "See all",
                            onActionClick = onNavigateToTasks
                        )

                        Spacer(modifier = Modifier.height(dimens.Space8))

                        if (uiState.dueSoonItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                                uiState.dueSoonItems.forEach { item ->
                                    ItemRow(
                                        item = item,
                                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) },
                                        onClick = { viewModel.openEditItem(item) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No upcoming quizzes, assignments, or exams due soon.",
                                style = typography.body,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(vertical = dimens.Space8)
                            )
                        }
                    }
                }
            }

            // SEGMENT 5: ATTENDANCE ALERT CARD (if any course is below threshold)
            if (uiState.attendanceAlerts.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = dimens.Space16
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = "Attendance Warning",
                                    tint = colors.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "ATTENDANCE ALERT",
                                    style = typography.labelBadge,
                                    color = colors.warning,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(dimens.Space8))

                            uiState.attendanceAlerts.forEach { alert ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = dimens.Space4),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alert.courseName,
                                            style = typography.body,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${alert.attendedCount}/${alert.totalClasses} classes attended",
                                            style = typography.meta,
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Text(
                                        text = "${alert.percentage.toInt()}%",
                                        style = typography.sectionTitle,
                                        color = colors.danger,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet 1: Class Detail Sheet
    uiState.selectedClass?.let { routine ->
        ClassDetailSheet(
            routine = routine,
            attendance = uiState.selectedClassAttendance,
            tasks = uiState.selectedClassTasks,
            isReminderEnabled = uiState.selectedClassReminderEnabled,
            onToggleReminder = { enabled -> viewModel.toggleCourseReminder(enabled) },
            onToggleTask = { task, completed -> viewModel.toggleTask(task, completed) },
            onAddTask = { title -> viewModel.addTaskForCourse(title) },
            onDismiss = { viewModel.selectClass(null) },
            onOpenCourse = onOpenCourse
        )
    }

    // Modal Sheet 2: Add/Edit Course Item Sheet
    if (uiState.showAddEditSheet) {
        AddEditCourseItemSheet(
            existingItem = uiState.addEditItem,
            preselectedCourseKey = uiState.addEditCourseKey,
            initialType = uiState.addEditInitialType,
            availableCourses = uiState.availableCourses,
            onSave = { item -> viewModel.saveCourseItem(item) },
            onDelete = { item -> viewModel.deleteCourseItem(item) },
            onDismiss = { viewModel.dismissAddEditSheet() }
        )
    }
}
