package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun getCurrentDayOfWeek(): String {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("EEEE", Locale.ENGLISH)
        return format.format(calendar.time)
    }

    /**
     * Formats headline date, e.g. "28 October"
     */
    fun formatDateHeadline(timestamp: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("d MMMM", Locale.ENGLISH)
        return format.format(Date(timestamp))
    }

    /**
     * Formats live clock for the AppTopBar glass pill: "Wednesday | 12:19 PM"
     */
    fun formatLiveClock(timestamp: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
        val time24 = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(cal.time)
        return "$dayName | ${formatTime12h(time24)}"
    }

    /**
     * Formats time into 12-hour display format:
     * - "15:00" -> "3:00 PM"
     * - "08:00" -> "8:00 AM"
     * - "10:20" -> "10:20 AM"
     * - "12:20" -> "12:20 PM"
     * - "00:30" -> "12:30 AM"
     */
    fun formatTime12h(timeStr: String): String {
        val (h12, minStr, amPm) = parseComponents12h(timeStr) ?: return timeStr
        return "$h12:$minStr $amPm"
    }

    /**
     * Formats a time range into 12-hour display format:
     * - When both times share the same AM/PM: omit the first period, e.g. "1:00 – 2:30 PM", "8:00 – 10:20 AM"
     * - When periods differ: show both periods, e.g. "10:20 AM – 12:20 PM", "11:20 AM – 12:50 PM"
     * Uses en-dash '–' with surrounding spaces.
     */
    fun formatTimeRange12h(startTimeStr: String, endTimeStr: String): String {
        val startComp = parseComponents12h(startTimeStr)
        val endComp = parseComponents12h(endTimeStr)

        if (startComp == null || endComp == null) {
            return "$startTimeStr – $endTimeStr"
        }

        val (sHour, sMin, sPeriod) = startComp
        val (eHour, eMin, ePeriod) = endComp

        return if (sPeriod.equals(ePeriod, ignoreCase = true)) {
            "$sHour:$sMin – $eHour:$eMin $ePeriod"
        } else {
            "$sHour:$sMin $sPeriod – $eHour:$eMin $ePeriod"
        }
    }

    /**
     * Helper to decompose a 24h or flexible time string into (12h Hour, "mm", "AM"/"PM")
     */
    private fun parseComponents12h(rawStr: String): Triple<Int, String, String>? {
        if (rawStr.isBlank()) return null
        val cleaned = rawStr.trim().uppercase()
        val isExplicitPm = cleaned.contains("PM")
        val isExplicitAm = cleaned.contains("AM")

        val digits = cleaned.replace("AM", "").replace("PM", "").replace('_', ' ').trim()
        val parts = digits.split(":")
        val hRaw = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val mRaw = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        val minFormatted = String.format(Locale.US, "%02d", mRaw.coerceIn(0, 59))

        return when {
            isExplicitPm -> {
                val h12 = if (hRaw in 1..11) hRaw else if (hRaw == 12) 12 else (hRaw % 12).let { if (it == 0) 12 else it }
                Triple(h12, minFormatted, "PM")
            }
            isExplicitAm -> {
                val h12 = if (hRaw in 1..11) hRaw else if (hRaw == 0 || hRaw == 12) 12 else (hRaw % 12).let { if (it == 0) 12 else it }
                Triple(h12, minFormatted, "AM")
            }
            // 24-hour time string
            else -> {
                val period = if (hRaw >= 12) "PM" else "AM"
                val h12 = when {
                    hRaw == 0 -> 12
                    hRaw > 12 -> hRaw - 12
                    else -> hRaw
                }
                Triple(h12, minFormatted, period)
            }
        }
    }

    /**
     * Formats last sync timestamp quietly:
     * "Synced just now", "Synced 2 h ago", "Synced 15 min ago"
     */
    fun formatLastSynced(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / (60 * 1000)
        return when {
            diffMinutes < 1 -> "Synced just now"
            diffMinutes < 60 -> "Synced $diffMinutes min ago"
            diffMinutes < 1440 -> "Synced ${diffMinutes / 60} h ago"
            else -> {
                val days = diffMinutes / 1440
                "Synced $days d ago"
            }
        }
    }

    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    /**
     * Formats relative due date for tasks: "Today", "Tomorrow", "in 3 days", "3 days ago"
     */
    fun formatRelativeDueDate(dueMillis: Long): String {
        return formatDueBadgeText(dueMillis).label
    }

    data class DueBadgeInfo(
        val label: String,
        val urgency: String, // "NORMAL", "URGENT", "OVERDUE"
        val formattedDateTime: String,
        val diffDays: Int
    )

    /**
     * Formats due badge text according to specifications:
     * - "today", "tomorrow", "in 8 days", "overdue by 2 days", etc.
     * Urgency: "OVERDUE" if overdue, "URGENT" if <= 3 days, "NORMAL" otherwise.
     */
    fun formatDueBadgeText(
        dueMillis: Long,
        hasTime: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): DueBadgeInfo {
        if (dueMillis <= 0L) {
            return DueBadgeInfo("no due date", "NORMAL", "", 0)
        }

        val nowCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetCal = Calendar.getInstance().apply {
            timeInMillis = dueMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffDays = ((targetCal.timeInMillis - nowCal.timeInMillis) / (24L * 3600 * 1000)).toInt()

        val label = when {
            diffDays < -1 -> "overdue by ${-diffDays} days"
            diffDays == -1 -> "overdue by 1 day"
            diffDays == 0 -> "today"
            diffDays == 1 -> "tomorrow"
            else -> "in $diffDays days"
        }

        val urgency = when {
            diffDays < 0 -> "OVERDUE"
            diffDays <= 3 -> "URGENT"
            else -> "NORMAL"
        }

        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
        val formattedDateTime = if (hasTime) {
            val timeCal = Calendar.getInstance().apply { timeInMillis = dueMillis }
            val timeStr = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(timeCal.time)
            "${dateFormat.format(Date(dueMillis))} · ${formatTime12h(timeStr)}"
        } else {
            dateFormat.format(Date(dueMillis))
        }

        return DueBadgeInfo(
            label = label,
            urgency = urgency,
            formattedDateTime = formattedDateTime,
            diffDays = diffDays
        )
    }

    /**
     * Calculates the next calendar date (in millis) when this course meets,
     * based on the list of routine days of the week (e.g. ["Sunday", "Tuesday"]).
     */
    fun calculateNextClassDate(
        routineDays: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (routineDays.isEmpty()) {
            // Default to tomorrow
            val tomorrow = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return tomorrow.timeInMillis
        }

        val dayMap = mapOf(
            "SUNDAY" to Calendar.SUNDAY,
            "MONDAY" to Calendar.MONDAY,
            "TUESDAY" to Calendar.TUESDAY,
            "WEDNESDAY" to Calendar.WEDNESDAY,
            "THURSDAY" to Calendar.THURSDAY,
            "FRIDAY" to Calendar.FRIDAY,
            "SATURDAY" to Calendar.SATURDAY
        )

        val targetCalDays = routineDays.mapNotNull { dayMap[it.trim().uppercase()] }.toSet()
        if (targetCalDays.isEmpty()) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        val searchCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Look up to 7 days ahead (starting from tomorrow so it's the NEXT class)
        for (i in 1..7) {
            searchCal.add(Calendar.DAY_OF_YEAR, 1)
            val dayOfWeek = searchCal.get(Calendar.DAY_OF_WEEK)
            if (targetCalDays.contains(dayOfWeek)) {
                searchCal.set(Calendar.HOUR_OF_DAY, 10)
                searchCal.set(Calendar.MINUTE, 0)
                return searchCal.timeInMillis
            }
        }

        return nowMillis + 86400000L
    }

    /**
     * Calculates reminder trigger timestamps in epoch millis given dueAt and list of offsets in minutes.
     */
    fun calculateReminderTriggerTimes(dueAt: Long, reminderOffsetsMinutes: List<Int>): List<Long> {
        return reminderOffsetsMinutes.map { minutes ->
            dueAt - (minutes * 60_000L)
        }
    }

    /**
     * Computes status, minutes until/remaining, and elapsed fraction (0f..1f)
     */
    fun getClassTimingStatus(startTimeStr: String, endTimeStr: String): Triple<String, Int, Float> {
        return try {
            val nowCal = Calendar.getInstance()
            val currentMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

            val startMinutes = parseTimeToMinutes(startTimeStr)
            val endMinutes = parseTimeToMinutes(endTimeStr)

            val totalDuration = (endMinutes - startMinutes).coerceAtLeast(1)

            when {
                currentMinutes in startMinutes until endMinutes -> {
                    val remaining = endMinutes - currentMinutes
                    val elapsed = currentMinutes - startMinutes
                    val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    Triple("IN_PROGRESS", remaining, progress)
                }
                currentMinutes < startMinutes -> {
                    Triple("UPCOMING", startMinutes - currentMinutes, 0f)
                }
                else -> {
                    Triple("PAST", 0, 1f)
                }
            }
        } catch (e: Exception) {
            Triple("UNKNOWN", 0, 0f)
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        val cleaned = timeStr.trim().uppercase()
        val isPm = cleaned.contains("PM")
        val isAm = cleaned.contains("AM")
        val digits = cleaned.replace("AM", "").replace("PM", "").trim()
        val parts = digits.split(":")
        var hour = parts[0].toIntOrNull() ?: 8
        val min = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0

        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0

        return hour * 60 + min
    }
}
