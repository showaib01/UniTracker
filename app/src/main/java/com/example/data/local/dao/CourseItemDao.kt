package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CourseItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseItemDao {

    @Query("SELECT * FROM course_items ORDER BY dueAt ASC")
    fun getAllItems(): Flow<List<CourseItemEntity>>

    @Query("""
        SELECT i.* FROM course_items i 
        LEFT JOIN course_settings s ON i.courseKey = s.courseKey 
        WHERE s.isDropped IS NULL OR s.isDropped = 0 
        ORDER BY i.dueAt ASC
    """)
    fun getAllActiveItems(): Flow<List<CourseItemEntity>>

    @Query("""
        SELECT i.* FROM course_items i 
        LEFT JOIN course_settings s ON i.courseKey = s.courseKey 
        WHERE (s.isDropped IS NULL OR s.isDropped = 0) AND i.status = 'TODO' 
        ORDER BY i.dueAt ASC 
        LIMIT :limit
    """)
    fun getUpcomingActiveItems(limit: Int = 3): Flow<List<CourseItemEntity>>

    @Query("SELECT * FROM course_items WHERE courseKey = :courseKey ORDER BY dueAt ASC")
    fun getItemsForCourse(courseKey: String): Flow<List<CourseItemEntity>>

    @Query("SELECT * FROM course_items WHERE courseKey = :courseKey ORDER BY dueAt ASC")
    suspend fun getItemsForCourseDirect(courseKey: String): List<CourseItemEntity>

    @Query("SELECT * FROM course_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): CourseItemEntity?

    @Query("SELECT COUNT(*) FROM course_items WHERE courseKey = :courseKey AND type = :type")
    suspend fun countByTypeAndCourse(courseKey: String, type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CourseItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CourseItemEntity>)

    @Update
    suspend fun updateItem(item: CourseItemEntity)

    @Delete
    suspend fun deleteItem(item: CourseItemEntity)

    @Query("DELETE FROM course_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("UPDATE course_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE course_items SET scoreObtained = :score WHERE id = :id")
    suspend fun updateScore(id: Long, score: Double?)

    @Query("SELECT * FROM course_items WHERE status = 'TODO' AND dueAt > :nowMillis")
    suspend fun getPendingItemsDueAfter(nowMillis: Long): List<CourseItemEntity>
}
