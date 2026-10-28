package com.example.ui.screens.course

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseItemType
import com.example.ui.components.AddEditCourseItemSheet
import com.example.ui.components.CourseItemTypePill
import com.example.ui.components.CoursePickerOption
import com.example.ui.components.RoomPill
import com.example.ui.components.TimePill
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    viewModel: CourseViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val snackbarHostState = remember { SnackbarHostState() }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("course_screen"),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Editorial Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp).testTag("course_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { overflowMenuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More Options",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.isRemindersMuted) "Unmute reminders" else "Mute reminders")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.isRemindersMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    viewModel.toggleMuteReminders()
                                    overflowMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.isDropped) "Restore course" else "Drop course")
                                },
                                onClick = {
                                    viewModel.toggleDropCourse()
                                    overflowMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.Space4))

                // Course Name & Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.courseName,
                        style = typography.classTitle.copy(fontSize = 20.sp),
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.section.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                                .background(colors.surface, RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Sec ${uiState.section}",
                                style = typography.metaText.copy(fontSize = 11.sp),
                                color = colors.secondary
                            )
                        }
                    }
                }

                // Class schedule preview in 12h
                if (uiState.routines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.routines.forEach { r ->
                            val timeRange = DateTimeUtils.formatTimeRange12h(r.startTime, r.endTime)
                            Row(
                                modifier = Modifier
                                    .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = r.dayOfWeek.take(3),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.accentNavy
                                )
                                TimePill(timeRangeText = timeRange)
                                RoomPill(roomText = r.room)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.Space20)
        ) {
            // Quick Add Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAddButton(label = "+ Quiz", onClick = { viewModel.openAddItem(CourseItemType.QUIZ) })
                    QuickAddButton(label = "+ Assignment", onClick = { viewModel.openAddItem(CourseItemType.ASSIGNMENT) })
                    QuickAddButton(label = "+ Exam", onClick = { viewModel.openAddItem(CourseItemType.EXAM_MID) })
                    QuickAddButton(label = "More", onClick = { viewModel.openAddItem(null) })
                }
            }

            // UPCOMING Section
            item {
                Text(
                    text = "UPCOMING",
                    style = typography.sectionTitle,
                    color = colors.secondary
                )
            }

            if (uiState.upcomingItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
                            .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
                            .padding(vertical = dimens.Space24, horizontal = dimens.Space16),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No quizzes or assignments yet",
                                style = typography.metaText,
                                color = colors.secondary
                            )
                            Spacer(modifier = Modifier.height(dimens.Space12))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickAddButton(label = "+ Quiz", onClick = { viewModel.openAddItem(CourseItemType.QUIZ) })
                                QuickAddButton(label = "+ Assignment", onClick = { viewModel.openAddItem(CourseItemType.ASSIGNMENT) })
                            }
                        }
                    }
                }
            } else {
                items(uiState.upcomingItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }

            // PAST Section (Collapsed by default)
            if (uiState.pastItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.togglePastExpanded() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PAST (${uiState.pastItems.size})",
                            style = typography.sectionTitle,
                            color = colors.secondary
                        )

                        Icon(
                            imageVector = if (uiState.isPastExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (uiState.isPastExpanded) {
                    items(uiState.pastItems, key = { it.id }) { item ->
                        CourseItemCard(
                            item = item,
                            onClick = { viewModel.openEditItem(item) },
                            onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                        )
                    }
                }
            }

            // Attendance Summary & Calculator
            uiState.attendance?.let { att ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
                            .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
                            .padding(dimens.CardPadding)
                    ) {
                        Text(
                            text = "ATTENDANCE",
                            style = typography.sectionTitle,
                            color = colors.secondary
                        )
                        Spacer(modifier = Modifier.height(dimens.Space8))

                        val percentage = if (att.totalClasses > 0) {
                            (att.attendedCount.toFloat() / att.totalClasses.toFloat()) * 100f
                        } else 0f
                        val isWarning = percentage < uiState.attendanceThreshold

                        Text(
                            text = "Attended ${att.attendedCount} of ${att.totalClasses} · ${String.format("%.1f", percentage)}%",
                            style = typography.classTitle.copy(fontSize = 15.sp),
                            color = if (isWarning) colors.red else colors.ink
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        // Bunk/Attend calculation
                        val thresholdRatio = uiState.attendanceThreshold / 100.0
                        if (isWarning) {
                            val mustAttend = kotlin.math.ceil((thresholdRatio * att.totalClasses - att.attendedCount) / (1.0 - thresholdRatio)).toInt().coerceAtLeast(1)
                            Text(
                                text = "Must attend the next $mustAttend classes to reach ${uiState.attendanceThreshold}%",
                                style = typography.metaText,
                                color = colors.amber
                            )
                        } else {
                            val canMiss = kotlin.math.floor((att.attendedCount - thresholdRatio * att.totalClasses) / thresholdRatio).toInt().coerceAtLeast(0)
                            Text(
                                text = if (canMiss > 0) "Can safely miss $canMiss upcoming classes" else "Maintain attendance to stay above ${uiState.attendanceThreshold}%",
                                style = typography.metaText,
                                color = colors.green
                            )
                        }
                    }
                }
            }

            // NOTES section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
                        .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
                        .padding(dimens.CardPadding)
                ) {
                    Text(
                        text = "NOTES",
                        style = typography.sectionTitle,
                        color = colors.secondary
                    )
                    Spacer(modifier = Modifier.height(dimens.Space8))
                    Text(
                        text = "Course notes and attachments",
                        style = typography.metaText,
                        color = colors.secondary
                    )
                    Spacer(modifier = Modifier.height(dimens.Space8))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                            .background(colors.background, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = colors.accentNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Google Drive",
                                style = typography.classTitle.copy(fontSize = 13.sp),
                                color = colors.ink
                            )
                            Text(
                                text = "Auto-upload handwritten lecture notes",
                                style = typography.metaText.copy(fontSize = 11.sp),
                                color = colors.secondary
                            )
                        }
                    }
                }
            }

            // Class times this week mini list
            if (uiState.routines.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
                            .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
                            .padding(dimens.CardPadding)
                    ) {
                        Text(
                            text = "CLASS TIMES THIS WEEK",
                            style = typography.sectionTitle,
                            color = colors.secondary
                        )
                        Spacer(modifier = Modifier.height(dimens.Space8))

                        uiState.routines.forEach { r ->
                            val range = DateTimeUtils.formatTimeRange12h(r.startTime, r.endTime)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = r.dayOfWeek,
                                    style = typography.classTitle.copy(fontSize = 13.sp),
                                    color = colors.ink
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TimePill(timeRangeText = range)
                                    RoomPill(roomText = r.room)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(dimens.Space32))
            }
        }
    }

    // Add / Edit Item Sheet
    if (uiState.showAddEditSheet) {
        val routineDays = uiState.routines.map { it.dayOfWeek }
        val courseOption = CoursePickerOption(
            courseKey = uiState.courseKey,
            courseName = uiState.courseName,
            section = uiState.section,
            routineDays = routineDays,
            room = uiState.routines.firstOrNull()?.room ?: ""
        )

        AddEditCourseItemSheet(
            existingItem = uiState.editingItem,
            preselectedCourseKey = uiState.courseKey,
            initialType = uiState.initialAddType,
            availableCourses = listOf(courseOption),
            onSave = { updatedItem -> viewModel.saveItem(updatedItem) },
            onDelete = { itemToDelete -> viewModel.deleteItem(itemToDelete) },
            onDismiss = { viewModel.dismissAddEditSheet() }
        )
    }
}

