package com.example

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
    fun testRoutineParserWithSampleHtml() {
        val html = loadResource("sample_routine.html")
        val parser = RoutineParser()

        assertTrue("Parser should recognize routine HTML", parser.canParse(html))

        val result = parser.parse(html)
        assertTrue("Parser should succeed", result.isSuccess)

        val routines = result.data
        assertTrue("Should extract routines", routines.isNotEmpty())

        val first = routines.first()
        assertNotNull(first.courseCode)
        assertNotNull(first.courseName)
        assertNotNull(first.section)
        assertNotNull(first.dayOfWeek)
        assertNotNull(first.startTime)
        assertNotNull(first.endTime)
    }

    @Test
    fun testRoutineParserCanParseInvalidHtml() {
        val parser = RoutineParser()
        assertFalse(parser.canParse("<html><body><div>No routine here</div></body></html>"))

        val noTableResult = parser.parse("<html><body><div>No tables here</div></body></html>")
        assertFalse(noTableResult.isSuccess)
        assertTrue(noTableResult.warnings.isNotEmpty())
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
}
