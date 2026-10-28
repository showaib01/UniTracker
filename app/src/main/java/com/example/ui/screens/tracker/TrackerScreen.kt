package com.example.ui.screens.tracker

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UniTrackApplication
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.TaskEntity
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun TrackerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication
    val scope = rememberCoroutineScope()
    val attendanceDao = app.database.attendanceDao()
    val taskDao = app.database.taskDao()
    val prefsRepo = app.preferencesRepository

    val attendanceList by attendanceDao.getAllAttendance().collectAsState(initial = emptyList())
    val tasksList by taskDao.getAllTasks().collectAsState(initial = emptyList())
    val userPrefs by prefsRepo.userPreferencesFlow.collectAsState(
        initial = com.example.data.local.preferences.UserPreferences()
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val threshold = userPrefs.attendanceThreshold

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tracker_screen")
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Attendance (${attendanceList.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Assignments (${tasksList.size})") }
            )
        }

        if (selectedTab == 0) {
            // Attendance List & Calculator
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "AIUB Academic Policy Threshold: $threshold%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Courses falling below $threshold% are flagged with warnings. You can manually adjust attendance using +1 / -1 if the portal update is pending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                items(attendanceList, key = { it.courseCode }) { record ->
                    AttendanceCard(
                        record = record,
                        threshold = threshold,
                        onAdjust = { delta ->
                            scope.launch {
                                attendanceDao.adjustManualCorrection(record.courseCode, delta)
                            }
                        }
                    )
                }
            }
        } else {
            // Tasks & Deadlines List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Course Assignments & Exams",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(tasksList, key = { it.id }) { task ->
                    TaskDeadlineItem(task = task)
                }
            }
        }
    }
}

@Composable
fun AttendanceCard(
    record: AttendanceEntity,
    threshold: Int,
    onAdjust: (Int) -> Unit
) {
    val colors = UniTrackTheme.colors
    val percentage = record.percentage
    val isWarning = percentage < threshold

    // Skip/Attend Calculator
    // To stay above T%, attended / total >= T/100
    // If percentage >= threshold: how many can skip? (attended / (total + x)) >= T/100 => total + x <= attended / (T/100) => x = floor(attended / (T/100) - total)
    // If percentage < threshold: how many to attend? (attended + y) / (total + y) >= T/100 => y = ceil((T*total - 100*attended)/(100 - T))
    val tRatio = threshold / 100.0
    val canSkipCount = if (percentage >= threshold && tRatio > 0) {
        val maxTotal = record.effectiveAttended / tRatio
        floor(maxTotal - record.totalClasses).toInt().coerceAtLeast(0)
    } else 0

    val mustAttendCount = if (percentage < threshold && threshold < 100) {
        val needed = ceil((threshold * record.totalClasses - 100.0 * record.effectiveAttended) / (100.0 - threshold)).toInt()
        needed.coerceAtLeast(1)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_card_${record.courseCode}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) colors.statusDanger.copy(alpha = 0.15f)
            else colors.cardFill
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.courseName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.courseCode} • Sec [${record.section}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentCyan
                    )
                }

                Surface(
                    color = if (isWarning) colors.statusDanger else colors.statusSuccess,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format("%.1f%%", percentage),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isWarning) colors.statusDanger else colors.statusSuccess,
                trackColor = colors.surfaceField
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Attendance details & manual correction buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.effectiveAttended} / ${record.totalClasses} classes attended" +
                            if (record.manualCorrection != 0) " (${if (record.manualCorrection > 0) "+" else ""}${record.manualCorrection} manual)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjust(-1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onAdjust(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calculator recommendation
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isWarning) colors.statusDanger else colors.statusSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWarning) {
                            "Warning: You must attend the next $mustAttendCount classes to reach $threshold%!"
                        } else {
                            if (canSkipCount > 0) {
                                "On Track: You can safely miss up to $canSkipCount classes and stay above $threshold%."
                            } else {
                                "On Track: Don't miss the next class to maintain $threshold%."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = if (isWarning) colors.statusDanger else colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun TaskDeadlineItem(
    task: TaskEntity,
    onToggleDone: ((Boolean) -> Unit)? = null
) {
    val colors = UniTrackTheme.colors
    val relativeDate = DateTimeUtils.formatRelativeDueDate(task.dueDateMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${task.courseName} · $relativeDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            if (task.priority.isNotEmpty()) {
                val priorityColor = when (task.priority) {
                    "HIGH" -> colors.statusDanger
                    "MEDIUM" -> colors.statusWarning
                    else -> colors.accentCyan
                }
                Surface(
                    color = priorityColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = task.priority,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }
        }
    }
}
