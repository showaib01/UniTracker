package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UniTrackDatabase
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceUnitTests {

    private lateinit var db: UniTrackDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UniTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testFreshDatabaseIsEmpty() = runBlocking {
        assertTrue("Routine table should be empty on fresh install", db.routineDao().getAllRoutinesDirect().isEmpty())
        assertTrue("Notice table should be empty on fresh install", db.noticeDao().getAllNotices().first().isEmpty())
        assertTrue("Result table should be empty on fresh install", db.resultDao().getAllResults().first().isEmpty())
        assertTrue("Course items table should be empty on fresh install", db.courseItemDao().getAllItems().first().isEmpty())
    }

    @Test
    fun testSyncReplacesPortalRowsPerWeekdayAndKeepsUserItems() = runBlocking {
        val userItem = CourseItemEntity(
            courseKey = "cse1101_a",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            type = CourseItemType.QUIZ,
            title = "Quiz 1",
            dueAt = System.currentTimeMillis() + 86400000L,
            hasTime = true,
            topics = "Variables",
            status = CourseItemStatus.TODO
        )
        db.courseItemDao().insertItem(userItem)

        val portalRoutine = ClassRoutineEntity(
            courseCode = "CSE1101",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            dayOfWeek = "Monday",
            startTime = "08:00",
            endTime = "10:30",
            room = "3101",
            faculty = "Dr. John",
            lastSeen = System.currentTimeMillis()
        )

        db.routineDao().syncRoutinesPerDay(listOf(portalRoutine))

        val routines = db.routineDao().getAllRoutinesDirect()
        assertEquals("Should contain 1 synced routine row", 1, routines.size)
        assertEquals("COMPUTER PROGRAMMING", routines[0].courseName)

        val items = db.courseItemDao().getAllItems().first()
        assertEquals("User-entered course item must be preserved", 1, items.size)
        assertEquals("Quiz 1", items[0].title)
    }

    @Test
    fun testFailedOrEmptyParseDeletesNothing() = runBlocking {
        val initialRoutine = ClassRoutineEntity(
            courseCode = "CSE1101",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            dayOfWeek = "Monday",
            startTime = "08:00",
            endTime = "10:30",
            room = "3101",
            faculty = "Dr. John",
            lastSeen = System.currentTimeMillis()
        )
        db.routineDao().insertRoutines(listOf(initialRoutine))

        db.routineDao().syncRoutinesPerDay(emptyList())

        val routines = db.routineDao().getAllRoutinesDirect()
        assertEquals("Existing routine data must not be deleted on empty/failed parse", 1, routines.size)
    }

    @Test
    fun testClearAllLeavesEveryTableEmpty() = runBlocking {
        val routine = ClassRoutineEntity(
            courseCode = "CSE1101",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            dayOfWeek = "Monday",
            startTime = "08:00",
            endTime = "10:30",
            room = "3101",
            faculty = "Dr. John"
        )
        db.routineDao().insertRoutines(listOf(routine))

        db.clearAllTables()

        assertTrue("Routine table should be empty after clearAllTables", db.routineDao().getAllRoutinesDirect().isEmpty())
        assertTrue("Course items table should be empty after clearAllTables", db.courseItemDao().getAllItems().first().isEmpty())
    }

    @Test
    fun testRowsNotSeenFor21DaysArePurged() = runBlocking {
        val now = System.currentTimeMillis()
        val twentyTwoDaysAgo = now - (22L * 24 * 3600 * 1000L)

        val oldRoutine = ClassRoutineEntity(
            courseCode = "MAT1101",
            courseName = "CALCULUS 1",
            section = "B",
            dayOfWeek = "Sunday",
            startTime = "11:00",
            endTime = "12:30",
            room = "2102",
            faculty = "Prof. Smith",
            lastSeen = twentyTwoDaysAgo
        )
        db.routineDao().insertRoutines(listOf(oldRoutine))

        val newMondayRoutine = ClassRoutineEntity(
            courseCode = "CSE1101",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            dayOfWeek = "Monday",
            startTime = "08:00",
            endTime = "10:30",
            room = "3101",
            faculty = "Dr. John",
            lastSeen = now
        )

        db.routineDao().syncRoutinesPerDay(listOf(newMondayRoutine), now)

        val routines = db.routineDao().getAllRoutinesDirect()
        assertEquals("Old routine row >21 days not seen should be purged", 1, routines.size)
        assertEquals("Monday", routines[0].dayOfWeek)
    }

    @Test
    fun testNoSeedingCodeInSrcMain() {
        val srcMainDir = File("src/main")
        assertTrue("src/main directory should exist", srcMainDir.exists())

        var foundSeedingMatches = false
        srcMainDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "kt") {
                val content = file.readText()
                if (content.contains("SampleDataGenerator") || content.contains("populateInitialDataIfEmpty")) {
                    foundSeedingMatches = true
                    println("Seeding match found in: ${file.path}")
                }
            }
        }

        assertFalse("No seeding or sample population code is permitted in src/main", foundSeedingMatches)
    }
}
