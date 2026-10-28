package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY courseName ASC")
    fun getAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records ORDER BY courseName ASC")
    suspend fun getAllAttendanceDirect(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_records WHERE courseCode = :code LIMIT 1")
    suspend fun getByCourseCode(code: String): AttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AttendanceEntity>)

    @Update
    suspend fun updateRecord(record: AttendanceEntity)

    @Query("UPDATE attendance_records SET manualCorrection = manualCorrection + :delta WHERE courseCode = :courseCode")
    suspend fun adjustManualCorrection(courseCode: String, delta: Int)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()

    @Transaction
    suspend fun syncAttendancePreservingCorrections(newRecords: List<AttendanceEntity>) {
        if (newRecords.isEmpty()) return
        val existing = getAllAttendanceDirect().associateBy { it.courseCode }
        val toInsert = newRecords.map { newRecord ->
            val previous = existing[newRecord.courseCode]
            val preservedCorrection = previous?.manualCorrection ?: 0
            newRecord.copy(manualCorrection = preservedCorrection)
        }
        clearAll()
        insertAll(toInsert)
    }
}
