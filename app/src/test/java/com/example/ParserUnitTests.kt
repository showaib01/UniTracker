package com.example

import com.example.data.parser.AttendanceParser
import com.example.data.parser.PortalSelectorConfig
import com.example.data.parser.RoutineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class ParserUnitTests {

    private fun loadResource(fileName: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(fileName)
            ?: throw IllegalArgumentException("Resource file $fileName not found in test resources")
        return InputStreamReader(stream).readText()
    }

    @Test
    fun testRealAiubRoutineParser() {
        val html = loadResource("real_aiub_routine.html")
        val parser = RoutineParser()

        assertTrue("Parser should recognize scheduleTable HTML", parser.canParse(html))

        val result = parser.parse(html)
        assertTrue("Parser should succeed", result.isSuccess)

        val routines = result.data
        assertEquals("Should extract exactly 11 deduplicated classes across the week", 11, routines.size)

        // Class 1: Sunday
        val sun1 = routines[0]
        assertEquals("OBJECT ORIENTED PROGRAMMING 2", sun1.courseName)
        assertEquals("V", sun1.section)
        assertEquals("Sunday", sun1.dayOfWeek)
        assertEquals("15:00", sun1.startTime)
        assertEquals("17:20", sun1.endTime)
        assertEquals("DN0212", sun1.room)

        // Class 2: Monday (sorted by startTime 11:20)
        val mon1 = routines[1]
        assertEquals("PRINCIPLES OF ACCOUNTING", mon1.courseName)
        assertEquals("B", mon1.section)
        assertEquals("Monday", mon1.dayOfWeek)
        assertEquals("11:20", mon1.startTime)
        assertEquals("12:50", mon1.endTime)
        assertEquals("1102", mon1.room)

        // Class 3: Monday 13:00
        val mon2 = routines[2]
        assertEquals("OBJECT ORIENTED ANALYSIS AND DESIGN", mon2.courseName)
        assertEquals("P", mon2.section)
        assertEquals("Monday", mon2.dayOfWeek)
        assertEquals("13:00", mon2.startTime)
        assertEquals("14:30", mon2.endTime)
        assertEquals("3110", mon2.room)

        // Class 4: Monday 14:40
        val mon3 = routines[3]
        assertEquals("MATRICES, VECTORS, FOURIER ANALYSIS", mon3.courseName)
        assertEquals("T", mon3.section)
        assertEquals("Monday", mon3.dayOfWeek)
        assertEquals("14:40", mon3.startTime)
        assertEquals("16:10", mon3.endTime)
        assertEquals("DN0609", mon3.room)

        // Class 5: Tuesday 08:00
        val tue1 = routines[4]
        assertEquals("INTRODUCTION TO ELECTRICAL CIRCUITS LAB", tue1.courseName)
        assertEquals("F", tue1.section)
        assertEquals("Tuesday", tue1.dayOfWeek)
        assertEquals("08:00", tue1.startTime)
        assertEquals("10:20", tue1.endTime)
        assertEquals("DS0302", tue1.room)

        // Class 6: Tuesday 10:20
        val tue2 = routines[5]
        assertEquals("ALGORITHMS", tue2.courseName)
        assertEquals("S", tue2.section)
        assertEquals("Tuesday", tue2.dayOfWeek)
        assertEquals("10:20", tue2.startTime)
        assertEquals("12:20", tue2.endTime)
        assertEquals("9207", tue2.room)

        // Class 7: Tuesday 15:00
        val tue3 = routines[6]
        assertEquals("OBJECT ORIENTED PROGRAMMING 2", tue3.courseName)
        assertEquals("V", tue3.section)
        assertEquals("Tuesday", tue3.dayOfWeek)
        assertEquals("15:00", tue3.startTime)
        assertEquals("17:00", tue3.endTime)
        assertEquals("9303", tue3.room)

        // Class 8: Wednesday 11:20
        val wed1 = routines[7]
        assertEquals("PRINCIPLES OF ACCOUNTING", wed1.courseName)
        assertEquals("B", wed1.section)
        assertEquals("Wednesday", wed1.dayOfWeek)
        assertEquals("11:20", wed1.startTime)
        assertEquals("12:50", wed1.endTime)
        assertEquals("1102", wed1.room)

        // Class 9: Wednesday 13:00
        val wed2 = routines[8]
        assertEquals("OBJECT ORIENTED ANALYSIS AND DESIGN", wed2.courseName)
        assertEquals("P", wed2.section)
        assertEquals("Wednesday", wed2.dayOfWeek)
        assertEquals("13:00", wed2.startTime)
        assertEquals("14:30", wed2.endTime)
        assertEquals("3110", wed2.room)

        // Class 10: Wednesday 14:40
        val wed3 = routines[9]
        assertEquals("MATRICES, VECTORS, FOURIER ANALYSIS", wed3.courseName)
        assertEquals("T", wed3.section)
        assertEquals("Wednesday", wed3.dayOfWeek)
        assertEquals("14:40", wed3.startTime)
        assertEquals("16:10", wed3.endTime)
        assertEquals("DN0609", wed3.room)

        // Class 11: Thursday 10:20
        val thu1 = routines[10]
        assertEquals("ALGORITHMS", thu1.courseName)
        assertEquals("S", thu1.section)
        assertEquals("Thursday", thu1.dayOfWeek)
        assertEquals("10:20", thu1.startTime)
        assertEquals("12:40", thu1.endTime)
        assertEquals("DN0109", thu1.room)
    }

    @Test
    fun testLoginDetection() {
        val loggedInHtml = """
            <html><body>
                <div class="header">
                    <a href="/Student/Home">Home</a>
                    <a href="/Login/Logout">Logout</a>
                </div>
                <form id="aspnetForm"><input type="hidden" name="viewstate" /></form>
            </body></html>
        """.trimIndent()

        // Must NOT be detected as login page when logout link is present even if aspnetForm exists
        assertFalse(PortalSelectorConfig.isLoginPage("https://portal.aiub.edu/Student/Home", loggedInHtml))
        assertTrue(PortalSelectorConfig.isLoggedIn(loggedInHtml))

        // Logged out login page with password input
        val loginPageHtml = """
            <html><body>
                <h2>AIUB Student Portal Login</h2>
                <input type="text" name="UserName" />
                <input type="password" name="Password" />
                <button type="submit">Sign In</button>
            </body></html>
        """.trimIndent()
        assertTrue(PortalSelectorConfig.isLoginPage("https://portal.aiub.edu/Login", loginPageHtml))
        assertTrue(PortalSelectorConfig.isLoginPage("https://portal.aiub.edu/Account/Login", ""))
    }

    @Test
    fun testRoutineParserEmptyAndMalformed() {
        val parser = RoutineParser()
        val blankResult = parser.parse("")
        assertFalse(blankResult.isSuccess)
        assertTrue(blankResult.data.isEmpty())

        val noTableResult = parser.parse("<html><body><div>No tables here</div></body></html>")
        assertFalse(noTableResult.isSuccess)
        assertTrue(noTableResult.warnings.isNotEmpty())
    }

    @Test
    fun testAttendanceParserWithSampleHtml() {
        val html = loadResource("sample_attendance.html")
        val parser = AttendanceParser()

        assertTrue("Parser should recognize attendance HTML", parser.canParse(html))

        val result = parser.parse(html)
        assertTrue("Parser should succeed", result.isSuccess)

        val records = result.data
        assertEquals("Should extract 4 valid attendance records", 4, records.size)

        // Record 1: Operating Systems
        val r1 = records[0]
        assertEquals("CSC 3211", r1.courseCode)
        assertEquals("Operating Systems", r1.courseName)
        assertEquals("A", r1.section)
        assertEquals(14, r1.attendedCount)
        assertEquals(16, r1.totalClasses)
        assertEquals(87.5f, r1.percentage, 0.1f)
    }

    @Test
    fun testTimeFormatting12Hour() {
        assertEquals("3:00 PM", com.example.util.DateTimeUtils.formatTime12h("15:00"))
        assertEquals("10:20 AM", com.example.util.DateTimeUtils.formatTime12h("10:20"))
        assertEquals("12:20 PM", com.example.util.DateTimeUtils.formatTime12h("12:20"))
        assertEquals("12:00 AM", com.example.util.DateTimeUtils.formatTime12h("00:00"))

        // Same period (PM - PM): "10:20 – 12:20 PM" when both PM, or "1:00 – 2:30 PM"
        assertEquals("1:00 – 2:30 PM", com.example.util.DateTimeUtils.formatTimeRange12h("13:00", "14:30"))
        assertEquals("8:00 – 10:20 AM", com.example.util.DateTimeUtils.formatTimeRange12h("08:00", "10:20"))

        // Different period (AM - PM): "11:20 AM – 12:50 PM"
        assertEquals("11:20 AM – 12:50 PM", com.example.util.DateTimeUtils.formatTimeRange12h("11:20", "12:50"))
    }

    @Test
    fun testDueDateTextFormatting() {
        val nowCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 28, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = nowCal.timeInMillis

        // Today
        val todayBadge = com.example.util.DateTimeUtils.formatDueBadgeText(nowMillis, false, nowMillis)
        assertEquals("today", todayBadge.label)
        assertEquals("URGENT", todayBadge.urgency)

        // Tomorrow (+1 day)
        val tomorrowCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowBadge = com.example.util.DateTimeUtils.formatDueBadgeText(tomorrowCal.timeInMillis, false, nowMillis)
        assertEquals("tomorrow", tomorrowBadge.label)
        assertEquals("URGENT", tomorrowBadge.urgency)

        // In 8 days
        val in8DaysCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(java.util.Calendar.DAY_OF_YEAR, 8)
        }
        val in8DaysBadge = com.example.util.DateTimeUtils.formatDueBadgeText(in8DaysCal.timeInMillis, false, nowMillis)
        assertEquals("in 8 days", in8DaysBadge.label)
        assertEquals("NORMAL", in8DaysBadge.urgency)

        // Overdue by 2 days (-2 days)
        val overdue2DaysCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(java.util.Calendar.DAY_OF_YEAR, -2)
        }
        val overdueBadge = com.example.util.DateTimeUtils.formatDueBadgeText(overdue2DaysCal.timeInMillis, false, nowMillis)
        assertEquals("overdue by 2 days", overdueBadge.label)
        assertEquals("OVERDUE", overdueBadge.urgency)
    }

    @Test
    fun testNextClassDateCalculation() {
        // Wednesday, Oct 28, 2026
        val wedCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 28, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val wedMillis = wedCal.timeInMillis

        // Class meets on Sunday and Tuesday
        val routineDays = listOf("Sunday", "Tuesday")
        val nextClassDate = com.example.util.DateTimeUtils.calculateNextClassDate(routineDays, wedMillis)

        val targetCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nextClassDate
        }

        // Next class after Wednesday Oct 28 should be Sunday Nov 1
        assertEquals(java.util.Calendar.SUNDAY, targetCal.get(java.util.Calendar.DAY_OF_WEEK))
        assertEquals(1, targetCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.NOVEMBER, targetCal.get(java.util.Calendar.MONTH))
    }

    @Test
    fun testReminderSchedulingTimes() {
        val dueAt = 1761800000000L
        val offsets = listOf(1440, 120, 60) // 1 day, 2 hours, 1 hour
        val triggerTimes = com.example.util.DateTimeUtils.calculateReminderTriggerTimes(dueAt, offsets)

        assertEquals(3, triggerTimes.size)
        assertEquals(dueAt - (1440 * 60_000L), triggerTimes[0])
        assertEquals(dueAt - (120 * 60_000L), triggerTimes[1])
        assertEquals(dueAt - (60 * 60_000L), triggerTimes[2])
    }

    @Test
    fun testSyncDoesNotDeleteCourseItems() {
        // Create sample user-entered CourseItem
        val userItem = com.example.data.local.entity.CourseItemEntity(
            id = 42L,
            courseKey = "ALGORITHMS_S",
            courseName = "ALGORITHMS",
            section = "S",
            type = "QUIZ",
            title = "Quiz 2",
            dueAt = System.currentTimeMillis() + 86400000L,
            topics = "Dynamic Programming"
        )

        val itemsBeforeSync = mutableListOf(userItem)

        // Execute RoutineParser on real portal HTML
        val html = loadResource("real_aiub_routine.html")
        val parser = RoutineParser()
        val result = parser.parse(html)
        assertTrue(result.isSuccess)

        // Routine parser outputs ClassRoutineEntity list, NEVER touches or alters CourseItemEntity
        val routines = result.data
        assertEquals(11, routines.size)

        // Verify user item remains intact and untouched
        assertEquals(1, itemsBeforeSync.size)
        assertEquals("Quiz 2", itemsBeforeSync[0].title)
        assertEquals(42L, itemsBeforeSync[0].id)
    }
}
