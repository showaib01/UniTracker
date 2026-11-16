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
import com.example.ui.components.SettingsRow
import com.example.ui.components.SettingsRowDivider
import com.example.ui.screens.portal.PortalWebViewHolder
import com.example.util.CourseItemReminderScheduler
import com.example.util.ScheduledReminderInfo
import com.example.util.UniTrackAlarmReceiver
import com.example.util.UniTrackAlarmScheduler
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
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
import com.example.data.auth.CredentialStore
import com.example.data.local.preferences.UserPreferences
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.ui.components.AppBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToPortal: () -> Unit = {},
    onNavigateToNotices: () -> Unit = {},
    onNavigateToCourseMaterials: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
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

    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showReminderMinutesDialog by remember { mutableStateOf(false) }
    var showClassesDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHealthCheckDialog by remember { mutableStateOf(false) }
    var showForgetLoginDialog by remember { mutableStateOf(false) }

    val credentialStore = remember { CredentialStore(context) }
    var hasCreds by remember { mutableStateOf(credentialStore.hasCredentials()) }

    val versionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    var upcomingReminders by remember { mutableStateOf<List<ScheduledReminderInfo>>(emptyList()) }

    LaunchedEffect(showHealthCheckDialog) {
        if (showHealthCheckDialog) {
            upcomingReminders = UniTrackAlarmScheduler.getUpcomingScheduledReminders(context, limit = 5)
        }
    }

    LaunchedEffect(userPrefs.isSessionActive) {
        hasCreds = credentialStore.hasCredentials()
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

    // Forget Saved Login Confirmation Dialog
    if (showForgetLoginDialog) {
        AlertDialog(
            onDismissRequest = { showForgetLoginDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text(stringResource(R.string.settings_forget_login), style = typography.sectionTitle, color = colors.textPrimary) },
            text = { Text(stringResource(R.string.settings_forget_login_confirm), style = typography.body, color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        credentialStore.clear()
                        CookieManager.getInstance().removeAllCookies(null)
                        scope.launch { prefsRepo.updateSessionActive(false) }
                        hasCreds = false
                        Toast.makeText(context, "Saved login forgotten & session ended", Toast.LENGTH_SHORT).show()
                        showForgetLoginDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusDanger, contentColor = colors.textPrimary)
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetLoginDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
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
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                        }
                    )

                    HealthCheckItemRow(
                        title = "Exact Alarm Permission",
                        isOk = hasExactAlarm,
                        buttonLabel = if (hasExactAlarm) "OK" else "Fix",
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                        }
                    )

                    HealthCheckItemRow(
                        title = "Unrestricted Battery",
                        isOk = isIgnoringBattery,
                        buttonLabel = if (isIgnoringBattery) "OK" else "Fix",
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                        }
                    )

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

    // Dialogs for Appearance, Language, Reminder Minutes, Classes, About
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text(stringResource(R.string.settings_theme), style = typography.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM" to "System default", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefsRepo.updateThemeMode(mode) }
                                    showAppearanceDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = userPrefs.themeMode == mode,
                                onClick = {
                                    scope.launch { prefsRepo.updateThemeMode(mode) }
                                    showAppearanceDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accentCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = typography.body, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Close", color = colors.accentCyan)
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language), style = typography.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = true,
                            onClick = { showLanguageDialog = false },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accentCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("English", style = typography.body, color = colors.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = colors.accentCyan)
                }
            }
        )
    }



    if (showReminderMinutesDialog) {
        AlertDialog(
            onDismissRequest = { showReminderMinutesDialog = false },
            title = { Text("Class reminder lead time", style = typography.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10 minutes before", 15 to "15 minutes before", 30 to "30 minutes before", 60 to "1 hour before").forEach { (mins, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        prefsRepo.updateReminderMinutes(mins)
                                        UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(context)
                                    }
                                    showReminderMinutesDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = userPrefs.reminderMinutes == mins,
                                onClick = {
                                    scope.launch {
                                        prefsRepo.updateReminderMinutes(mins)
                                        UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(context)
                                    }
                                    showReminderMinutesDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accentCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = typography.body, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderMinutesDialog = false }) {
                    Text("Close", color = colors.accentCyan)
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

    // My Classes Dialog
    if (showClassesDialog) {
        val courseKeysMap = routines.map { CourseSettingEntity.makeKey(it.courseName, it.section) }.toSet()
        val settingsMap = allSettings.associateBy { it.courseKey }

        AlertDialog(
            onDismissRequest = { showClassesDialog = false },
            containerColor = colors.surfaceSheet,
            title = { Text("My Classes & Reminders", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (courseKeysMap.isEmpty()) {
                        Text("No active class routines found in local database. Log in to AIUB Student Portal to load your schedule.", style = typography.body, color = colors.textSecondary)
                    } else {
                        courseKeysMap.forEach { key ->
                            val setting = settingsMap[key]
                            val sampleRoutine = routines.firstOrNull { CourseSettingEntity.makeKey(it.courseName, it.section) == key }
                            val cName = sampleRoutine?.courseName ?: key
                            val cSec = sampleRoutine?.section ?: ""
                            val isDropped = setting?.isDropped == true
                            val notifEnabled = setting?.notificationsEnabled != false

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceField)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("$cName [$cSec]", style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = notifEnabled,
                                            onCheckedChange = { checked ->
                                                scope.launch {
                                                    val existing = app.database.courseSettingDao().getSettingDirect(key)
                                                    if (existing != null) {
                                                        app.database.courseSettingDao().insertOrUpdate(existing.copy(notificationsEnabled = checked, updatedAt = System.currentTimeMillis()))
                                                    } else {
                                                        app.database.courseSettingDao().insertOrUpdate(CourseSettingEntity(courseKey = key, courseName = cName, section = cSec, notificationsEnabled = checked, updatedAt = System.currentTimeMillis()))
                                                    }
                                                    UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(context)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = colors.accentCyan)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reminders", style = typography.meta, color = colors.textSecondary)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isDropped) "Dropped" else "Active", style = typography.meta, color = if (isDropped) colors.statusDanger else colors.statusSuccess)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val itemsForCourse = allCourseItems.filter { it.courseKey == key }
                                                    itemsForCourse.forEach { item ->
                                                        app.database.courseItemDao().deleteItem(item)
                                                    }
                                                    val existing = app.database.courseSettingDao().getSettingDirect(key)
                                                    if (existing != null) {
                                                        app.database.courseSettingDao().insertOrUpdate(existing.copy(isDropped = !isDropped, updatedAt = System.currentTimeMillis()))
                                                    } else {
                                                        app.database.courseSettingDao().insertOrUpdate(CourseSettingEntity(courseKey = key, courseName = cName, section = cSec, isDropped = true, updatedAt = System.currentTimeMillis()))
                                                    }
                                                    UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(context)
                                                    Toast.makeText(context, "Updated course status", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Warning,
                                                contentDescription = "Drop/Restore",
                                                tint = if (isDropped) colors.statusSuccess else colors.statusDanger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
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
            title = { Text("About UniTrack", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UniTrack v1.2.0", style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("UniTrack is an unofficial, student-built independent academic assistant for American International University-Bangladesh (AIUB) and is not officially affiliated with AIUB.", style = typography.body, color = colors.textSecondary)
                    Text("Tracks and syncs your student portal class routines to be available completely offline. Features secure student portal login, OCR captcha solver, offline class routines tracking, course materials, and timely proactive reminders before upcoming classes and tasks.", style = typography.meta, color = colors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = colors.accentCyan)
                }
            }
        )
    }

    AppBackground(modifier = modifier.testTag("settings_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Sub-screen header if navigation back is available
            if (onNavigateBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp).testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(dimens.Space8))
                    Text(
                        text = "Settings & Tools",
                        style = typography.screenTitle,
                        color = colors.textPrimary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    horizontal = dimens.ScreenPadding,
                    vertical = dimens.Space16
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.Space24)
            ) {
                // ACCOUNT SECTION
                item {
                    SectionHeader(title = "ACCOUNT")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = "AIUB Student Portal",
                                subtitle = if (userPrefs.isSessionActive) "Active session" else "Not logged in",
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
                                onClick = onNavigateToPortal,
                                testTag = "settings_account_row"
                            )

                            if (hasCreds) {
                                SettingsRowDivider()

                                SettingsRow(
                                    title = stringResource(R.string.settings_forget_login),
                                    subtitle = stringResource(R.string.settings_forget_login_subtitle),
                                    icon = Icons.Outlined.LockReset,
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
                                    onClick = { showForgetLoginDialog = true },
                                    testTag = "settings_forget_login_row"
                                )
                            }
                        }
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
                                icon = Icons.Outlined.School,
                                iconTint = colors.accentCyan,
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

                // DOWNLOADS SECTION (Task 3.6)
                item {
                    SectionHeader(title = stringResource(R.string.settings_downloads_section))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = stringResource(R.string.settings_course_materials),
                                subtitle = stringResource(R.string.settings_course_materials_subtitle),
                                icon = Icons.Outlined.Folder,
                                iconTint = colors.accentCyan,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = onNavigateToCourseMaterials,
                                testTag = "settings_course_materials_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = stringResource(R.string.settings_my_downloads),
                                subtitle = stringResource(R.string.settings_my_downloads_subtitle),
                                icon = Icons.Outlined.FolderOpen,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = onNavigateToDownloads,
                                testTag = "settings_my_downloads_row"
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
                            SettingsRow(
                                title = "Theme",
                                subtitle = when (userPrefs.themeMode) {
                                    "LIGHT" -> "Light"
                                    "DARK" -> "Dark"
                                    else -> "System default"
                                },
                                icon = Icons.Outlined.Palette,
                                iconTint = colors.sand,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showAppearanceDialog = true },
                                testTag = "settings_theme_row"
                            )

                            SettingsRowDivider()

                            SettingsRow(
                                title = "Language",
                                subtitle = "English",
                                icon = Icons.Outlined.Language,
                                iconTint = colors.accentCyan,
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
                        }
                    }
                }

                // ABOUT SECTION (Task 4: plain info row without 7-tap)
                item {
                    SectionHeader(title = "ABOUT")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsRow(
                                title = "Version 1.0.0",
                                subtitle = "UniTrack",
                                icon = Icons.Outlined.Info,
                                iconTint = colors.accentCyan,
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

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
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
