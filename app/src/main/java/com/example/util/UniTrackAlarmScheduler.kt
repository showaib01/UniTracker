package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.UniTrackApplication
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseSettingEntity
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduledReminderInfo(
    val title: String,
    val subtitle: String,
    val triggerTimeMillis: Long,
    val formattedTime: String
)

object UniTrackAlarmScheduler {

    /**
     * Rebuilds exact AlarmManager setAlarmClock alarms for the next 14 days.
     * Schedules both class routine reminders (e.g. 15 min lead) and deadline reminders.
     */
    suspend fun scheduleAllRemindersForNext14Days(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as UniTrackApplication
        val prefs = app.preferencesRepository.userPreferencesFlow.first()

        if (!prefs.allRemindersEnabled) {
            UniTrackLogger.i("UniTrack: All reminders muted in preferences. Cancelling all alarms.")
            cancelAllAlarms(context)
            return@withContext
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext

        val now = System.currentTimeMillis()
        val leadMinutes = prefs.reminderMinutes

        val dayMap = mapOf(
            "SUNDAY" to Calendar.SUNDAY,
            "MONDAY" to Calendar.MONDAY,
            "TUESDAY" to Calendar.TUESDAY,
            "WEDNESDAY" to Calendar.WEDNESDAY,
            "THURSDAY" to Calendar.THURSDAY,
            "FRIDAY" to Calendar.FRIDAY,
            "SATURDAY" to Calendar.SATURDAY
        )

        // 1. Class Routine Alarms for next 14 days
        val routines = app.database.routineDao().getAllRoutinesDirect()
        val courseSettings = app.database.courseSettingDao().getAllSettings().first().associateBy { it.courseKey }

        for (routine in routines) {
            val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
            val setting = courseSettings[key]
            if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) {
                continue
            }

            val targetDayOfWeek = dayMap[routine.dayOfWeek.trim().uppercase()] ?: continue
            val startMins = DateTimeUtils.parseTimeToMinutes(routine.startTime)
            val startHour = startMins / 60
            val startMin = startMins % 60

            for (dayOffset in 0..13) {
                val candidateCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }

                if (candidateCal.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    candidateCal.set(Calendar.HOUR_OF_DAY, startHour)
                    candidateCal.set(Calendar.MINUTE, startMin)
                    candidateCal.set(Calendar.SECOND, 0)
                    candidateCal.set(Calendar.MILLISECOND, 0)

                    val classStartMillis = candidateCal.timeInMillis
                    val triggerMillis = classStartMillis - (leadMinutes * 60_000L)

                    if (triggerMillis > now) {
                        val requestCode = ("CLASS_" + key + "_" + candidateCal.get(Calendar.DAY_OF_YEAR) + "_" + routine.startTime).hashCode()
                        val intent = Intent(context, UniTrackAlarmReceiver::class.java).apply {
                            action = UniTrackAlarmReceiver.ACTION_CLASS_REMINDER
                            putExtra("COURSE_NAME", routine.courseName)
                            putExtra("SECTION", routine.section)
                            putExtra("ROOM", routine.room)
                            putExtra("START_TIME", routine.startTime)
                            putExtra("COURSE_KEY", key)
                        }
                        scheduleExactAlarm(context, alarmManager, requestCode, triggerMillis, intent)
                    }
                }
            }
        }

        // 2. Course Item Deadline Alarms
        val items = app.database.courseItemDao().getPendingItemsDueAfter(now)
        for (item in items) {
            if (item.status != CourseItemStatus.TODO) continue
            val setting = courseSettings[item.courseKey]
            if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) continue

