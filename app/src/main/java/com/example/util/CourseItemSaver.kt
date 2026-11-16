package com.example.util

import com.example.UniTrackApplication
import com.example.data.local.entity.CourseItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CourseItemSaver {
    suspend fun saveItem(app: UniTrackApplication, item: CourseItemEntity): Long = withContext(Dispatchers.IO) {
        val courseItemDao = app.database.courseItemDao()
        val id = if (item.id == 0L) {
            courseItemDao.insertItem(item)
        } else {
            courseItemDao.updateItem(item)
            item.id
        }
        CourseItemReminderScheduler.scheduleItemReminders(app, item.copy(id = id))
        id
    }
}
