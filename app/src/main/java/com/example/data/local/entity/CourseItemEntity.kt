package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object CourseItemType {
    const val QUIZ = "QUIZ"
    const val ASSIGNMENT = "ASSIGNMENT"
    const val EXAM_MID = "EXAM_MID"
    const val EXAM_FINAL = "EXAM_FINAL"
    const val LAB_REPORT = "LAB_REPORT"
    const val PROJECT = "PROJECT"
    const val PRESENTATION = "PRESENTATION"
    const val OTHER = "OTHER"

    val ALL = listOf(
        QUIZ,
        ASSIGNMENT,
        EXAM_MID,
        EXAM_FINAL,
        LAB_REPORT,
        PROJECT,
        PRESENTATION,
        OTHER
    )

    fun displayName(type: String): String = when (type.uppercase()) {
        QUIZ -> "Quiz"
        ASSIGNMENT -> "Assignment"
        EXAM_MID -> "Mid Exam"
        EXAM_FINAL -> "Final Exam"
        LAB_REPORT -> "Lab Report"
        PROJECT -> "Project"
        PRESENTATION -> "Presentation"
        else -> "Other"
    }
}

object CourseItemStatus {
    const val TODO = "TODO"
    const val DONE = "DONE"
    const val MISSED = "MISSED"
}

@Entity(tableName = "course_items")
data class CourseItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseKey: String, // courseName_section e.g. "OBJECT ORIENTED PROGRAMMING 2_V"
    val courseName: String,
    val section: String = "",
    val type: String, // QUIZ | ASSIGNMENT | EXAM_MID | EXAM_FINAL | LAB_REPORT | PROJECT | PRESENTATION | OTHER
    val title: String,
    val dueAt: Long, // epoch millis; date + optional time
    val hasTime: Boolean = false,
    val topics: String = "",
    val marks: Double? = null,
    val venue: String = "",
    val notes: String = "",
    val status: String = CourseItemStatus.TODO, // TODO | DONE | MISSED
    val scoreObtained: Double? = null,
    val reminderOffsets: String = "1440,120", // comma-separated minutes before dueAt (e.g. 1440 = 1 day, 120 = 2 hours)
    val createdAt: Long = System.currentTimeMillis()
) {
    val isCompleted: Boolean
        get() = status.equals(CourseItemStatus.DONE, ignoreCase = true)

    fun getReminderOffsetsList(): List<Int> {
        if (reminderOffsets.isBlank()) return emptyList()
        return reminderOffsets.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    companion object {
        fun makeCourseKey(courseName: String, section: String): String {
            return "${courseName.trim().uppercase()}_${section.trim().uppercase()}"
        }
    }
}
