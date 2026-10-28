package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey
    val courseCode: String,
    val courseName: String,
    val section: String,
    val attendedCount: Int,
    val totalClasses: Int,
    val manualCorrection: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val effectiveAttended: Int
        get() = (attendedCount + manualCorrection).coerceAtLeast(0)

    val percentage: Float
        get() = if (totalClasses > 0) {
            (effectiveAttended.toFloat() / totalClasses) * 100f
        } else 100f
}
