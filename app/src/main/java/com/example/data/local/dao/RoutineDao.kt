package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.ClassRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("""
        SELECT r.* FROM class_routines r
        LEFT JOIN course_settings s ON (LOWER(TRIM(r.courseName)) || '_' || LOWER(TRIM(r.section))) = s.courseKey
        WHERE s.isDropped IS NULL OR s.isDropped = 0
        ORDER BY r.dayOfWeek, r.startTime
    """)
    fun getAllRoutines(): Flow<List<ClassRoutineEntity>>

    @Query("""
        SELECT r.* FROM class_routines r
        LEFT JOIN course_settings s ON (LOWER(TRIM(r.courseName)) || '_' || LOWER(TRIM(r.section))) = s.courseKey
        WHERE s.isDropped IS NULL OR s.isDropped = 0
        ORDER BY r.dayOfWeek, r.startTime
    """)
    suspend fun getAllRoutinesDirect(): List<ClassRoutineEntity>

    @Query("""
        SELECT r.* FROM class_routines r
        LEFT JOIN course_settings s ON (LOWER(TRIM(r.courseName)) || '_' || LOWER(TRIM(r.section))) = s.courseKey
        WHERE r.dayOfWeek = :day AND (s.isDropped IS NULL OR s.isDropped = 0)
        ORDER BY r.startTime ASC
    """)
    fun getRoutinesForDay(day: String): Flow<List<ClassRoutineEntity>>

    @Query("""
        SELECT r.* FROM class_routines r
        LEFT JOIN course_settings s ON (LOWER(TRIM(r.courseName)) || '_' || LOWER(TRIM(r.section))) = s.courseKey
        WHERE r.dayOfWeek = :day AND (s.isDropped IS NULL OR s.isDropped = 0)
        ORDER BY r.startTime ASC
    """)
    suspend fun getRoutinesForDayDirect(day: String): List<ClassRoutineEntity>

    @Query("""
        SELECT r.* FROM class_routines r
        LEFT JOIN course_settings s ON (LOWER(TRIM(r.courseName)) || '_' || LOWER(TRIM(r.section))) = s.courseKey
        WHERE r.dayOfWeek = :day 
          AND (s.isDropped IS NULL OR s.isDropped = 0)
          AND (s.notificationsEnabled IS NULL OR s.notificationsEnabled = 1)
        ORDER BY r.startTime ASC
    """)
    suspend fun getActiveRoutinesForDayWithNotifications(day: String): List<ClassRoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(routines: List<ClassRoutineEntity>)

    @Query("DELETE FROM class_routines")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAllRoutines(routines: List<ClassRoutineEntity>) {
        if (routines.isNotEmpty()) {
            clearAll()
            insertRoutines(routines)
        }
    }
}
