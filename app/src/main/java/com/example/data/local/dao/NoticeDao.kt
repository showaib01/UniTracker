package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.NoticeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoticeDao {
    @Query("SELECT * FROM portal_notices ORDER BY publishDate DESC, lastUpdated DESC")
    fun getAllNotices(): Flow<List<NoticeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotices(notices: List<NoticeEntity>)

    @Update
    suspend fun updateNotice(notice: NoticeEntity)

    @Query("UPDATE portal_notices SET isRead = :isRead WHERE noticeId = :id")
    suspend fun setReadStatus(id: String, isRead: Boolean)

    @Query("DELETE FROM portal_notices")
    suspend fun clearAll()
}
