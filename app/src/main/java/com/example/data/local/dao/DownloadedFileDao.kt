package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DownloadedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedFileDao {
    @Query("SELECT * FROM downloaded_files ORDER BY downloadedAt DESC")
    fun getAllFiles(): Flow<List<DownloadedFileEntity>>

    @Query("SELECT * FROM downloaded_files ORDER BY downloadedAt DESC")
    suspend fun getAllFilesDirect(): List<DownloadedFileEntity>

    @Query("SELECT * FROM downloaded_files WHERE courseKey = :courseKey ORDER BY downloadedAt DESC")
    fun getFilesByCourse(courseKey: String): Flow<List<DownloadedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: DownloadedFileEntity): Long

    @Delete
    suspend fun deleteFile(file: DownloadedFileEntity)

    @Query("DELETE FROM downloaded_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM downloaded_files WHERE filePath = :path")
    suspend fun deleteByFilePath(path: String)
}
