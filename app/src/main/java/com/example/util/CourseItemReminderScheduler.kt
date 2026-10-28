package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.UniTrackApplication
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CourseItemReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getLong("ITEM_ID", -1L)
        val offsetMinutes = inputData.getInt("OFFSET_MINUTES", 0)
        if (itemId <= 0L) return@withContext Result.failure()

        val app = context.applicationContext as UniTrackApplication
        val prefs = app.preferencesRepository.userPreferencesFlow.first()

        // Respect global reminder master switch
        if (!prefs.allRemindersEnabled) {
            return@withContext Result.success()
        }

        val item = app.database.courseItemDao().getItemById(itemId) ?: return@withContext Result.success()

        // If item was deleted, dropped, or marked DONE, do not notify
        if (item.status != CourseItemStatus.TODO) {
            return@withContext Result.success()
        }

        // Respect per-course reminder switch
        val courseSetting = app.database.courseSettingDao().getSettingDirect(item.courseKey)
        if (courseSetting != null) {
            if (courseSetting.isDropped || !courseSetting.notificationsEnabled) {
                return@withContext Result.success()
            }
        }

        // Show notification
        showNotification(context, item, offsetMinutes)
        Result.success()
    }

    private fun showNotification(context: Context, item: CourseItemEntity, offsetMinutes: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "course_deadlines_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Quizzes, Assignments & Exams",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for course deadlines, tests, and exams"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n${item.topics.ifBlank { item.notes }}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify((item.id * 1000 + offsetMinutes).toInt(), builder.build())
    }
}

object CourseItemReminderScheduler {

    fun scheduleItemReminders(context: Context, item: CourseItemEntity) {
        cancelItemReminders(context, item.id)

        if (item.status != CourseItemStatus.TODO) return

        val now = System.currentTimeMillis()
        val offsets = item.getReminderOffsetsList()
        val workManager = WorkManager.getInstance(context)

        for (offset in offsets) {
            val triggerTime = item.dueAt - (offset * 60_000L)
            val delayMillis = triggerTime - now
            if (delayMillis > 0) {
                val inputData = Data.Builder()
                    .putLong("ITEM_ID", item.id)
                    .putInt("OFFSET_MINUTES", offset)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<CourseItemReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("reminder_item_${item.id}")
                    .addTag("reminder_item_${item.id}_${offset}")
                    .build()

                workManager.enqueueUniqueWork(
                    "work_reminder_${item.id}_${offset}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }
    }

    fun cancelItemReminders(context: Context, itemId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_item_$itemId")
    }

    fun rescheduleAll(context: Context, items: List<CourseItemEntity>) {
        for (item in items) {
            scheduleItemReminders(context, item)
        }
    }
}