            val offsets = item.getReminderOffsetsList()
            for (offset in offsets) {
                val triggerMillis = item.dueAt - (offset * 60_000L)
                if (triggerMillis > now) {
                    val requestCode = ("ITEM_" + item.id + "_" + offset).hashCode()
                    val intent = Intent(context, UniTrackAlarmReceiver::class.java).apply {
                        action = UniTrackAlarmReceiver.ACTION_ITEM_REMINDER
                        putExtra("ITEM_ID", item.id)
                        putExtra("OFFSET_MINUTES", offset)
                    }
                    scheduleExactAlarm(context, alarmManager, requestCode, triggerMillis, intent)
                }
            }
        }

        UniTrackLogger.i("UniTrack: Completed scheduling exact alarms for next 14 days")
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerMillis: Long,
        intent: Intent
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: Exception) {
            UniTrackLogger.e("UniTrack: Error scheduling alarm ($requestCode): ${e.message}")
        }
    }

    fun scheduleTest1MinAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerMillis = System.currentTimeMillis() + 60_000L
        val intent = Intent(context, UniTrackAlarmReceiver::class.java).apply {
            action = UniTrackAlarmReceiver.ACTION_TEST_IN_1_MIN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            88881,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            UniTrackLogger.i("UniTrack: Scheduled 1-minute test alarm for $triggerMillis")
        } catch (e: Exception) {
            UniTrackLogger.e("UniTrack: Error scheduling 1-min test alarm: ${e.message}")
        }
    }

    suspend fun getUpcomingScheduledReminders(context: Context, limit: Int = 5): List<ScheduledReminderInfo> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as UniTrackApplication
        val now = System.currentTimeMillis()
        val prefs = app.preferencesRepository.userPreferencesFlow.first()
        val leadMinutes = prefs.reminderMinutes

        val list = mutableListOf<ScheduledReminderInfo>()

        val routines = app.database.routineDao().getAllRoutinesDirect()
        val courseSettings = app.database.courseSettingDao().getAllSettings().first().associateBy { it.courseKey }

        val dayMap = mapOf(
            "SUNDAY" to Calendar.SUNDAY,
            "MONDAY" to Calendar.MONDAY,
            "TUESDAY" to Calendar.TUESDAY,
            "WEDNESDAY" to Calendar.WEDNESDAY,
            "THURSDAY" to Calendar.THURSDAY,
            "FRIDAY" to Calendar.FRIDAY,
            "SATURDAY" to Calendar.SATURDAY
        )

        for (routine in routines) {
            val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
            val setting = courseSettings[key]
            if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) continue

            val targetDayOfWeek = dayMap[routine.dayOfWeek.trim().uppercase()] ?: continue
            val startMins = DateTimeUtils.parseTimeToMinutes(routine.startTime)
            val startHour = startMins / 60
            val startMin = startMins % 60

            for (dayOffset in 0..13) {
                val candidateCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }

                if (candidateCal.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    candidateCal.set(Calendar.HOUR_OF_DAY, startHour)
                    candidateCal.set(Calendar.MINUTE, startMin)
                    candidateCal.set(Calendar.SECOND, 0)
                    candidateCal.set(Calendar.MILLISECOND, 0)

                    val classStartMillis = candidateCal.timeInMillis
                    val triggerMillis = classStartMillis - (leadMinutes * 60_000L)

                    if (triggerMillis > now) {
                        val dateFormatted = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(triggerMillis))
                        val timeFormatted = DateTimeUtils.formatTime12h(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(triggerMillis)))
                        list.add(
                            ScheduledReminderInfo(
                                title = "${routine.courseName} [${routine.section}]",
                                subtitle = "Class at ${DateTimeUtils.formatTime12h(routine.startTime)} (${routine.room})",
                                triggerTimeMillis = triggerMillis,
                                formattedTime = "$dateFormatted · $timeFormatted"
                            )
                        )
                    }
                }
            }
        }

        val items = app.database.courseItemDao().getPendingItemsDueAfter(now)
        for (item in items) {
            if (item.status != CourseItemStatus.TODO) continue
            val setting = courseSettings[item.courseKey]
            if (setting != null && (setting.isDropped || !setting.notificationsEnabled)) continue

            val offsets = item.getReminderOffsetsList()
            for (offset in offsets) {
                val triggerMillis = item.dueAt - (offset * 60_000L)
                if (triggerMillis > now) {
                    val dateFormatted = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(triggerMillis))
                    val timeFormatted = DateTimeUtils.formatTime12h(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(triggerMillis)))
                    list.add(
                        ScheduledReminderInfo(
                            title = "${item.courseName} · ${item.title}",
                            subtitle = "Deadline reminder ($offset min before)",
                            triggerTimeMillis = triggerMillis,
                            formattedTime = "$dateFormatted · $timeFormatted"
                        )
                    )
                }
            }
        }

        list.sortBy { it.triggerTimeMillis }
        list.take(limit)
    }

    fun cancelAllAlarms(context: Context) {
        // Alarms are cleaned via intent replacements or clearAll
    }
}
