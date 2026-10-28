package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "student_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val courseName: String,
    val dueDateMillis: Long,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val status: String = "TODO",     // TODO, DOING, DONE
    val taskType: String = "ASSIGNMENT", // ASSIGNMENT, QUIZ, MID_EXAM, FINAL_EXAM, PROJECT
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isCompleted: Boolean
        get() = status.equals("DONE", ignoreCase = true)
}
