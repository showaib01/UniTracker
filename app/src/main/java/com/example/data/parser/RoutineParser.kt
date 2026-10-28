package com.example.data.parser

import com.example.data.local.entity.ClassRoutineEntity
import com.example.util.UniTrackLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RoutineParser : PageParser<List<ClassRoutineEntity>> {

    override fun canParse(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("scheduletable") ||
                lower.contains("class schedule") ||
                (lower.contains("student/section") && lower.contains("room:"))
    }

    override fun parse(html: String): ParserResult<List<ClassRoutineEntity>> {
        val list = mutableListOf<ClassRoutineEntity>()
        val warnings = mutableListOf<String>()

        if (html.isBlank()) {
            return ParserResult(emptyList(), listOf("Provided HTML is empty"), isSuccess = false)
        }

        try {
            val doc: Document = Jsoup.parse(html)
            val container: Element? = doc.select(PortalSelectorConfig.SCHEDULE_CONTAINER_SELECTOR).first()

            if (container == null) {
                val errorMsg = "Schedule container '${PortalSelectorConfig.SCHEDULE_CONTAINER_SELECTOR}' not found."
                warnings.add(errorMsg)
                UniTrackLogger.w("RoutineParser: $errorMsg")
                return ParserResult(emptyList(), warnings, isSuccess = false)
            }

            UniTrackLogger.d("RoutineParser: scheduleTable container found")

            val rows = container.select(PortalSelectorConfig.SCHEDULE_DAY_ROWS_SELECTOR)
            if (rows.isEmpty()) {
                val warnMsg = "Schedule container found but contains no day rows."
                warnings.add(warnMsg)
                UniTrackLogger.w("RoutineParser: $warnMsg")
                return ParserResult(emptyList(), warnings, isSuccess = true)
            }

            for ((rowIndex, row) in rows.withIndex()) {
                // Check if this row is marked as "No Class On This Day"
                val noClassElements = row.select(PortalSelectorConfig.SCHEDULE_NO_CLASS_SELECTOR)
                val isNoClass = noClassElements.any {
                    it.text().contains(PortalSelectorConfig.SCHEDULE_NO_CLASS_TEXT, ignoreCase = true)
                }
                if (isNoClass) {
                    // Ignored without warnings as required
                    continue
                }

                val classItems = row.select(PortalSelectorConfig.SCHEDULE_CLASS_ITEMS_SELECTOR)
                if (classItems.isEmpty()) {
                    continue
                }

                for (item in classItems) {
                    val anchor = item.select(PortalSelectorConfig.SCHEDULE_COURSE_TITLE_SELECTOR).firstOrNull()
                    if (anchor == null) {
                        warnings.add("Row ${rowIndex + 1}: Skipping class item missing course title anchor.")
                        continue
                    }

                    val rawTitle = cleanText(anchor.text())
                    if (rawTitle.isBlank()) {
                        warnings.add("Row ${rowIndex + 1}: Skipping empty course title.")
                        continue
                    }

                    // Split "NAME  [SECTION]"
                    val (courseName, section) = splitCourseTitle(rawTitle)

                    // Find inner div for Time and Room labels
                    val innerDiv = item.select("div").firstOrNull()
                    val labels = innerDiv?.select("label") ?: item.select("label")

                    val timeRaw = cleanText(labels.getOrNull(0)?.text())
                    val roomRaw = cleanText(labels.getOrNull(1)?.text())
                    val room = if (roomRaw.isNotBlank()) {
                        roomRaw
                    } else {
                        val divText = innerDiv?.text() ?: item.text()
                        val roomMatch = Regex("(?i)Room:\\s*([A-Za-z0-9_-]+)").find(divText)
                        roomMatch?.groupValues?.getOrNull(1) ?: "TBD"
                    }

                    if (timeRaw.isBlank()) {
                        warnings.add("Row ${rowIndex + 1} ('$courseName'): Missing time label text.")
                        continue
                    }

                    // Extract Day from time string (e.g. "Tue" from "Tue 3:0 PM - Tue 5:0 PM")
                    val dayOfWeek = extractDayFromTime(timeRaw)
                    if (dayOfWeek == null) {
                        warnings.add("Row ${rowIndex + 1} ('$courseName'): Unable to identify day from '$timeRaw'.")
                        continue
                    }

                    // Parse Time range into normalized 24-hour HH:mm
                    val timeRange = parseTimeRange(timeRaw)
                    if (timeRange == null) {
                        warnings.add("Row ${rowIndex + 1} ('$courseName'): Invalid time format '$timeRaw'.")
                        continue
                    }

                    val entity = ClassRoutineEntity(
                        courseCode = "", // No course code in this view; courseName + section is the identity
                        courseName = courseName,
                        section = section.ifBlank { "A" },
                        dayOfWeek = dayOfWeek,
                        startTime = timeRange.first,
                        endTime = timeRange.second,
                        room = room,
                        faculty = "TBA",
                        lastUpdated = System.currentTimeMillis()
                    )
                    list.add(entity)
                }
            }

            // De-duplicate on (courseName, section, day, startTime, endTime)
            val deduplicated = list.distinctBy {
                "${it.courseName.lowercase()}|${it.section.lowercase()}|${it.dayOfWeek}|${it.startTime}|${it.endTime}"
            }

            // Sort by day order (Sunday first) then start time
            val dayOrder = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val sortedList = deduplicated.sortedWith(
                compareBy(
                    { dayOrder.indexOf(it.dayOfWeek).let { idx -> if (idx == -1) 99 else idx } },
                    { it.startTime }
                )
            )

            if (sortedList.isEmpty()) {
                val warnMsg = "Schedule container found but no classes were parsed."
                warnings.add(warnMsg)
                UniTrackLogger.w("RoutineParser: $warnMsg")
                return ParserResult(emptyList(), warnings, isSuccess = true)
            }

            UniTrackLogger.i("RoutineParser: Successfully parsed ${sortedList.size} unique classes (from ${list.size} total items) with ${warnings.size} warnings")

            return ParserResult(sortedList, warnings, isSuccess = true)

        } catch (e: Exception) {
            val errorMsg = "RoutineParser exception: ${e.message ?: e.javaClass.simpleName}"
            UniTrackLogger.e(errorMsg, e)
            warnings.add(errorMsg)
            return ParserResult(list, warnings, isSuccess = false)
        }
    }

    /**
     * Splits "OBJECT ORIENTED PROGRAMMING 2  [V]" into ("OBJECT ORIENTED PROGRAMMING 2", "V").
     */
    internal fun splitCourseTitle(rawTitle: String): Pair<String, String> {
        val lastOpen = rawTitle.lastIndexOf('[')
        val lastClose = rawTitle.lastIndexOf(']')

        if (lastOpen != -1 && lastClose > lastOpen) {
            val name = rawTitle.substring(0, lastOpen).trim().replace(Regex("\\s+"), " ")
            val section = rawTitle.substring(lastOpen + 1, lastClose).trim()
            return Pair(name, section)
        }

        return Pair(rawTitle.trim().replace(Regex("\\s+"), " "), "")
    }

    /**
     * Extracts 3-letter day name from time text and maps it to full day name.
     * e.g., "Tue 3:0 PM - Tue 5:0 PM" -> "Tuesday"
     */
    internal fun extractDayFromTime(timeText: String): String? {
        val match = Regex("(?i)\\b(Sun|Mon|Tue|Wed|Thu|Fri|Sat)\\b").find(timeText) ?: return null
        return when (match.value.lowercase()) {
            "sun" -> "Sunday"
            "mon" -> "Monday"
            "tue" -> "Tuesday"
            "wed" -> "Wednesday"
            "thu" -> "Thursday"
            "fri" -> "Friday"
            "sat" -> "Saturday"
            else -> null
        }
    }

    /**
     * Parses time string into standardized 24-hour "HH:mm" start and end pair.
     *
     * Handles:
     * - "Tue 3:0 PM - Tue 5:0 PM" -> ("15:00", "17:00")
     * - "Tue 8:0 - Tue 10:20" -> ("08:00", "10:20")
     * - "Tue 10:20 - Tue 12:20 PM" -> ("10:20", "12:20")
     * - "Wed 1:0 PM - Wed 2:30 PM" -> ("13:00", "14:30")
     * - "Wed 11:20 - Wed 12:50 PM" -> ("11:20", "12:50")
     * - "Thu 10:20 - Thu 12:40 PM" -> ("10:20", "12:40")
     * - "Sun 3:0 PM - Sun 5:20 PM" -> ("15:00", "17:20")
     */
    internal fun parseTimeRange(timeStr: String): Pair<String, String>? {
        if (timeStr.isBlank()) return null

        val parts = timeStr.split(Regex("[-–—]|(?i)\\bto\\b")).map { it.trim() }
        if (parts.size < 2) return null

        // Strip day names from both parts
        val dayPattern = Regex("(?i)^[\\s_]*(Sun|Mon|Tue|Wed|Thu|Fri|Sat)[\\s_]*")
        val leftRaw = dayPattern.replace(parts[0], "").trim()
        val rightRaw = dayPattern.replace(parts[1], "").trim()

        val isStartPm = leftRaw.contains("PM", ignoreCase = true)
        val isStartAm = leftRaw.contains("AM", ignoreCase = true)
        val isEndPm = rightRaw.contains("PM", ignoreCase = true)
        val isEndAm = rightRaw.contains("AM", ignoreCase = true)

        val startParsed = parseTimeComponents(leftRaw) ?: return null
        val endParsed = parseTimeComponents(rightRaw) ?: return null

        val hLeft = startParsed.first
        val mLeft = startParsed.second
        val hRight = endParsed.first
        val mRight = endParsed.second

        val (finalStart, finalEnd) = when {
            // Both sides have AM/PM explicitly
            (isStartAm || isStartPm) && (isEndAm || isEndPm) -> {
                val start24 = to24Hour(hLeft, mLeft, isStartPm, isStartAm)
                val end24 = to24Hour(hRight, mRight, isEndPm, isEndAm)
                Pair(start24, end24)
            }

            // Only end has AM/PM explicitly, start has none
            (isEndAm || isEndPm) && (!isStartAm && !isStartPm) -> {
                val end24 = to24Hour(hRight, mRight, isEndPm, isEndAm)
                val endTotalMinutes = end24.first * 60 + end24.second

                // Try AM candidate for start
                val startAm24 = to24Hour(hLeft, mLeft, isPm = false, isAm = true)
                val startAmMinutes = startAm24.first * 60 + startAm24.second
                val durationAm = endTotalMinutes - startAmMinutes

                // Try PM candidate for start
                val startPm24 = to24Hour(hLeft, mLeft, isPm = true, isAm = false)
                val startPmMinutes = startPm24.first * 60 + startPm24.second
                val durationPm = endTotalMinutes - startPmMinutes

                // Choose AM or PM so start < end and duration is under 6 hours (<= 360 mins)
                val chosenStart = when {
                    durationPm in 1..360 && (durationAm !in 1..360 || durationPm <= durationAm) -> startPm24
                    durationAm in 1..360 -> startAm24
                    durationPm > 0 -> startPm24
                    else -> startAm24
                }
                Pair(chosenStart, end24)
            }

            // Only start has AM/PM explicitly, end has none
            (isStartAm || isStartPm) && (!isEndAm && !isEndPm) -> {
                val start24 = to24Hour(hLeft, mLeft, isStartPm, isStartAm)
                val startTotalMinutes = start24.first * 60 + start24.second

                val endAm24 = to24Hour(hRight, mRight, isPm = false, isAm = true)
                val durationAm = (endAm24.first * 60 + endAm24.second) - startTotalMinutes

                val endPm24 = to24Hour(hRight, mRight, isPm = true, isAm = false)
                val durationPm = (endPm24.first * 60 + endPm24.second) - startTotalMinutes

                val chosenEnd = when {
                    durationPm in 1..360 && (durationAm !in 1..360 || durationPm <= durationAm) -> endPm24
                    durationAm in 1..360 -> endAm24
                    else -> endPm24
                }
                Pair(start24, chosenEnd)
            }

            // Neither side has AM/PM -> treat as 24-hour time ("8:0 - 10:20" -> 08:00 - 10:20)
            else -> {
                Pair(Pair(hLeft, mLeft), Pair(hRight, mRight))
            }
        }

        val startFormatted = String.format("%02d:%02d", finalStart.first, finalStart.second)
        val endFormatted = String.format("%02d:%02d", finalEnd.first, finalEnd.second)
        return Pair(startFormatted, endFormatted)
    }

    private fun parseTimeComponents(raw: String): Pair<Int, Int>? {
        val cleaned = raw.replace(Regex("(?i)AM|PM"), "").trim()
        val parts = cleaned.split(":")
        if (parts.isEmpty()) return null

        val h = parts[0].trim().toIntOrNull() ?: return null
        val mStr = parts.getOrNull(1)?.trim() ?: "00"
        val m = if (mStr == "0") 0 else (mStr.toIntOrNull() ?: 0)

        if (h !in 0..23 || m !in 0..59) return null
        return Pair(h, m)
    }

    private fun to24Hour(hour: Int, minute: Int, isPm: Boolean, isAm: Boolean): Pair<Int, Int> {
        var h = hour
        if (isPm && h < 12) h += 12
        if (isAm && h == 12) h = 0
        return Pair(h, minute)
    }

    private fun cleanText(text: String?): String {
        if (text == null) return ""
        return text
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
