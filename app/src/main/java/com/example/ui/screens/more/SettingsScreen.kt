package com.example.ui.screens.more

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.UniTrackApplication
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.ui.components.AppBackground
import com.example.ui.theme.UniTrackTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDebugLogs: () -> Unit = {},
    onNavigateToDesignSystem: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication
    val scope = rememberCoroutineScope()
    val prefsRepo = app.preferencesRepository
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography

    val userPrefs by prefsRepo.userPreferencesFlow.collectAsState(
        initial = com.example.data.local.preferences.UserPreferences(
            portalUrl = UserPreferencesRepository.DEFAULT_PORTAL_URL,
            routineUrl = "",
            attendanceUrl = "",
            studentId = "",
            rememberStudentId = false,
            attendanceThreshold = 80,
            themeMode = "SYSTEM",
            language = "en",
            lastSyncTimestamp = 0L,
            isSessionActive = false,
            reminderMinutes = 15
        )
    )

    var urlInput by remember(userPrefs.portalUrl) { mutableStateOf(userPrefs.portalUrl) }
    var routineUrlInput by remember(userPrefs.routineUrl) { mutableStateOf(userPrefs.routineUrl) }
    var attendanceUrlInput by remember(userPrefs.attendanceUrl) { mutableStateOf(userPrefs.attendanceUrl) }
    var thresholdSlider by remember(userPrefs.attendanceThreshold) { mutableFloatStateOf(userPrefs.attendanceThreshold.toFloat()) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceField,
        unfocusedContainerColor = colors.surfaceField,
        disabledContainerColor = colors.surfaceField.copy(alpha = 0.5f),
        focusedBorderColor = colors.accentCyan,
        unfocusedBorderColor = colors.cardBorder,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedPlaceholderColor = colors.textSecondary,
        unfocusedPlaceholderColor = colors.textSecondary,
        focusedLabelColor = colors.accentCyan,
        unfocusedLabelColor = colors.sand,
        cursorColor = colors.accentCyan
    )

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            containerColor = colors.surfaceSheet,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = colors.statusDanger) },
            title = {
                Text(
                    text = stringResource(R.string.settings_clear_data),
                    style = typography.sectionTitle,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_clear_data_confirm),
                    style = typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.database.routineDao().clearAll()
                            app.database.attendanceDao().clearAll()
                            app.database.resultDao().clearAll()
                            app.database.noticeDao().clearAll()
                            app.database.taskDao().clearAll()
                            app.database.capturedPageDao().clearAll()
                            prefsRepo.clearSettings()
                            Toast.makeText(context, context.getString(R.string.settings_clear_data_success), Toast.LENGTH_SHORT).show()
                            showClearDataDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.statusDanger,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    AppBackground(modifier = modifier.testTag("settings_screen")) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = typography.screenTitle,
                            color = colors.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        titleContentColor = colors.textPrimary,
                        navigationIconContentColor = colors.textPrimary
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Portal Base URL
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_portal_url),
                                style = typography.sectionTitle,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UniTrack restricts webview browsing strictly to this portal domain for security.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_portal_url_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        urlInput = UserPreferencesRepository.DEFAULT_PORTAL_URL
                                        scope.launch { prefsRepo.updatePortalUrl(urlInput) }
                                    },
                                    border = BorderStroke(1.dp, colors.cardBorder)
                                ) {
                                    Text("Reset Default", color = colors.textSecondary)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            prefsRepo.updatePortalUrl(urlInput)
                                            Toast.makeText(context, "Portal URL saved", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentCyan,
                                        contentColor = colors.background
                                    )
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Sync Page URLs: Routine & Attendance
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Offline Sync Page Addresses",
                                style = typography.sectionTitle,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Auto-saved when you tap 'Capture' on the Routine or Attendance page in Portal. You can also paste them directly below.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Routine URL
                            OutlinedTextField(
                                value = routineUrlInput,
                                onValueChange = { routineUrlInput = it },
                                label = { Text("Routine Page URL") },
                                placeholder = { Text("e.g. https://portal.aiub.edu/Student/Routine") },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = colors.accentCyan) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Attendance URL
                            OutlinedTextField(
                                value = attendanceUrlInput,
                                onValueChange = { attendanceUrlInput = it },
                                label = { Text("Attendance Page URL") },
                                placeholder = { Text("e.g. https://portal.aiub.edu/Student/Attendance") },
                                leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = colors.accentCyan) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            prefsRepo.updateRoutineUrl(routineUrlInput.trim())
                                            prefsRepo.updateAttendanceUrl(attendanceUrlInput.trim())
                                            Toast.makeText(context, "Sync URLs saved", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentCyan,
                                        contentColor = colors.background
                                    )
                                ) {
                                    Text("Save Addresses", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Reminders Settings (Master switch & default offsets)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "All Reminders",
                                        style = typography.sectionTitle,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Enable or mute all notifications and alarm triggers",
                                        style = typography.meta,
                                        color = colors.textSecondary
                                    )
                                }

                                Switch(
                                    checked = userPrefs.allRemindersEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            prefsRepo.updateAllRemindersEnabled(checked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.background,
                                        checkedTrackColor = colors.accentCyan,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.surfaceField
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "DEFAULT REMINDER OFFSETS",
                                style = typography.sectionHeaderLabel,
                                color = colors.sand
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val currentOffsets = userPrefs.defaultReminderOffsets.split(",")
                                .mapNotNull { it.trim().toIntOrNull() }.toSet()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    1440 to "1 day",
                                    180 to "3 hours",
                                    120 to "2 hours",
                                    60 to "1 hour"
                                ).forEach { (offset, label) ->
                                    val isSelected = currentOffsets.contains(offset)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newSet = if (isSelected) currentOffsets - offset else currentOffsets + offset
                                            scope.launch {
                                                prefsRepo.updateDefaultReminderOffsets(newSet.sortedDescending().joinToString(","))
                                            }
                                        },
                                        label = { Text(label, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(999.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceField,
                                            labelColor = colors.textSecondary,
                                            selectedContainerColor = colors.accentCyan.copy(alpha = 0.15f),
                                            selectedLabelColor = colors.accentCyan
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (isSelected) colors.accentCyan else colors.cardBorder,
                                            selectedBorderColor = colors.accentCyan,
                                            borderWidth = 1.dp,
                                            selectedBorderWidth = 1.dp,
                                            enabled = true,
                                            selected = isSelected
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Attendance Warning Threshold
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Attendance Threshold",
                                    style = typography.sectionTitle,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${thresholdSlider.toInt()}%",
                                    style = typography.sectionTitle,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "AIUB requires 80% minimum attendance to sit for semester final examinations.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = thresholdSlider,
                                onValueChange = { thresholdSlider = it },
                                onValueChangeFinished = {
                                    scope.launch {
                                        prefsRepo.updateAttendanceThreshold(thresholdSlider.toInt())
                                    }
                                },
                                valueRange = 60f..95f,
                                steps = 6,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentCyan,
                                    activeTrackColor = colors.accentCyan,
                                    inactiveTrackColor = colors.surfaceField
                                ),
                                modifier = Modifier.testTag("attendance_threshold_slider")
                            )
                        }
                    }
                }

                // Diagnostics & Debug Logs
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = colors.accentCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Diagnostics & System Logs",
                                    style = typography.sectionTitle,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Inspect real-time logs for WebView events, JS extraction, parser execution, and WorkManager sync worker.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToDebugLogs,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.surfaceField,
                                    contentColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Text("Open In-App Debug Log")
                            }
                        }
                    }
                }

                // Language Toggle
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = colors.sand)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_language),
                                    style = typography.sectionTitle,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = userPrefs.language == "en",
                                    onClick = { scope.launch { prefsRepo.updateLanguage("en") } },
                                    label = { Text("English") },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceField,
                                        labelColor = colors.textSecondary,
                                        selectedContainerColor = colors.accentCyan.copy(alpha = 0.15f),
                                        selectedLabelColor = colors.accentCyan
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (userPrefs.language == "en") colors.accentCyan else colors.cardBorder,
                                        selectedBorderColor = colors.accentCyan,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp,
                                        enabled = true,
                                        selected = userPrefs.language == "en"
                                    ),
                                    modifier = Modifier.testTag("language_chip_en")
                                )
                                FilterChip(
                                    selected = userPrefs.language == "bn",
                                    onClick = { scope.launch { prefsRepo.updateLanguage("bn") } },
                                    label = { Text("বাংলা (Bangla)") },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceField,
                                        labelColor = colors.textSecondary,
                                        selectedContainerColor = colors.accentCyan.copy(alpha = 0.15f),
                                        selectedLabelColor = colors.accentCyan
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (userPrefs.language == "bn") colors.accentCyan else colors.cardBorder,
                                        selectedBorderColor = colors.accentCyan,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp,
                                        enabled = true,
                                        selected = userPrefs.language == "bn"
                                    ),
                                    modifier = Modifier.testTag("language_chip_bn")
                                )
                            }
                        }
                    }
                }

                // Developer & Design System
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardFill),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = colors.accentCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Developer & Design System",
                                    style = typography.sectionTitle,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Inspect glassmorphic tokens, pills, card variants, and diagnose background parser events.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onNavigateToDesignSystem,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentCyan,
                                        contentColor = colors.background
                                    ),
                                    modifier = Modifier.testTag("open_design_system_button")
                                ) {
                                    Text("Design System Preview", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = onNavigateToDebugLogs,
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier.testTag("open_debug_logs_button")
                                ) {
                                    Text("Debug Logs", color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }

                // Clear Cache & Reset
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.statusDanger.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, colors.statusDanger.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = colors.statusDanger)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_clear_data),
                                    style = typography.sectionTitle,
                                    color = colors.statusDanger
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Deletes all cached routine timetable, captured portal HTML snapshots, and preferences from this device.",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showClearDataDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.statusDanger,
                                    contentColor = colors.textPrimary
                                ),
                                modifier = Modifier.testTag("clear_local_data_button")
                            ) {
                                Text("Clear All Local Data", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
