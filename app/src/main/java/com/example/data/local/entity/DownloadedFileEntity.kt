package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_files")
data class DownloadedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseKey: String,
    val courseCode: String,
    val courseName: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceUrl: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
