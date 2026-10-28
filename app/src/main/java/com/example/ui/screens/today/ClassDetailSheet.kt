package com.example.ui.screens.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.TaskEntity
import com.example.ui.components.RoomPill
import com.example.ui.components.TimePill
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

/**
 * Editorial modal bottom sheet for displaying class details, attendance, tasks, and Google Drive notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailSheet(
    routine: ClassRoutineEntity,
    attendance: AttendanceEntity?,
    tasks: List<TaskEntity>,
    isReminderEnabled: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onToggleTask: (TaskEntity, Boolean) -> Unit,
    onAddTask: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenCourse: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var isAddingTask by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceSheet,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.cardBorder)
            )
        },
        modifier = modifier.testTag("class_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.ScreenPadding)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.courseName,
                        style = typography.heroCourseTitle,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sec ${routine.section}",
                            style = typography.meta,
                            color = colors.textSecondary
                        )
                        TimePill(
                            timeRangeText = DateTimeUtils.formatTimeRange12h(routine.startTime, routine.endTime)
                        )
                        RoomPill(
                            roomText = routine.room
                        )
                    }
                }

                if (onOpenCourse != null) {
                    val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                    Box(
                        modifier = Modifier
                            .border(dimens.HairlineBorder, colors.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(dimens.PillRadius))
                            .background(colors.accentCyan.copy(alpha = 0.12f), RoundedCornerShape(dimens.PillRadius))
                            .clickable {
                                onDismiss()
                                onOpenCourse(key)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("open_course_button")
                    ) {
                        Text(
                            text = "Open course",
                            style = typography.labelBadge,
                            color = colors.accentCyan
                        )
                    }
                }
            }

            // Attendance line if available
            if (attendance != null && attendance.totalClasses > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val isBelowThreshold = attendance.percentage < 80f
                val attendanceColor = if (isBelowThreshold) colors.warning else colors.success

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dimens.BoxRadius))
                        .border(dimens.HairlineBorder, colors.cardBorder, RoundedCornerShape(dimens.BoxRadius))
                        .background(colors.surfaceField)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(attendanceColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.attendance_attended_summary,
                            attendance.attendedCount,
                            attendance.totalClasses,
                            attendance.percentage.toInt()
                        ),
                        style = typography.meta,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.RhythmMedium))

            // Row of 3 quiet action buttons: Notes, Tasks, Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuietActionButton(
                    icon = Icons.Outlined.Folder,
                    label = stringResource(R.string.class_action_notes),
                    isActive = false,
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )

                QuietActionButton(
                    icon = Icons.Outlined.CheckCircle,
                    label = stringResource(R.string.class_action_tasks),
                    isActive = false,
                    onClick = { isAddingTask = true },
                    modifier = Modifier.weight(1f)
                )

                val bellIcon = if (isReminderEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff
                QuietActionButton(
                    icon = bellIcon,
                    label = stringResource(R.string.class_action_reminder),
                    isActive = isReminderEnabled,
                    onClick = { onToggleReminder(!isReminderEnabled) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_class_reminder")
                )
            }

            Spacer(modifier = Modifier.height(dimens.RhythmMedium))

            // NOTES section
            Text(
                text = stringResource(R.string.class_action_notes).uppercase(),
                style = typography.sectionHeaderLabel,
                color = colors.sand
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = colors.surfaceField,
                shape = RoundedCornerShape(dimens.BoxRadius),
                border = androidx.compose.foundation.BorderStroke(dimens.HairlineBorder, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.notes_connect_drive_hint),
                        style = typography.meta,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.notes_add_note), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.notes_open_drive), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.RhythmMedium))

            // TASKS section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.class_action_tasks).uppercase(),
                    style = typography.sectionHeaderLabel,
                    color = colors.sand
                )
                Text(
                    text = "+ ${stringResource(R.string.tasks_add_task)}",
                    style = typography.meta.copy(fontWeight = FontWeight.Medium, color = colors.accentCyan),
                    modifier = Modifier
                        .clickable { isAddingTask = !isAddingTask }
                        .padding(4.dp)
                        .testTag("add_task_trigger")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAddingTask) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("Task description...", fontSize = 13.sp, color = colors.textSecondary) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_task_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.surfaceField,
                            unfocusedContainerColor = colors.surfaceField,
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (newTaskTitle.isNotBlank()) {
                                onAddTask(newTaskTitle.trim())
                                newTaskTitle = ""
                                isAddingTask = false
                            }
                        },
                        shape = RoundedCornerShape(dimens.BoxRadius),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Add", fontSize = 13.sp, color = colors.accentCyan)
                    }
                }
            }

            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_upcoming_deadlines),
                    style = typography.meta,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable { onToggleTask(task, !task.isCompleted) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    dimens.HairlineBorder,
                                    if (task.isCompleted) colors.accentCyan else colors.textSecondary,
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (task.isCompleted) colors.accentCyan else colors.surfaceField),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = colors.surfaceSheet,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = task.title,
                            style = typography.meta.copy(
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (task.isCompleted) colors.textSecondary else colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val bgColor = if (isActive) colors.accentCyan.copy(alpha = 0.12f) else colors.surfaceField
    val borderColor = if (isActive) colors.accentCyan else colors.cardBorder
    val contentColor = if (isActive) colors.accentCyan else colors.textPrimary

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(dimens.BoxRadius),
        border = androidx.compose.foundation.BorderStroke(dimens.HairlineBorder, borderColor),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = typography.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = contentColor
            )
        }
    }
}
