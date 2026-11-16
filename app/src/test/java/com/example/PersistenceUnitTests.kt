package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UniTrackDatabase
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemType
import com.example.data.local.entity.CourseSettingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceUnitTests {

    private lateinit var context: Context
    private lateinit var db: UniTrackDatabase

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
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
        assertTrue("Course items table should be empty on fresh install", db.courseItemDao().getAllItems().first().isEmpty())
    }

    @Test
    fun testClearRoutinesAndKeepsUserItems() = runBlocking {
        val userItem = CourseItemEntity(
            courseKey = "cse1101_a",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            type = CourseItemType.QUIZ,
            title = "Quiz 1",
            dueAt = System.currentTimeMillis() + 86400000L,
            hasTime = true,
            topics = "Arrays",
            marks = 10.0,
            venue = "VLLAB",
            notes = "User note",
            status = "PENDING",
            scoreObtained = null,
            reminderOffsets = "1440",
            createdAt = System.currentTimeMillis()
        )
        db.courseItemDao().insertItem(userItem)

        val initialRoutine = ClassRoutineEntity(
            courseCode = "CSE1101",
            courseName = "COMPUTER PROGRAMMING",
            section = "A",
            dayOfWeek = "Monday",
            startTime = "08:00",
            endTime = "09:30",
            room = "VLLAB",
            faculty = "ABC",
            lastSeen = System.currentTimeMillis()
        )
        db.routineDao().insertRoutines(listOf(initialRoutine))
        db.routineDao().clearAll()

        val routines = db.routineDao().getAllRoutinesDirect()
        assertTrue("Routines should be cleared", routines.isEmpty())

        val items = db.courseItemDao().getAllItems().first()
        assertEquals("User created item should be kept", 1, items.size)
        assertEquals("Quiz 1", items[0].title)
    }

    @Test
    fun testCourseSettingEntityKey() {
        val key = CourseSettingEntity.makeKey("Object Oriented Programming", "A")
        assertEquals("object oriented programming_a", key)
    }
}
