package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CapturedPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedPageDao {
    @Query("SELECT * FROM captured_pages ORDER BY capturedAt DESC")
    fun getAllCapturedPages(): Flow<List<CapturedPageEntity>>

    @Query("SELECT * FROM captured_pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: Long): CapturedPageEntity?

    @Query("SELECT * FROM captured_pages ORDER BY capturedAt DESC LIMIT 1")
    fun getLatestCapturedPage(): Flow<CapturedPageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: CapturedPageEntity): Long

    @Delete
    suspend fun deletePage(page: CapturedPageEntity)

    @Query("DELETE FROM captured_pages WHERE id = :id")
    suspend fun deletePageById(id: Long)

    @Query("DELETE FROM captured_pages")
    suspend fun clearAll()
}
