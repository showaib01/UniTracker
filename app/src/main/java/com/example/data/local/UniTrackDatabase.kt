package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.CapturedPageDao
import com.example.data.local.dao.CourseItemDao
import com.example.data.local.dao.CourseSettingDao
import com.example.data.local.dao.NoticeDao
import com.example.data.local.dao.ResultDao
import com.example.data.local.dao.RoutineDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.CapturedPageEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.NoticeEntity
import com.example.data.local.entity.ResultEntity
import com.example.data.local.entity.TaskEntity

@Database(
    entities = [
        ClassRoutineEntity::class,
        AttendanceEntity::class,
        ResultEntity::class,
        NoticeEntity::class,
        TaskEntity::class,
        CapturedPageEntity::class,
        CourseSettingEntity::class,
        CourseItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class UniTrackDatabase : RoomDatabase() {

    abstract fun routineDao(): RoutineDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun resultDao(): ResultDao
    abstract fun noticeDao(): NoticeDao
    abstract fun taskDao(): TaskDao
    abstract fun capturedPageDao(): CapturedPageDao
    abstract fun courseSettingDao(): CourseSettingDao
    abstract fun courseItemDao(): CourseItemDao

    companion object {
        @Volatile
        private var INSTANCE: UniTrackDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `course_settings` (
                        `courseKey` TEXT NOT NULL PRIMARY KEY,
                        `courseName` TEXT NOT NULL,
                        `section` TEXT NOT NULL,
                        `notificationsEnabled` INTEGER NOT NULL DEFAULT 1,
                        `isDropped` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `course_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `courseKey` TEXT NOT NULL,
                        `courseName` TEXT NOT NULL,
                        `section` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `hasTime` INTEGER NOT NULL,
                        `topics` TEXT NOT NULL,
                        `marks` REAL,
                        `venue` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `scoreObtained` REAL,
                        `reminderOffsets` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Safely migrate existing tasks from student_tasks table if it exists
                db.execSQL("""
                    INSERT INTO `course_items` (
                        `id`, `courseKey`, `courseName`, `section`, `type`, `title`, 
                        `dueAt`, `hasTime`, `topics`, `marks`, `venue`, `notes`, 
                        `status`, `scoreObtained`, `reminderOffsets`, `createdAt`
                    )
                    SELECT 
                        `id`, 
                        `courseName` || '_', 
                        `courseName`, 
                        '', 
                        CASE 
                            WHEN `taskType` = 'MID_EXAM' THEN 'EXAM_MID'
                            WHEN `taskType` = 'FINAL_EXAM' THEN 'EXAM_FINAL'
                            WHEN `taskType` IN ('QUIZ', 'ASSIGNMENT', 'PROJECT') THEN `taskType`
                            ELSE 'OTHER'
                        END, 
                        `title`, 
                        `dueDateMillis`, 
                        1, 
                        '', 
                        NULL, 
                        '', 
                        `notes`, 
                        `status`, 
                        NULL, 
                        '1440,120', 
                        `createdAt` 
                    FROM `student_tasks`
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `course_settings` ADD COLUMN `facultyName` TEXT")
                db.execSQL("ALTER TABLE `course_settings` ADD COLUMN `courseCode` TEXT")
            }
        }

        fun getDatabase(context: Context): UniTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UniTrackDatabase::class.java,
                    "unitrack_aiub.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
