package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.UniTrackApplication
import com.example.data.local.entity.CourseItemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UniTrackAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        UniTrackLogger.i("UniTrack: AlarmReceiver fired for action=$action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarmIntent(context, intent, action)
            } catch (e: Exception) {
                UniTrackLogger.e("UniTrack: Error in AlarmReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarmIntent(context: Context, intent: Intent, action: String) {
        ensureNotificationChannel(context)

        when (action) {
            ACTION_TEST_NOW -> {
                showNotification(
                    context,
                    id = 9991,
                    title = "UniTracker Test Notification",
                    text = "Alarms and notifications are working properly!",
                    target = NotificationNav.TARGET_TODAY
                )
                UniTrackLogger.i("UniTrack: Posted instant test notification")
            }

            ACTION_TEST_IN_1_MIN -> {
                showNotification(
                    context,
                    id = 9992,
                    title = "UniTracker 1-Minute Test",
                    text = "Exact Alarm fired successfully while app was backgrounded/closed!",
                    target = NotificationNav.TARGET_TODAY
                )
                UniTrackLogger.i("UniTrack: Posted 1-minute exact alarm test notification")
            }

            ACTION_CLASS_REMINDER -> {
                val courseName = intent.getStringExtra("COURSE_NAME") ?: "Class"
                val section = intent.getStringExtra("SECTION") ?: ""
                val room = intent.getStringExtra("ROOM") ?: ""
                val startTime = intent.getStringExtra("START_TIME") ?: ""
                val courseKey = intent.getStringExtra("COURSE_KEY") ?: ""

                val app = context.applicationContext as UniTrackApplication
                val prefs = app.preferencesRepository.userPreferencesFlow.first()

                if (!prefs.allRemindersEnabled) {
                    UniTrackLogger.i("UniTrack: Skip class reminder for '$courseName' - all reminders muted")
                    return
                }

                val setting = app.database.courseSettingDao().getSettingDirect(courseKey)
                if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) {
                    UniTrackLogger.i("UniTrack: Skip class reminder for '$courseName' - course muted/dropped")
                    return
                }

                val timeFormatted = if (startTime.isNotBlank()) DateTimeUtils.formatTime12h(startTime) else ""
                val roomFormatted = if (room.isNotBlank()) " in Room $room" else ""
                val contentText = "Class starting soon at $timeFormatted$roomFormatted"

                showNotification(
                    context,
                    id = ("CLASS_$courseKey").hashCode(),
                    title = "$courseName [$section]",
                    text = contentText,
                    target = NotificationNav.TARGET_COURSE,
                    courseKey = courseKey
                )
                UniTrackLogger.i("UniTrack: Fired class reminder for $courseName at $timeFormatted")
            }

            ACTION_ITEM_REMINDER -> {
                val itemId = intent.getLongExtra("ITEM_ID", -1L)
                val offsetMinutes = intent.getIntExtra("OFFSET_MINUTES", 0)
                if (itemId <= 0L) return

                val app = context.applicationContext as UniTrackApplication
                val prefs = app.preferencesRepository.userPreferencesFlow.first()

                if (!prefs.allRemindersEnabled) {
                    UniTrackLogger.i("UniTrack: Skip item reminder #$itemId - all reminders muted")
                    return
                }

                val item = app.database.courseItemDao().getItemById(itemId)
                if (item == null || item.status != CourseItemStatus.TODO) {
                    UniTrackLogger.i("UniTrack: Skip item reminder #$itemId - item missing or done")
                    return
                }

                val setting = app.database.courseSettingDao().getSettingDirect(item.courseKey)
                if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) {
                    UniTrackLogger.i("UniTrack: Skip item reminder for '${item.title}' - course muted/dropped")
                    return
                }

                val dueBadge = DateTimeUtils.formatDueBadgeText(item.dueAt, item.hasTime)
                val timeNotice = if (item.hasTime) " at ${dueBadge.formattedDateTime}" else " (${dueBadge.label})"

                val offsetDesc = when (offsetMinutes) {
                    1440 -> "Tomorrow"
                    180 -> "In 3 hours"
                    120 -> "In 2 hours"
                    60 -> "In 1 hour"
                    else -> "Reminder"
                }

                val title = "${item.courseName} · ${item.title}"
                val content = "$offsetDesc: Due$timeNotice"

                showNotification(
                    context,
                    id = (itemId * 1000 + offsetMinutes).toInt(),
                    title = title,
                    text = content,
                    target = NotificationNav.TARGET_COURSE,
                    courseKey = item.courseKey,
                    itemId = item.id
                )
                UniTrackLogger.i("UniTrack: Fired deadline reminder for ${item.title}")
            }
        }
    }

    private fun showNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
        target: String = NotificationNav.TARGET_TODAY,
        courseKey: String? = null,
        itemId: Long? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel(context)

        val contentIntent = NotificationNav.buildContentIntent(context, id, target, courseKey, itemId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        notificationManager.notify(id, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "unitrack_exact_reminders_channel"
        const val ACTION_TEST_NOW = "com.example.action.TEST_NOW"
        const val ACTION_TEST_IN_1_MIN = "com.example.action.TEST_IN_1_MIN"
        const val ACTION_CLASS_REMINDER = "com.example.action.CLASS_REMINDER"
        const val ACTION_ITEM_REMINDER = "com.example.action.ITEM_REMINDER"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "UniTracker Class & Deadline Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Exact alarms and reminders for classes, tests, and deadlines"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
