package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_pages")
data class CapturedPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val pageType: String, // e.g. "ROUTINE", "ATTENDANCE", "RESULTS", "NOTICES", "LOGIN", "OTHER"
    val htmlContent: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)
