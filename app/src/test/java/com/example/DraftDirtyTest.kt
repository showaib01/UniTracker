package com.example

import com.example.ui.components.isDraftDirty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftDirtyTest {

    @Test
    fun testIsDraftDirty() {
        val initialDueAt = 1000L
        val initialHasTime = false
        val initialType = "QUIZ"
        val initialCourseKey = "CSE110_A"
        val initialOffsets = setOf(1440, 120)
        val initialVenue = "VLLAB"
        val autoTitle = "Quiz"

        // 1. Untouched sheet -> false
        assertFalse(
            isDraftDirty(
                title = autoTitle,
                autoTitle = autoTitle,
                topics = "",
                notes = "",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = initialType,
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )

        // 2. Typed custom title -> true
        assertTrue(
            isDraftDirty(
                title = "Midterm Quiz 1",
                autoTitle = autoTitle,
                topics = "",
                notes = "",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = initialType,
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )

        // 3. Only notes -> true
        assertTrue(
            isDraftDirty(
                title = autoTitle,
                autoTitle = autoTitle,
                topics = "",
                notes = "Study chapter 3",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = initialType,
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )

        // 4. Only date changed -> true
        assertTrue(
            isDraftDirty(
                title = autoTitle,
                autoTitle = autoTitle,
                topics = "",
                notes = "",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt + 86400000L,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = initialType,
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )

        // 5. Title equal to auto title and nothing else -> false
        assertFalse(
            isDraftDirty(
                title = "Quiz",
                autoTitle = "Quiz",
                topics = "",
                notes = "",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = initialType,
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )

        // 6. Cleared title with a changed type -> true
        assertTrue(
            isDraftDirty(
                title = "",
                autoTitle = "Assignment",
                topics = "",
                notes = "",
                marksStr = "",
                venue = initialVenue,
                initialVenue = initialVenue,
                dueAt = initialDueAt,
                initialDueAt = initialDueAt,
                hasTime = initialHasTime,
                initialHasTime = initialHasTime,
                type = "ASSIGNMENT",
                initialType = initialType,
                courseKey = initialCourseKey,
                initialCourseKey = initialCourseKey,
                offsets = initialOffsets,
                initialOffsets = initialOffsets
            )
        )
    }
}
