package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_routines")
data class ClassRoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseCode: String,
    val courseName: String,
    val section: String,
    /**
     * Day of week: "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
     */
    val dayOfWeek: String,
    /**
     * Start time in HH:mm format, e.g. "08:00", "09:30", "11:00", "14:00"
     */
    val startTime: String,
    /**
     * End time in HH:mm format, e.g. "09:30", "11:00", "12:30", "15:30"
     */
    val endTime: String,
    val room: String,
    val faculty: String,
    val semester: String = "Spring 2026-2027",
    val lastUpdated: Long = System.currentTimeMillis()
)
