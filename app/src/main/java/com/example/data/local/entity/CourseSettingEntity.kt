package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-class user preferences that survive sync operations.
 * Keyed by compound `courseKey`: "${courseName}_${section}".
 */
@Entity(tableName = "course_settings")
data class CourseSettingEntity(
    @PrimaryKey
    val courseKey: String,
    val courseName: String,
    val section: String,
    val notificationsEnabled: Boolean = true,
    val isDropped: Boolean = false,
    val facultyName: String? = null,
    val courseCode: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun makeKey(courseName: String, section: String): String {
            return "${courseName.trim().lowercase()}_${section.trim().lowercase()}"
        }
    }
}
