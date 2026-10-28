package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portal_notices")
data class NoticeEntity(
    @PrimaryKey
    val noticeId: String,
    val title: String,
    val publishDate: String,
    val category: String = "General",
    val contentSnippet: String = "",
    val fullContent: String = "",
    val isRead: Boolean = false,
    val sourceUrl: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
