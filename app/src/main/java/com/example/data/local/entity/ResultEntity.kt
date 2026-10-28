package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "academic_results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semester: String,
    val courseCode: String,
    val courseTitle: String,
    val credits: Double,
    val grade: String,
    val gradePoint: Double,
    val semesterGpa: Double = 0.0,
    val cumulativeCgpa: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)
