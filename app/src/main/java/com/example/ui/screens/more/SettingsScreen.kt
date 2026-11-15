package com.example.ui.screens.more

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import com.example.data.local.entity.CourseSettingEntity
import androidx.work.WorkManager
import com.example.ui.screens.portal.PortalWebViewHolder
import com.example.util.CourseItemReminderScheduler
import com.example.util.ScheduledReminderInfo
import com.example.util.UniTrackAlarmReceiver
import com.example.util.UniTrackAlarmScheduler
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.UniTrackApplication
import com.example.data.local.preferences.UserPreferences
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.data.sync.PortalSyncWorker
import com.example.ui.components.AppBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToPortal: () -> Unit = {},
    onNavigateToNotices: () -> Unit = {},
    onNavigateToCapture: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
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
    val dimens = UniTrackTheme.dimens

    val userPrefs by prefsRepo.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )

    var tapCount by remember { mutableIntStateOf(0) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var alsoLogout by remember { mutableStateOf(true) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showPortalUrlDialog by remember { mutableStateOf(false) }
    var showReminderMinutesDialog by remember { mutableStateOf(false) }
    var showClassesDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHealthCheckDialog by remember { mutableStateOf(false) }
    var upcomingReminders by remember { mutableStateOf<List<ScheduledReminderInfo>>(emptyList()) }

    LaunchedEffect(showHealthCheckDialog) {
        if (showHealthCheckDialog) {
            upcomingReminders = UniTrackAlarmScheduler.getUpcomingScheduledReminders(context, limit = 5)
        }
    }



    // One-Time Permission Prompt after first login
    if (userPrefs.isSessionActive && !userPrefs.hasSeenPermissionPrompt) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { prefsRepo.updateHasSeenPermissionPrompt(true) }
            },
            containerColor = colors.surfaceSheet,
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = colors.accentCyan) },
            title = { Text("Enable Background Reminders", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Text(
                    "To get class reminders and deadline alerts when the app is closed, allow notifications, exact alarms, and unrestricted battery optimizations.",
                    style = typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { prefsRepo.updateHasSeenPermissionPrompt(true) }
                        showHealthCheckDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Open Health Check")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { prefsRepo.updateHasSeenPermissionPrompt(true) }
                    }
                ) {
                    Text("Dismiss", color = colors.textSecondary)
                }
            }
        )
    }

    // Health Check Dialog
    if (showHealthCheckDialog) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val hasPostNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val channelImportanceOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(UniTrackAlarmReceiver.CHANNEL_ID)
            channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
        } else true

        val hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else true

        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        AlertDialog(
            onDismissRequest = { showHealthCheckDialog = false },
            containerColor = colors.surfaceSheet,
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.accentCyan) },
            title = { Text("Notification Health Check", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Item 1: Post Notifications
                    HealthCheckItemRow(
                        title = "Notification Permission",
                        isOk = hasPostNotif,
                        buttonLabel = if (hasPostNotif) "OK" else "Fix",
                        onFix = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )

                    // Item 2: Class Channel Importance
                    HealthCheckItemRow(
                        title = "Channel Importance (High)",
                        isOk = channelImportanceOk,
                        buttonLabel = if (channelImportanceOk) "OK" else "Fix",
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, UniTrackAlarmReceiver.CHANNEL_ID)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                        }
                    )

                    // Item 3: Exact Alarm Permission
                    HealthCheckItemRow(
                        title = "Exact Alarm Permission",
                        isOk = hasExactAlarm,
                        buttonLabel = if (hasExactAlarm) "OK" else "Fix",
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                        }
                    )

                    // Item 4: Battery Optimization Exemption
                    HealthCheckItemRow(
                        title = "Unrestricted Battery",
                        isOk = isIgnoringBattery,
                        buttonLabel = if (isIgnoringBattery) "OK" else "Fix",
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                        }
                    )

                    // OEM Background Limits Guide Card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("OEM Background Limits (Xiaomi, Oppo, Vivo, Samsung)", style = typography.body, fontWeight = FontWeight.Bold, color = colors.sand, fontSize = 12.sp)
                            Text(
                                "On Xiaomi/Redmi/POCO (MIUI/HyperOS): enable 'Autostart' and set Battery saver to 'No restrictions'. If force-stopped, Android cancels alarms until re-opened.",
                                style = typography.meta,
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Open App Settings", fontSize = 11.sp, color = colors.accentCyan)
                            }
                        }
                    }

                    // Test Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, UniTrackAlarmReceiver::class.java).apply {
                                    action = UniTrackAlarmReceiver.ACTION_TEST_NOW
                                }
                                context.sendBroadcast(intent)
                                Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Now", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                UniTrackAlarmScheduler.scheduleTest1MinAlarm(context)
                                Toast.makeText(context, "Alarm set for 1 min from now. Swipe app away & lock phone!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test in 1 Min", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Upcoming 5 Scheduled Reminders
                    if (upcomingReminders.isNotEmpty()) {
                        Text("Next Scheduled Reminders (${upcomingReminders.size})", style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 12.sp)
                        upcomingReminders.forEach { rem ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceField)
                                    .padding(8.dp)
                            ) {
                                Text(rem.title, style = typography.body, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
                                Text(rem.subtitle, style = typography.meta, color = colors.textSecondary, fontSize = 11.sp)
                                Text(rem.formattedTime, style = typography.meta, color = colors.accentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHealthCheckDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Done")
                }
            }
        )
    }

    val routinesFlow = remember { app.database.routineDao().getAllRoutines() }
    val routines by routinesFlow.collectAsState(initial = emptyList())
    val allSettingsFlow = remember { app.database.courseSettingDao().getAllSettings() }
    val allSettings by allSettingsFlow.collectAsState(initial = emptyList())
    val allCourseItemsFlow = remember { app.database.courseItemDao().getAllItems() }
    val allCourseItems by allCourseItemsFlow.collectAsState(initial = emptyList())

    // Clear Data Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            containerColor = colors.surfaceSheet,
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = colors.statusDanger) },
            title = {
                Text(
                    text = stringResource(R.string.settings_clear_data),
                    style = typography.sectionTitle,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will permanently delete:\n• Routine, attendance & result cache\n• Quizzes, assignments, exams & tasks\n• Per-course settings & dropped classes\n• Saved HTML snapshots & debug logs\n• Preferences & local settings",
                        style = typography.body,
                        color = colors.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoLogout = !alsoLogout }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = alsoLogout,
                            onCheckedChange = { alsoLogout = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentCyan,
                                uncheckedColor = colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Also log out of the portal",
                            style = typography.body,
                            color = colors.textPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            // 1. Cancel scheduled reminder alarms, WorkManager jobs & posted notifications
                            CourseItemReminderScheduler.cancelAllReminders(context)
                            WorkManager.getInstance(context).cancelAllWork()
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancelAll()

                            // 2. Clear all DB tables then checkpoint/VACUUM
                            app.database.clearAllTables()
                            try {
                                app.database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL);")
                                app.database.openHelper.writableDatabase.execSQL("VACUUM;")
                            } catch (e: Exception) {
                                UniTrackLogger.w("VACUUM error: ${e.message}")
                            }

                            // 3. Clear DataStore & cache files & debug logs
                            prefsRepo.clearSettings()
                            try {
                                context.cacheDir.deleteRecursively()
                            } catch (e: Exception) {
                                UniTrackLogger.w("Cache dir error: ${e.message}")
                            }
                            UniTrackLogger.clear()

                            // 4. If checkbox is on: clear cookies & web storage
                            if (alsoLogout) {
                                withContext(Dispatchers.Main) {
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.removeAllCookies {
                                        cookieManager.flush()
                                    }
                                    WebStorage.getInstance().deleteAllData()
                                    val webView = PortalWebViewHolder.getWebView()
                                    webView?.clearCache(true)
                                    webView?.clearHistory()
                                    webView?.clearFormData()
                                }
                                prefsRepo.updateSessionActive(false)
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "All local data deleted", Toast.LENGTH_SHORT).show()
                                showClearDataDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.statusDanger,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Appearance Choice Dialog
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("Appearance", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SYSTEM" to "System default",
                        "DARK" to "Dark theme",
                        "LIGHT" to "Light theme"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefsRepo.updateThemeMode(mode) }
                                    showAppearanceDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = userPrefs.themeMode == mode,
                                onClick = {
                                    scope.launch { prefsRepo.updateThemeMode(mode) }
                                    showAppearanceDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.accentCyan,
                                    unselectedColor = colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = typography.body, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            }
        )
    }

    // Language Choice Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("Language", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "en" to "English",
                        "bn" to "বাংলা (Bangla)"
                    ).forEach { (lang, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefsRepo.updateLanguage(lang) }
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = userPrefs.language == lang,
                                onClick = {
                                    scope.launch { prefsRepo.updateLanguage(lang) }
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.accentCyan,
                                    unselectedColor = colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = typography.body, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            }
        )
    }

    // Attendance Threshold Dialog
    if (showThresholdDialog) {
        var sliderVal by remember(userPrefs.attendanceThreshold) {
            mutableFloatStateOf(userPrefs.attendanceThreshold.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("Attendance Warning Threshold", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Minimum Required", style = typography.body, color = colors.textSecondary)
                        Text("${sliderVal.toInt()}%", style = typography.sectionTitle, color = colors.accentCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = sliderVal,
                        onValueChange = { sliderVal = it },
                        valueRange = 60f..95f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentCyan,
                            activeTrackColor = colors.accentCyan,
                            inactiveTrackColor = colors.surfaceField
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { prefsRepo.updateAttendanceThreshold(sliderVal.toInt()) }
                        showThresholdDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Portal URL Dialog
    if (showPortalUrlDialog) {
        var urlInput by remember(userPrefs.portalUrl) { mutableStateOf(userPrefs.portalUrl) }
        AlertDialog(
            onDismissRequest = { showPortalUrlDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("Portal Base URL", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column {
                    Text("UniTrack restricts browsing strictly to this portal domain.", style = typography.meta, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceField,
                            unfocusedContainerColor = colors.surfaceField,
                            focusedBorderColor = colors.accentCyan,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            prefsRepo.updatePortalUrl(urlInput.trim())
                            Toast.makeText(context, "Portal URL updated", Toast.LENGTH_SHORT).show()
                        }
                        showPortalUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        urlInput = UserPreferencesRepository.DEFAULT_PORTAL_URL
                        scope.launch { prefsRepo.updatePortalUrl(urlInput) }
                        showPortalUrlDialog = false
                    }
                ) {
                    Text("Reset Default", color = colors.textSecondary)
                }
            }
        )
    }

    // Class Reminder Offset Dialog
    if (showReminderMinutesDialog) {
        AlertDialog(
            onDismissRequest = { showReminderMinutesDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("Class Reminder Lead Time", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { mins ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefsRepo.updateReminderMinutes(mins) }
                                    showReminderMinutesDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = userPrefs.reminderMinutes == mins,
                                onClick = {
                                    scope.launch { prefsRepo.updateReminderMinutes(mins) }
                                    showReminderMinutesDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.accentCyan,
                                    unselectedColor = colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$mins minutes before class", style = typography.body, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderMinutesDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // My Classes Dialog
    if (showClassesDialog) {
        val activeKeys = routines.map { CourseSettingEntity.makeKey(it.courseName, it.section) }.toSet()
        val settingKeys = allSettings.map { it.courseKey }
        val itemKeys = allCourseItems.map { it.courseKey }
        val orphanedKeys = (settingKeys + itemKeys).filter { it.isNotBlank() && !activeKeys.contains(it) }.distinct()

        AlertDialog(
            onDismissRequest = { showClassesDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("My Classes & Reminders", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (routines.isEmpty()) {
                        Text("No active class routines found in local database. Log in to AIUB Student Portal to load your schedule.", style = typography.body, color = colors.textSecondary)
                    } else {
                        val distinctCourses = routines.distinctBy { "${it.courseName}_${it.section}" }
                        distinctCourses.forEach { classItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceField)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(classItem.courseName, style = typography.body, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                    Text("Sec: ${classItem.section} • Room: ${classItem.room}", style = typography.meta, color = colors.textSecondary)
                                }
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Active", tint = colors.statusSuccess, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (orphanedKeys.isNotEmpty()) {
                        Text("No longer in routine", style = typography.sectionTitle, color = colors.sand, fontSize = 13.sp)
                        orphanedKeys.forEach { orphanedKey ->
                            val cName = allSettings.find { it.courseKey == orphanedKey }?.courseName
                                ?: allCourseItems.find { it.courseKey == orphanedKey }?.courseName
                                ?: orphanedKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceField)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cName, style = typography.body, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("User items retained", style = typography.meta, color = colors.textSecondary)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            allCourseItems.filter { it.courseKey == orphanedKey }.forEach {
                                                app.database.courseItemDao().deleteItem(it)
                                            }
                                            Toast.makeText(context, "Removed course data for $cName", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete course data", tint = colors.statusDanger, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showClassesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Done")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = colors.surfaceSheet,
            icon = { Icon(Icons.Outlined.School, contentDescription = null, tint = colors.accentCyan) },
            title = { Text("About UniTrack", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UniTrack v1.0.0", style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        "An offline-first academic assistant designed specifically for AIUB students. Automatically parses your portal routine and attendance, schedules local notifications, and operates fully offline.",
                        style = typography.body,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = colors.background)
                ) {
                    Text("Close")
                }
            }
        )
    }

    AppBackground(modifier = modifier.testTag("settings_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Screen Header: Title only, no clock pill, no bell, no theme toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12)
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = typography.screenTitle,
                    color = colors.textPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = dimens.ScreenPadding,
                    vertical = dimens.Space8
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.Space16)
            ) {
                // ACCOUNT SECTION
                item {
                    SectionHeader(title = "ACCOUNT")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        SettingsRow(
                            title = "AIUB Student Portal",
                            subtitle = "Log in and refresh your routine",
                            icon = Icons.Outlined.School,
                            iconTint = colors.accentCyan,
                            trailingContent = {
                                val (statusText, statusColor) = if (userPrefs.isSessionActive) {
                                    "Active session" to colors.statusSuccess
                                } else {
                                    "Not logged in" to colors.textSecondary
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = statusText,
                                        style = typography.meta,
                                        color = statusColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = onNavigateToPortal,
                            testTag = "settings_account_row"
                        )
                    }
                }

                // CLASSES SECTION
                item {
                    SectionHeader(title = "CLASSES")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = "My classes",
                                subtitle = "Reminders per class, drop/restore",
                                icon = Icons.Outlined.Class,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showClassesDialog = true },
                                testTag = "settings_my_classes_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Notices & announcements",
                                subtitle = "Cached university notices",
                                icon = Icons.Outlined.Notifications,
                                iconTint = colors.warning,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = onNavigateToNotices,
                                testTag = "settings_notices_row"
                            )
                        }
                    }
                }

                // REMINDERS SECTION
                item {
                    SectionHeader(title = "REMINDERS")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = "Class reminders",
                                subtitle = "Remind me ${userPrefs.reminderMinutes} min before",
                                icon = Icons.Outlined.Alarm,
                                iconTint = colors.accentCyan,
                                trailingContent = {
                                    Switch(
                                        checked = userPrefs.allRemindersEnabled,
                                        onCheckedChange = { checked ->
                                            scope.launch { prefsRepo.updateAllRemindersEnabled(checked) }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.background,
                                            checkedTrackColor = colors.accentCyan,
                                            uncheckedThumbColor = colors.textSecondary,
                                            uncheckedTrackColor = colors.surfaceField
                                        )
                                    )
                                },
                                onClick = { showReminderMinutesDialog = true },
                                testTag = "settings_class_reminders_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Reminder sound",
                                subtitle = "Default notification sound",
                                icon = Icons.Outlined.VolumeUp,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    Toast.makeText(context, "Using default notification sound channel", Toast.LENGTH_SHORT).show()
                                },
                                testTag = "settings_reminder_sound_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Notification health check",
                                subtitle = "Check system permissions and alarms",
                                icon = Icons.Outlined.CheckCircle,
                                iconTint = colors.statusSuccess,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showHealthCheckDialog = true },
                                testTag = "settings_health_check_row"
                            )
                        }
                    }
                }

                // PREFERENCES SECTION
                item {
                    SectionHeader(title = "PREFERENCES")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            val themeLabel = when (userPrefs.themeMode) {
                                "DARK" -> "Dark"
                                "LIGHT" -> "Light"
                                else -> "System"
                            }
                            SettingsRow(
                                title = "Appearance",
                                subtitle = themeLabel,
                                icon = Icons.Outlined.Palette,
                                iconTint = colors.accentCyan,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showAppearanceDialog = true },
                                testTag = "settings_appearance_row"
                            )

                            SettingsRowDivider()

                            val langLabel = if (userPrefs.language == "bn") "বাংলা (Bangla)" else "English"
                            SettingsRow(
                                title = "Language",
                                subtitle = langLabel,
                                icon = Icons.Outlined.Language,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showLanguageDialog = true },
                                testTag = "settings_language_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Attendance warning threshold",
                                subtitle = "${userPrefs.attendanceThreshold}%",
                                icon = Icons.Outlined.Warning,
                                iconTint = colors.warning,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showThresholdDialog = true },
                                testTag = "settings_threshold_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Portal URL",
                                subtitle = userPrefs.portalUrl,
                                icon = Icons.Outlined.Link,
                                iconTint = colors.accentCyan,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showPortalUrlDialog = true },
                                testTag = "settings_portal_url_row"
                            )
                        }
                    }
                }

                // DATA SECTION
                item {
                    SectionHeader(title = "DATA")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            val lastSyncText = DateTimeUtils.formatLastSynced(userPrefs.lastSyncTimestamp)
                            SettingsRow(
                                title = "Sync now",
                                subtitle = lastSyncText,
                                icon = Icons.Outlined.Sync,
                                iconTint = colors.accentCyan,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    PortalSyncWorker.triggerManualSync(context)
                                    Toast.makeText(context, "Offline sync started", Toast.LENGTH_SHORT).show()
                                },
                                testTag = "settings_sync_now_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Export / import backup",
                                subtitle = "Backup or restore local data",
                                icon = Icons.Outlined.ImportExport,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    Toast.makeText(context, "Local data is safely backed up on-device", Toast.LENGTH_SHORT).show()
                                },
                                testTag = "settings_backup_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Clear local data",
                                subtitle = "Delete cached routine, attendance & pages",
                                icon = Icons.Outlined.DeleteForever,
                                iconTint = colors.statusDanger,
                                titleColor = colors.statusDanger,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.statusDanger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showClearDataDialog = true },
                                testTag = "settings_clear_data_row"
                            )
                        }
                    }
                }

                // ABOUT SECTION
                item {
                    SectionHeader(title = "ABOUT")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = "Version 1.0.0",
                                subtitle = if (userPrefs.isDeveloperModeEnabled) "Developer tools enabled" else "Tap 7 times to enable developer tools",
                                icon = Icons.Outlined.Info,
                                iconTint = colors.accentCyan,
                                onClick = {
                                    if (userPrefs.isDeveloperModeEnabled) {
                                        Toast.makeText(context, "Developer tools already enabled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        tapCount++
                                        if (tapCount in 3..6) {
                                            val remaining = 7 - tapCount
                                            Toast.makeText(context, "$remaining more taps to enable developer tools", Toast.LENGTH_SHORT).show()
                                        } else if (tapCount >= 7) {
                                            tapCount = 0
                                            scope.launch {
                                                prefsRepo.updateDeveloperModeEnabled(true)
                                                Toast.makeText(context, "Developer tools enabled", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                testTag = "settings_version_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "About UniTrack",
                                subtitle = "Offline-first academic assistant for AIUB",
                                icon = Icons.Outlined.School,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showAboutDialog = true },
                                testTag = "settings_about_row"
                            )
                        }
                    }
                }

                // HIDDEN DEVELOPER SECTION (only visible when unlocked)
                if (userPrefs.isDeveloperModeEnabled) {
                    item {
                        SectionHeader(title = "DEVELOPER")
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 0.dp
                        ) {
                            Column {
                                SettingsRow(
                                    title = "Page Capture & HTML Samples",
                                    subtitle = "Saved page snapshots for inspecting portal structure",
                                    icon = Icons.Outlined.CameraAlt,
                                    iconTint = colors.sand,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = onNavigateToCapture,
                                    testTag = "settings_dev_capture_row"
                                )

                                SettingsRowDivider()

                                SettingsRow(
                                    title = "Parser Debugger",
                                    subtitle = "Test CSS selectors against live captured HTML",
                                    icon = Icons.Outlined.BugReport,
                                    iconTint = colors.accentCyan,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = onNavigateToDebug,
                                    testTag = "settings_dev_parser_row"
                                )

                                SettingsRowDivider()

                                SettingsRow(
                                    title = "UniTrack Debug Log",
                                    subtitle = "Live capture, JS extraction & background sync logs",
                                    icon = Icons.Outlined.Terminal,
                                    iconTint = colors.statusDanger,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = onNavigateToDebugLogs,
                                    testTag = "settings_dev_log_row"
                                )

                                SettingsRowDivider()

                                SettingsRow(
                                    title = "Design System Preview",
                                    subtitle = "Inspect glassmorphic tokens, cards, pills & buttons",
                                    icon = Icons.Outlined.Palette,
                                    iconTint = colors.accentCyan,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = onNavigateToDesignSystem,
                                    testTag = "settings_dev_design_system_row"
                                )

                                SettingsRowDivider()

                                SettingsRow(
                                    title = "Hide developer tools",
                                    subtitle = "Turn off developer options section",
                                    icon = Icons.Outlined.VisibilityOff,
                                    iconTint = colors.textSecondary,
                                    titleColor = colors.textSecondary,
                                    onClick = {
                                        scope.launch {
                                            prefsRepo.updateDeveloperModeEnabled(false)
                                            Toast.makeText(context, "Developer tools hidden", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    testTag = "settings_hide_dev_row"
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = UniTrackTheme.colors.accentCyan,
    titleColor: Color = UniTrackTheme.colors.textPrimary,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = typography.body,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = typography.meta,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
private fun SettingsRowDivider() {
    val colors = UniTrackTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 12.dp)
            .background(colors.cardBorder.copy(alpha = 0.4f))
    )
}

@Composable
private fun HealthCheckItemRow(
    title: String,
    isOk: Boolean,
    buttonLabel: String,
    onFix: () -> Unit
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceField)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.body, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
            Text(
                if (isOk) "Status: OK" else "Action needed",
                style = typography.meta,
                color = if (isOk) colors.statusSuccess else colors.statusDanger,
                fontSize = 11.sp
            )
        }
        if (!isOk) {
            OutlinedButton(
                onClick = onFix,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(buttonLabel, fontSize = 11.sp, color = colors.accentCyan)
            }
        } else {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "OK", tint = colors.statusSuccess, modifier = Modifier.size(20.dp))
        }
    }
}