@Composable
fun CourseItemCard(
    item: CourseItemEntity,
    onClick: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val isDone = item.isCompleted
    val dueBadge = DateTimeUtils.formatDueBadgeText(item.dueAt, item.hasTime)

    val badgeColor = when (dueBadge.urgency) {
        "OVERDUE" -> colors.red
        "URGENT" -> colors.amber
        else -> colors.secondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
            .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("course_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggleDone(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accentNavy,
                    uncheckedColor = colors.secondary
                ),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CourseItemTypePill(type = item.type)

                    Text(
                        text = item.title,
                        style = typography.classTitle.copy(fontSize = 15.sp),
                        color = if (isDone) colors.secondary else colors.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dueBadge.formattedDateTime,
                        style = typography.timeText.copy(fontSize = 12.sp),
                        color = colors.secondary
                    )

                    Text(text = "·", color = colors.secondary)

                    Text(
                        text = dueBadge.label,
                        style = typography.metaText.copy(fontSize = 12.sp),
                        color = if (isDone) colors.secondary else badgeColor,
                        fontWeight = if (dueBadge.urgency != "NORMAL") FontWeight.Medium else FontWeight.Normal
                    )

                    if (item.marks != null) {
                        Text(text = "·", color = colors.secondary)
                        Text(
                            text = "${item.marks} marks",
                            style = typography.metaText.copy(fontSize = 12.sp),
                            color = colors.secondary
                        )
                    }

                    if (item.scoreObtained != null) {
                        Text(text = "·", color = colors.secondary)
                        Text(
                            text = "Score: ${item.scoreObtained}",
                            style = typography.metaText.copy(fontSize = 12.sp),
                            color = colors.green
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddButton(
    label: String,
    onClick: () -> Unit
) {
    val colors = UniTrackTheme.colors
    Box(
        modifier = Modifier
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .background(colors.surface, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accentNavy
        )
    }
}
