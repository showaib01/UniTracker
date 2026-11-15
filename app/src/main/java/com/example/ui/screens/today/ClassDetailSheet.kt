package com.example.ui.screens.today

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.TaskEntity
import com.example.ui.components.RoomPill
import com.example.ui.components.TimePill
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils

/**
 * Editorial modal bottom sheet for displaying class details, tasks, and Google Drive notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailSheet(
    routine: ClassRoutineEntity,
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
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = dimens.ScreenPadding)
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Course Code badge + Time Pill + Room Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = colors.accentCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(dimens.PillRadius)
                    ) {
                        Text(
                            text = routine.courseCode.ifBlank { "COURSE" },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = typography.labelBadge,
                            color = colors.accentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TimePill(timeRangeText = DateTimeUtils.formatTimeRange12h(routine.startTime, routine.endTime))
                }

                RoomPill(roomText = routine.room)
            }

            Spacer(modifier = Modifier.height(dimens.Space12))

            // Course Name (22sp Semibold)
            Text(
                text = routine.courseName,
                style = typography.screenTitle,
                color = colors.textPrimary,
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(dimens.Space4))

            // Section & Faculty
            val sectionText = "Section: ${routine.section}"
            val facultyText = if (!routine.faculty.isBlank()) " · ${routine.faculty}" else ""
            Text(
                text = "$sectionText$facultyText",
                style = typography.meta,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(dimens.Space20))

            // Action Bar (Reminders Toggle + Open Course Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reminder toggle pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimens.PillRadius))
                        .background(if (isReminderEnabled) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField)
                        .border(dimens.HairlineBorder, if (isReminderEnabled) colors.accentCyan else colors.cardBorder, RoundedCornerShape(dimens.PillRadius))
                        .clickable { onToggleReminder(!isReminderEnabled) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("toggle_reminder_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isReminderEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = if (isReminderEnabled) colors.accentCyan else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isReminderEnabled) "Reminders On" else "Muted",
                        style = typography.labelBadge,
                        color = if (isReminderEnabled) colors.accentCyan else colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onOpenCourse != null) {
                    val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                    Surface(
                        color = colors.accentCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(dimens.PillRadius),
                        modifier = Modifier
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

            Spacer(modifier = Modifier.height(dimens.Space20))

            // TASKS SECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TASKS FOR THIS COURSE",
                    style = typography.sectionHeaderLabel,
                    color = colors.sand
                )

                IconButton(
                    onClick = { isAddingTask = !isAddingTask },
                    modifier = Modifier.size(32.dp).testTag("add_task_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Task",
                        tint = colors.accentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.Space8))

            if (isAddingTask) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("Task description...", color = colors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_quick_task"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    IconButton(
                        onClick = {
                            if (newTaskTitle.isNotBlank()) {
                                onAddTask(newTaskTitle.trim())
                                newTaskTitle = ""
                                isAddingTask = false
                            }
                        },
                        modifier = Modifier.testTag("submit_quick_task")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Save Task",
                            tint = colors.accentCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(dimens.Space12))
            }

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks added for this class yet.",
                    style = typography.meta,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasks.forEach { task ->
                        val isDone = task.status == "DONE"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                .background(colors.surfaceField)
                                .clickable { onToggleTask(task, !isDone) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isDone) colors.accentCyan else colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = task.title,
                                    style = typography.body,
                                    color = if (isDone) colors.textSecondary else colors.textPrimary,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
