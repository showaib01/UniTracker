package com.example

import com.example.util.CourseMeetingSlot
import com.example.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CourseMeetingUnitTests {

    @Test
    fun testTwoMeetingsWithDifferentRooms() {
        val slots = listOf(
            CourseMeetingSlot("Tuesday", "10:20", "11:50", "9207"),
            CourseMeetingSlot("Thursday", "14:00", "15:30", "DN0109")
        )

        // Sunday at 9:00 AM -> next meeting is Tuesday in Room 9207
        val sundayCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        val infoSunday = DateTimeUtils.calculateNextMeetingSlotInfo(slots, sundayCal.timeInMillis)
        assertEquals("9207", infoSunday.room)

        // Tuesday at 16:00 PM (after Tuesday class ended) -> next meeting is Thursday in Room DN0109
        val tuesdayLateCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }
        val infoTuesdayLate = DateTimeUtils.calculateNextMeetingSlotInfo(slots, tuesdayLateCal.timeInMillis)
        assertEquals("DN0109", infoTuesdayLate.room)
    }

    @Test
    fun testVenueManualOverride() {
        val slots1 = listOf(CourseMeetingSlot("Tuesday", "10:20", "11:50", "9207"))
        val slots2 = listOf(CourseMeetingSlot("Thursday", "14:00", "15:30", "DN0109"))

        // Case A: Not manually edited -> updates venue to slot1 room
        var isVenueManuallyEdited = false
        var venue = ""
        val info1 = DateTimeUtils.calculateNextMeetingSlotInfo(slots1)
        if (!isVenueManuallyEdited) {
            venue = info1.room
        }
        assertEquals("9207", venue)

        // Case B: User manually edits venue -> changing course selection does NOT overwrite venue
        venue = "Custom Annex 4"
        isVenueManuallyEdited = true

        val info2 = DateTimeUtils.calculateNextMeetingSlotInfo(slots2)
        if (!isVenueManuallyEdited) {
            venue = info2.room
        }
        assertEquals("Custom Annex 4", venue)
    }

    @Test
    fun testCourseNotInRoutineRoomIsEmpty() {
        val slots = emptyList<CourseMeetingSlot>()
        val info = DateTimeUtils.calculateNextMeetingSlotInfo(slots)
        assertEquals("", info.room)
        assertFalse(info.hasTime)
    }

    @Test
    fun testNextMeetingCalculationTwoWeekdaysClassTodayNotEnded() {
        val slots = listOf(
            CourseMeetingSlot("Sunday", "10:20", "11:50", "3101"),
            CourseMeetingSlot("Wednesday", "14:00", "15:30", "3102")
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis

        val result = DateTimeUtils.calculateNextMeetingSlot(slots, now)

        val resultCal = Calendar.getInstance().apply { timeInMillis = result.first }
        assertEquals("Should pick Sunday", Calendar.SUNDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals("Should pick 10 AM", 10, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Should pick 20 min", 20, resultCal.get(Calendar.MINUTE))
        assertTrue("hasTime should be true", result.second)
    }

    @Test
    fun testNextMeetingCalculationTwoWeekdaysClassTodayEnded() {
        val slots = listOf(
            CourseMeetingSlot("Sunday", "10:20", "11:50", "3101"),
            CourseMeetingSlot("Wednesday", "14:00", "15:30", "3102")
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis

        val result = DateTimeUtils.calculateNextMeetingSlot(slots, now)

        val resultCal = Calendar.getInstance().apply { timeInMillis = result.first }
        assertEquals("Should pick Wednesday", Calendar.WEDNESDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals("Should pick 14 (2 PM)", 14, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Should pick 00 min", 0, resultCal.get(Calendar.MINUTE))
        assertTrue("hasTime should be true", result.second)
    }

    @Test
    fun testFormatTime12hDisplay() {
        assertEquals("10:20 AM", DateTimeUtils.formatTime12h("10:20"))
        assertEquals("2:00 PM", DateTimeUtils.formatTime12h("14:00"))
        assertEquals("3:30 PM", DateTimeUtils.formatTime12h("15:30"))
        assertEquals("8:00 AM", DateTimeUtils.formatTime12h("08:00"))
    }

    @Test
    fun test15MinClassReminderLeadTimeCalculation() {
        val classStartCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val classStartMillis = classStartCal.timeInMillis
        val leadMinutes = 15
        val triggerMillis = classStartMillis - (leadMinutes * 60_000L)

        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        assertEquals(10, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun testReminderTimeCalculation() {
        val targetTime = 1700000000000L
        val offsets = listOf(1440, 180, 60)

        val triggerTimes = DateTimeUtils.calculateReminderTriggerTimes(targetTime, offsets)

        assertEquals(3, triggerTimes.size)
        assertEquals(targetTime - (1440 * 60_000L), triggerTimes[0])
        assertEquals(targetTime - (180 * 60_000L), triggerTimes[1])
        assertEquals(targetTime - (60 * 60_000L), triggerTimes[2])

        val cal = Calendar.getInstance().apply {
            timeInMillis = targetTime
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val allDay8AM = cal.timeInMillis

        val allDayTriggers = DateTimeUtils.calculateReminderTriggerTimes(allDay8AM, offsets)
        assertEquals(allDay8AM - (1440 * 60_000L), allDayTriggers[0])
    }
}
