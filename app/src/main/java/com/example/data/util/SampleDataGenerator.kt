package com.example.data.util

import com.example.data.local.UniTrackDatabase
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.NoticeEntity
import com.example.data.local.entity.ResultEntity
import com.example.data.local.entity.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SampleDataGenerator {

    fun getCurrentDayOfWeek(): String {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("EEEE", Locale.ENGLISH)
        return format.format(calendar.time)
    }

    suspend fun populateInitialDataIfEmpty(db: UniTrackDatabase) {
        val currentDay = getCurrentDayOfWeek()
        val existingToday = db.routineDao().getRoutinesForDayDirect(currentDay)
        if (existingToday.isNotEmpty()) return

        val routineSamples = listOf(
            ClassRoutineEntity(
                courseCode = "CSC 3211",
                courseName = "Operating Systems",
                section = "A",
                dayOfWeek = "Sunday",
                startTime = "08:00 AM",
                endTime = "09:30 AM",
                room = "DS0201",
                faculty = "Dr. M. Rahman (MRH)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3212",
                courseName = "Operating Systems Lab",
                section = "A1",
                dayOfWeek = "Sunday",
                startTime = "09:30 AM",
                endTime = "11:00 AM",
                room = "Lab 4 (Annex 1)",
                faculty = "Dr. M. Rahman (MRH)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3222",
                courseName = "Algorithms",
                section = "B",
                dayOfWeek = "Sunday",
                startTime = "12:30 PM",
                endTime = "02:00 PM",
                room = "DS0304",
                faculty = "Prof. S. Ahmed (SAH)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3230",
                courseName = "Web Technologies",
                section = "C",
                dayOfWeek = "Monday",
                startTime = "09:30 AM",
                endTime = "11:00 AM",
                room = "DS0102",
                faculty = "Engr. K. Hasan (KHS)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 4140",
                courseName = "Artificial Intelligence",
                section = "A",
                dayOfWeek = "Monday",
                startTime = "02:00 PM",
                endTime = "03:30 PM",
                room = "DS0205",
                faculty = "Dr. F. Alam (FAM)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3211",
                courseName = "Operating Systems",
                section = "A",
                dayOfWeek = "Tuesday",
                startTime = "08:00 AM",
                endTime = "09:30 AM",
                room = "DS0201",
                faculty = "Dr. M. Rahman (MRH)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3222",
                courseName = "Algorithms",
                section = "B",
                dayOfWeek = "Tuesday",
                startTime = "12:30 PM",
                endTime = "02:00 PM",
                room = "DS0304",
                faculty = "Prof. S. Ahmed (SAH)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3230",
                courseName = "Web Technologies",
                section = "C",
                dayOfWeek = "Wednesday",
                startTime = "09:30 AM",
                endTime = "11:00 AM",
                room = "DS0102",
                faculty = "Engr. K. Hasan (KHS)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 4140",
                courseName = "Artificial Intelligence",
                section = "A",
                dayOfWeek = "Wednesday",
                startTime = "02:00 PM",
                endTime = "03:30 PM",
                room = "DS0205",
                faculty = "Dr. F. Alam (FAM)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3240",
                courseName = "Software Engineering",
                section = "B",
                dayOfWeek = "Thursday",
                startTime = "11:00 AM",
                endTime = "12:30 PM",
                room = "DS0402",
                faculty = "Ms. T. Kabir (TKB)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3231",
                courseName = "Web Technologies Lab",
                section = "C1",
                dayOfWeek = "Thursday",
                startTime = "02:00 PM",
                endTime = "04:00 PM",
                room = "CL03 (Annex 3)",
                faculty = "Engr. K. Hasan (KHS)"
            ),
            ClassRoutineEntity(
                courseCode = "CSC 3290",
                courseName = "Research Methodology",
                section = "A",
                dayOfWeek = "Friday",
                startTime = "09:30 AM",
                endTime = "11:00 AM",
                room = "Auditorium Annex",
                faculty = "Dr. N. Islam (NIS)"
            ),
            ClassRoutineEntity(
                courseCode = "MAT 2101",
                courseName = "Complex Variable & Laplace",
                section = "D",
                dayOfWeek = "Saturday",
                startTime = "11:00 AM",
                endTime = "12:30 PM",
                room = "DS0108",
                faculty = "Dr. R. Chowdhury (RCH)"
            )
        )
        db.routineDao().insertRoutines(routineSamples)

        val attendanceSamples = listOf(
            AttendanceEntity(
                courseCode = "CSC 3211",
                courseName = "Operating Systems",
                section = "A",
                attendedCount = 14,
                totalClasses = 16
            ),
            AttendanceEntity(
                courseCode = "CSC 3222",
                courseName = "Algorithms",
                section = "B",
                attendedCount = 11,
                totalClasses = 15
            ),
            AttendanceEntity(
                courseCode = "CSC 3230",
                courseName = "Web Technologies",
                section = "C",
                attendedCount = 15,
                totalClasses = 16
            ),
            AttendanceEntity(
                courseCode = "CSC 4140",
                courseName = "Artificial Intelligence",
                section = "A",
                attendedCount = 13,
                totalClasses = 15
            ),
            AttendanceEntity(
                courseCode = "CSC 3240",
                courseName = "Software Engineering",
                section = "B",
                attendedCount = 9,
                totalClasses = 13
            )
        )
        db.attendanceDao().insertAll(attendanceSamples)

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val taskSamples = listOf(
            TaskEntity(
                title = "OS Process Scheduling Simulation",
                courseName = "Operating Systems",
                dueDateMillis = now + oneDay * 2,
                priority = "HIGH",
                status = "TODO",
                taskType = "ASSIGNMENT",
                notes = "Implement Round Robin and FCFS algorithms in C/C++"
            ),
            TaskEntity(
                title = "Algorithms Midterm Quiz 2",
                courseName = "Algorithms",
                dueDateMillis = now + oneDay * 4,
                priority = "HIGH",
                status = "TODO",
                taskType = "QUIZ",
                notes = "Topics: Dynamic Programming & Greedy Algorithms"
            ),
            TaskEntity(
                title = "AIUB Portal Clone Frontend",
                courseName = "Web Technologies",
                dueDateMillis = now + oneDay * 6,
                priority = "MEDIUM",
                status = "DOING",
                taskType = "PROJECT",
                notes = "Responsive layout and student session handling"
            )
        )
        db.taskDao().insertTasks(taskSamples)

        val noticeSamples = listOf(
            NoticeEntity(
                noticeId = "aiub_notice_101",
                title = "Spring 2026-2027 Midterm Exam Schedule Published",
                publishDate = "Oct 25, 2026",
                category = "Examination",
                contentSnippet = "The midterm examination schedule for undergraduate programs has been published on the student portal.",
                fullContent = "All students are requested to check their individual exam routine and room allocation. Admit cards must be presented during examinations."
            ),
            NoticeEntity(
                noticeId = "aiub_notice_102",
                title = "Library Hours Extension During Exam Week",
                publishDate = "Oct 22, 2026",
                category = "Campus Facilities",
                contentSnippet = "Central Library study area will remain open until 9:00 PM on weekdays.",
                fullContent = "In order to facilitate student exam preparation, library facilities will extend operational hours."
            )
        )
        db.noticeDao().insertNotices(noticeSamples)

        val resultSamples = listOf(
            ResultEntity(
                semester = "Fall 2025-2026",
                courseCode = "CSC 2210",
                courseTitle = "Data Structures",
                credits = 3.0,
                grade = "A+",
                gradePoint = 4.0,
                semesterGpa = 3.88,
                cumulativeCgpa = 3.82
            ),
            ResultEntity(
                semester = "Fall 2025-2026",
                courseCode = "CSC 2211",
                courseTitle = "Data Structures Lab",
                credits = 1.0,
                grade = "A+",
                gradePoint = 4.0,
                semesterGpa = 3.88,
                cumulativeCgpa = 3.82
            ),
            ResultEntity(
                semester = "Fall 2025-2026",
                courseCode = "MAT 1205",
                courseTitle = "Linear Algebra & Vector Analysis",
                credits = 3.0,
                grade = "A",
                gradePoint = 3.75,
                semesterGpa = 3.88,
                cumulativeCgpa = 3.82
            )
        )
        db.resultDao().insertResults(resultSamples)
    }
}
