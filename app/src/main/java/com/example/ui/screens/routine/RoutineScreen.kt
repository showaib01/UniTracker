package com.example.ui.screens.routine

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UniTrackApplication
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.TaskEntity
import com.example.ui.screens.today.ClassDetailSheet
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    onOpenCourse: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication
    val routineDao = app.database.routineDao()
    val attendanceDao = app.database.attendanceDao()
    val taskDao = app.database.taskDao()
    val courseSettingDao = app.database.courseSettingDao()

    val scope = rememberCoroutineScope()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val allRoutines by routineDao.getAllRoutines().collectAsState(initial = emptyList())

    val daysOfWeek = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    var selectedDay by remember { mutableStateOf(DateTimeUtils.getCurrentDayOfWeek()) }

    val filteredRoutines = allRoutines.filter { it.dayOfWeek.equals(selectedDay, ignoreCase = true) }

    // Detail sheet state
    var selectedRoutine by remember { mutableStateOf<ClassRoutineEntity?>(null) }
    var selectedAttendance by remember { mutableStateOf<AttendanceEntity?>(null) }
    var selectedTasks by remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    var selectedReminderEnabled by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("routine_screen")
        ) {
            // Day Selector Bar (Chips radius 999dp)
            Surface(
                color = colors.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.hairline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = dimens.ScreenPadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    daysOfWeek.forEach { day ->
                        val isToday = day.equals(DateTimeUtils.getCurrentDayOfWeek(), ignoreCase = true)
                        val isSelected = selectedDay == day

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDay = day },
                            shape = RoundedCornerShape(dimens.ChipRadius),
                            label = {
                                Text(
                                    text = if (isToday) "${day.take(3)} · Today" else day.take(3),
                                    style = typography.metaText.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentNavy.copy(alpha = 0.1f),
                                selectedLabelColor = colors.accentNavy,
                                containerColor = colors.background,
                                labelColor = colors.ink
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) colors.accentNavy else colors.hairline,
                                selectedBorderColor = colors.accentNavy
                            ),
                            modifier = Modifier.testTag("day_chip_$day")
                        )
                    }
                }
            }

            // Routine list for the selected day
            if (filteredRoutines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No classes on $selectedDay",
                        style = typography.metaText,
                        color = colors.secondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = dimens.ScreenPadding,
                        vertical = dimens.RhythmMedium
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$selectedDay".uppercase(),
                                style = typography.sectionTitle,
                                color = colors.secondary
                            )
                            Text(
                                text = "${filteredRoutines.size} classes",
                                style = typography.metaText.copy(fontSize = 12.sp),
                                color = colors.secondary
                            )
                        }
                    }

                    items(filteredRoutines, key = { it.id }) { routine ->
                        RoutineClassCard(
                            routine = routine,
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                                    val setting = courseSettingDao.getSettingDirect(key)
                                    val isRemEnabled = setting?.notificationsEnabled ?: true

                                    val allAtt = attendanceDao.getAllAttendanceDirect()
                                    val att = allAtt.firstOrNull {
                                        it.courseName.equals(routine.courseName, ignoreCase = true) &&
                                                it.section.equals(routine.section, ignoreCase = true)
                                    } ?: allAtt.firstOrNull {
                                        it.courseName.equals(routine.courseName, ignoreCase = true)
                                    }

                                    val allTasks = taskDao.getAllTasksDirect()
                                    val courseTasks = allTasks.filter {
                                        it.courseName.equals(routine.courseName, ignoreCase = true)
                                    }

                                    selectedRoutine = routine
                                    selectedAttendance = att
                                    selectedTasks = courseTasks
                                    selectedReminderEnabled = isRemEnabled
                                }
                            }
                        )
                    }
                }
            }
        }

        // Class Detail Sheet Modal
        selectedRoutine?.let { routine ->
            ClassDetailSheet(
                routine = routine,
                attendance = selectedAttendance,
                tasks = selectedTasks,
                isReminderEnabled = selectedReminderEnabled,
                onToggleReminder = { enabled ->
                    scope.launch(Dispatchers.IO) {
                        val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                        val existing = courseSettingDao.getSettingDirect(key)
                        if (existing != null) {
                            courseSettingDao.setNotificationsEnabled(key, enabled)
                        } else {
                            courseSettingDao.insertOrUpdate(
                                CourseSettingEntity(
                                    courseKey = key,
                                    courseName = routine.courseName,
                                    section = routine.section,
                                    notificationsEnabled = enabled,
                                    isDropped = false
                                )
                            )
                        }
                        selectedReminderEnabled = enabled
                    }
                },
                onToggleTask = { task, completed ->
                    scope.launch(Dispatchers.IO) {
                        taskDao.updateTaskCompletion(task.id, completed)
                        val allTasks = taskDao.getAllTasksDirect()
                        selectedTasks = allTasks.filter {
                            it.courseName.equals(routine.courseName, ignoreCase = true)
                        }
                    }
                },
                onAddTask = { title ->
                    scope.launch(Dispatchers.IO) {
                        taskDao.insertTask(
                            TaskEntity(
                                title = title,
                                courseName = routine.courseName,
                                dueDateMillis = System.currentTimeMillis() + 86400000L * 3,
                                priority = "MEDIUM",
                                taskType = "ASSIGNMENT"
                            )
                        )
                        val allTasks = taskDao.getAllTasksDirect()
                        selectedTasks = allTasks.filter {
                            it.courseName.equals(routine.courseName, ignoreCase = true)
                        }
                    }
                },
                onOpenCourse = onOpenCourse,
                onDismiss = { selectedRoutine = null }
            )
        }
    }
}

@Composable
private fun RoutineClassCard(
    routine: ClassRoutineEntity,
    onClick: () -> Unit
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val timeRange12h = DateTimeUtils.formatTimeRange12h(routine.startTime, routine.endTime)

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(dimens.CardRadius),
        border = androidx.compose.foundation.BorderStroke(dimens.HairlineBorder, colors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("routine_item_${routine.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.courseName,
                    style = typography.classTitle.copy(fontSize = 16.sp),
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Sec ${routine.section}",
                        style = typography.metaText,
                        color = colors.secondary
                    )
                    com.example.ui.components.RoomPill(roomText = routine.room)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            com.example.ui.components.TimePill(timeRangeText = timeRange12h)
        }
    }
}
