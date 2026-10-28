package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CourseSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseSettingDao {

    @Query("SELECT * FROM course_settings WHERE courseKey = :courseKey")
    fun getSetting(courseKey: String): Flow<CourseSettingEntity?>

    @Query("SELECT * FROM course_settings WHERE courseKey = :courseKey")
    suspend fun getSettingDirect(courseKey: String): CourseSettingEntity?

    @Query("SELECT * FROM course_settings")
    fun getAllSettings(): Flow<List<CourseSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: CourseSettingEntity)

    @Query("UPDATE course_settings SET notificationsEnabled = :enabled WHERE courseKey = :courseKey")
    suspend fun setNotificationsEnabled(courseKey: String, enabled: Boolean)

    @Query("UPDATE course_settings SET isDropped = :isDropped WHERE courseKey = :courseKey")
    suspend fun setDropped(courseKey: String, isDropped: Boolean)

    @Query("UPDATE course_settings SET facultyName = :facultyName, courseCode = :courseCode WHERE courseKey = :courseKey")
    suspend fun updateDetails(courseKey: String, facultyName: String?, courseCode: String?)
}
