package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM student_tasks ORDER BY dueDateMillis ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM student_tasks WHERE status != 'DONE' ORDER BY dueDateMillis ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM student_tasks WHERE status != 'DONE' ORDER BY dueDateMillis ASC LIMIT :limit")
    fun getUpcomingTasks(limit: Int = 3): Flow<List<TaskEntity>>

    @Query("SELECT * FROM student_tasks ORDER BY dueDateMillis ASC")
    suspend fun getAllTasksDirect(): List<TaskEntity>

    @Query("UPDATE student_tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: String)

    suspend fun updateTaskCompletion(taskId: Long, completed: Boolean) {
        updateTaskStatus(taskId, if (completed) "DONE" else "TODO")
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM student_tasks")
    suspend fun clearAll()
}
