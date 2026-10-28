package com.example.ui.screens.today

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.UniTrackApplication
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.sync.PortalSyncWorker
import com.example.ui.components.CoursePickerOption
import com.example.util.CourseItemReminderScheduler
import com.example.util.DateTimeUtils
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayChipItem(
    val dayOfWeek: String,
    val dayAbbr: String,
    val dateNumber: String,
    val fullDateFormatted: String,
    val isToday: Boolean,
    val classCount: Int
)

data class TodayUiState(
    val dayOfWeek: String = "",
    val dateHeadline: String = "",
    val selectedDay: String = "",
    val selectedDayHeadline: String = "",
    val isViewingToday: Boolean = true,
    val weekDays: List<DayChipItem> = emptyList(),
    val classes: List<ClassRoutineEntity> = emptyList(),
    val classesForSelectedDay: List<ClassRoutineEntity> = emptyList(),
    // Hero Card: LIVE NOW
    val liveNowClass: ClassRoutineEntity? = null,
    val liveNowRemainingMinutes: Int = 0,
    val liveNowProgress: Float = 0f,
    // Hero Card: NEXT CLASS TODAY
    val upNextClass: ClassRoutineEntity? = null,
    val upNextMinutes: Int = 0,
    val firstClassTomorrow: ClassRoutineEntity? = null,
    // Due Soon Items (next 3)
    val dueSoonItems: List<CourseItemEntity> = emptyList(),
    val overdueAndDueTodayCount: Int = 0,
    val attendanceAlerts: List<AttendanceEntity> = emptyList(),
    val lastSyncedText: String = "Never",
    val isSessionActive: Boolean = false,
    val isSyncing: Boolean = false,
    val isOffline: Boolean = false,
    val syncMessage: String? = null,
    val facultyNamesByCourseKey: Map<String, String> = emptyMap(),
    // Class Detail Sheet
    val selectedClass: ClassRoutineEntity? = null,
    val selectedClassAttendance: AttendanceEntity? = null,
    val selectedClassTasks: List<TaskEntity> = emptyList(),
    val selectedClassReminderEnabled: Boolean = true,
    // Add/Edit Item Sheet
    val showAddEditSheet: Boolean = false,
    val addEditItem: CourseItemEntity? = null,
    val addEditCourseKey: String? = null,
    val addEditInitialType: String? = null,
    val availableCourses: List<CoursePickerOption> = emptyList(),
    // Theme
    val themeMode: String = "DARK"
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val routineDao = app.database.routineDao()
    private val attendanceDao = app.database.attendanceDao()
    private val taskDao = app.database.taskDao()
    private val courseItemDao = app.database.courseItemDao()
    private val courseSettingDao = app.database.courseSettingDao()
    private val preferencesRepository = app.preferencesRepository
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var syncObservationJob: Job? = null
    private var allRoutinesCache: List<ClassRoutineEntity> = emptyList()

    init {
        val today = DateTimeUtils.getCurrentDayOfWeek()
        _uiState.update {
            it.copy(
                dayOfWeek = today,
                selectedDay = today,
                dateHeadline = DateTimeUtils.formatDateHeadline(),
                selectedDayHeadline = DateTimeUtils.formatDateHeadline()
            )
        }
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            // Emits every 30 seconds for live countdown and class progress updates
            val tickerFlow = flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(30_000L)
                }
            }

            val settingsAndPrefsFlow = combine(
                courseSettingDao.getAllSettings(),
                preferencesRepository.userPreferencesFlow,
                tickerFlow
            ) { settings, prefs, _ ->
                Pair(settings, prefs)
            }

            combine(
                routineDao.getAllRoutines(),
                courseItemDao.getUpcomingActiveItems(10),
                attendanceDao.getAllAttendance(),
                settingsAndPrefsFlow
            ) { routines, dueItems, attendanceList, (settingsList, preferences) ->
                allRoutinesCache = routines

                val today = DateTimeUtils.getCurrentDayOfWeek()
                val currentSelectedDay = _uiState.value.selectedDay.ifBlank { today }
                val isToday = currentSelectedDay.equals(today, ignoreCase = true)

                // 7-day calendar items
                val weekDays = generateWeekDays(routines)

                // Classes for today (for hero card calculations)
                val todayClasses = routines.filter { it.dayOfWeek.equals(today, ignoreCase = true) }
                // Classes for currently selected day
                val selectedDayClasses = routines.filter { it.dayOfWeek.equals(currentSelectedDay, ignoreCase = true) }
                    .sortedBy { it.startTime }

                var liveNow: ClassRoutineEntity? = null
                var liveRemaining = 0
                var liveProgress = 0f
                val upcomingToday = mutableListOf<Pair<ClassRoutineEntity, Int>>()

                for (routine in todayClasses) {
                    val timing = DateTimeUtils.getClassTimingStatus(routine.startTime, routine.endTime)
                    when (timing.first) {
                        "IN_PROGRESS" -> {
                            liveNow = routine
                            liveRemaining = timing.second
                            liveProgress = timing.third
                        }
                        "UPCOMING" -> {
                            upcomingToday.add(routine to timing.second)
                        }
                    }
                }

                // Up next is the first upcoming class today
                val upNextPair = upcomingToday.minByOrNull { it.second }
                val upNext = upNextPair?.first
                val upNextMins = upNextPair?.second ?: 0

                // Tomorrow's first class (if no active or upcoming classes today)
                val tomorrowDay = getTomorrowDayOfWeek(today)
                val tomorrowClasses = routines.filter { it.dayOfWeek.equals(tomorrowDay, ignoreCase = true) }
                val firstTomorrow = tomorrowClasses.minByOrNull { it.startTime }

                val threshold = preferences.attendanceThreshold
                val alerts = attendanceList.filter {
                    it.totalClasses > 0 && it.percentage < threshold
                }

                // Count overdue and due today
                val overdueOrDueTodayCount = dueItems.count {
                    val info = DateTimeUtils.formatDueBadgeText(it.dueAt, it.hasTime)
                    info.urgency == "OVERDUE" || info.diffDays == 0
                }

                val lastSync = DateTimeUtils.formatLastSynced(preferences.lastSyncTimestamp)
                val isOffline = !isNetworkAvailable()

                // Faculty names map
                val facultyMap = settingsList.filter { !it.facultyName.isNullOrBlank() }
                    .associate { it.courseKey to (it.facultyName ?: "") }

                // Available courses for picker
                val courseOptions = routines.groupBy {
                    CourseSettingEntity.makeKey(it.courseName, it.section)
                }.map { (key, rList) ->
                    val firstR = rList.first()
                    CoursePickerOption(
                        courseKey = key,
                        courseName = firstR.courseName,
                        section = firstR.section,
                        routineDays = rList.map { it.dayOfWeek },
                        room = firstR.room
                    )
                }

                _uiState.update { current ->
                    current.copy(
                        dayOfWeek = today,
                        selectedDay = currentSelectedDay,
                        isViewingToday = isToday,
                        weekDays = weekDays,
                        classes = todayClasses,
                        classesForSelectedDay = selectedDayClasses,
                        liveNowClass = liveNow,
                        liveNowRemainingMinutes = liveRemaining,
                        liveNowProgress = liveProgress,
                        upNextClass = upNext,
                        upNextMinutes = upNextMins,
                        firstClassTomorrow = firstTomorrow,
                        dueSoonItems = dueItems.take(3),
                        overdueAndDueTodayCount = overdueOrDueTodayCount,
                        attendanceAlerts = alerts,
                        lastSyncedText = lastSync,
                        isSessionActive = preferences.isSessionActive,
                        isOffline = isOffline,
                        facultyNamesByCourseKey = facultyMap,
                        availableCourses = courseOptions,
                        themeMode = preferences.themeMode
                    )
                }
            }.collect {}
        }
    }

    private fun generateWeekDays(allRoutines: List<ClassRoutineEntity>): List<DayChipItem> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val todayCal = Calendar.getInstance()
        val todayDay = SimpleDateFormat("EEEE", Locale.ENGLISH).format(todayCal.time)
        val todayDate = todayCal.get(Calendar.DAY_OF_MONTH)

        val items = mutableListOf<DayChipItem>()
        val abbrFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val dayFormat = SimpleDateFormat("EEEE", Locale.ENGLISH)
        val fullDateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH)

        for (i in 0 until 7) {
            val dayName = dayFormat.format(cal.time)
            val abbr = abbrFormat.format(cal.time)
            val dateNum = cal.get(Calendar.DAY_OF_MONTH).toString()
            val fullDate = fullDateFormat.format(cal.time)
            val isToday = dayName.equals(todayDay, ignoreCase = true) && (cal.get(Calendar.DAY_OF_MONTH) == todayDate)
            val count = allRoutines.count { it.dayOfWeek.equals(dayName, ignoreCase = true) }

            items.add(
                DayChipItem(
                    dayOfWeek = dayName,
                    dayAbbr = abbr,
                    dateNumber = dateNum,
                    fullDateFormatted = fullDate,
                    isToday = isToday,
                    classCount = count
                )
            )
            cal.add(Calendar.DAY_OF_WEEK, 1)
        }
        return items
    }

    fun selectDay(dayOfWeek: String) {
        val today = DateTimeUtils.getCurrentDayOfWeek()
        val isToday = dayOfWeek.equals(today, ignoreCase = true)
        val selectedClasses = allRoutinesCache.filter { it.dayOfWeek.equals(dayOfWeek, ignoreCase = true) }
            .sortedBy { it.startTime }

        val weekDayItem = _uiState.value.weekDays.firstOrNull { it.dayOfWeek.equals(dayOfWeek, ignoreCase = true) }
        val headline = weekDayItem?.fullDateFormatted ?: dayOfWeek

        _uiState.update {
            it.copy(
                selectedDay = dayOfWeek,
                selectedDayHeadline = headline,
                isViewingToday = isToday,
                classesForSelectedDay = selectedClasses
            )
        }
    }

    fun selectToday() {
        val today = DateTimeUtils.getCurrentDayOfWeek()
        selectDay(today)
    }

    fun selectPreviousDay() {
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentIndex = days.indexOfFirst { it.equals(_uiState.value.selectedDay, ignoreCase = true) }
        if (currentIndex >= 0) {
            val prevIndex = if (currentIndex == 0) days.size - 1 else currentIndex - 1
            selectDay(days[prevIndex])
        }
    }

    fun selectNextDay() {
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentIndex = days.indexOfFirst { it.equals(_uiState.value.selectedDay, ignoreCase = true) }
        if (currentIndex >= 0) {
            val nextIndex = (currentIndex + 1) % days.size
            selectDay(days[nextIndex])
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val currentMode = _uiState.value.themeMode
            val newMode = if (currentMode == "LIGHT") "DARK" else "LIGHT"
            preferencesRepository.updateThemeMode(newMode)
            _uiState.update { it.copy(themeMode = newMode) }
        }
    }

    private fun getTomorrowDayOfWeek(currentDay: String): String {
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentIndex = days.indexOfFirst { it.equals(currentDay, ignoreCase = true) }
        return if (currentIndex >= 0) {
            days[(currentIndex + 1) % days.size]
        } else "Sunday"
    }

    fun triggerSync() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true, syncMessage = "Syncing schedule...") }

        val request = PortalSyncWorker.createOneTimeWorkRequest()
        workManager.enqueue(request)

        syncObservationJob?.cancel()
        syncObservationJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            _uiState.update { it.copy(isSyncing = true) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val msg = workInfo.outputData.getString("SYNC_MESSAGE") ?: "Synced successfully"
                            _uiState.update { it.copy(isSyncing = false, syncMessage = msg) }
                            delay(3000)
                            _uiState.update { it.copy(syncMessage = null) }
                        }
                        WorkInfo.State.FAILED -> {
                            val err = workInfo.outputData.getString("SYNC_ERROR") ?: "Sync failed"
                            _uiState.update { it.copy(isSyncing = false, syncMessage = err) }
                            delay(4000)
                            _uiState.update { it.copy(syncMessage = null) }
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(isSyncing = false, syncMessage = null) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun selectClass(routine: ClassRoutineEntity?) {
        if (routine == null) {
            _uiState.update {
                it.copy(
                    selectedClass = null,
                    selectedClassAttendance = null,
                    selectedClassTasks = emptyList()
                )
            }
            return
        }

        viewModelScope.launch {
            val allAttendance = attendanceDao.getAllAttendanceDirect()
            val att = allAttendance.firstOrNull {
                it.courseName.equals(routine.courseName, ignoreCase = true) &&
                        it.section.equals(routine.section, ignoreCase = true)
            } ?: allAttendance.firstOrNull {
                it.courseName.equals(routine.courseName, ignoreCase = true)
            }

            val allTasks = taskDao.getAllTasksDirect()
            val tasks = allTasks.filter {
                it.courseName.equals(routine.courseName, ignoreCase = true)
            }

            val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
            val setting = courseSettingDao.getSettingDirect(key)
            val isRemEnabled = setting?.notificationsEnabled ?: true

            _uiState.update {
                it.copy(
                    selectedClass = routine,
                    selectedClassAttendance = att,
                    selectedClassTasks = tasks,
                    selectedClassReminderEnabled = isRemEnabled
                )
            }
        }
    }

    fun toggleCourseReminder(enabled: Boolean) {
        val routine = _uiState.value.selectedClass ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
            val existing = courseSettingDao.getSettingDirect(key)
            if (existing != null) {
                courseSettingDao.setNotificationsEnabled(key, enabled)
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = key,
                        courseName = routine.courseName,
                        section = routine.section,
                        notificationsEnabled = enabled,
                        isDropped = false
                    )
                )
            }
            _uiState.update { it.copy(selectedClassReminderEnabled = enabled) }
        }
    }

    fun openAddItemForCourse(courseKey: String?, type: String? = null) {
        _uiState.update {
            it.copy(
                showAddEditSheet = true,
                addEditItem = null,
                addEditCourseKey = courseKey,
                addEditInitialType = type
            )
        }
    }

    fun openEditItem(item: CourseItemEntity) {
        _uiState.update {
            it.copy(
                showAddEditSheet = true,
                addEditItem = item,
                addEditCourseKey = item.courseKey,
                addEditInitialType = item.type
            )
        }
    }

    fun dismissAddEditSheet() {
        _uiState.update {
            it.copy(
                showAddEditSheet = false,
                addEditItem = null,
                addEditCourseKey = null,
                addEditInitialType = null
            )
        }
    }

    fun saveCourseItem(item: CourseItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = if (item.id == 0L) {
                courseItemDao.insertItem(item)
            } else {
                courseItemDao.updateItem(item)
                item.id
            }
            CourseItemReminderScheduler.scheduleItemReminders(app, item.copy(id = id))
            dismissAddEditSheet()
        }
    }

    fun deleteCourseItem(item: CourseItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            courseItemDao.deleteItem(item)
            CourseItemReminderScheduler.cancelItemReminders(app, item.id)
            dismissAddEditSheet()
        }
    }

    fun toggleItemDone(item: CourseItemEntity, isDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (isDone) CourseItemStatus.DONE else CourseItemStatus.TODO
            courseItemDao.updateStatus(item.id, newStatus)
            if (isDone) {
                CourseItemReminderScheduler.cancelItemReminders(app, item.id)
            } else {
                CourseItemReminderScheduler.scheduleItemReminders(app, item.copy(status = newStatus))
            }
        }
    }

    fun toggleTask(task: TaskEntity, completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.updateTaskCompletion(task.id, completed)
            val routine = _uiState.value.selectedClass ?: return@launch
            val allTasks = taskDao.getAllTasksDirect()
            val tasks = allTasks.filter {
                it.courseName.equals(routine.courseName, ignoreCase = true)
            }
            _uiState.update { it.copy(selectedClassTasks = tasks) }
        }
    }

    fun addTaskForCourse(title: String) {
        val routine = _uiState.value.selectedClass ?: return
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insertTask(
                TaskEntity(
                    title = title.trim(),
                    courseName = routine.courseName,
                    dueDateMillis = System.currentTimeMillis() + 86400000L * 3,
                    priority = "MEDIUM",
                    taskType = "ASSIGNMENT"
                )
            )
            val allTasks = taskDao.getAllTasksDirect()
            val tasks = allTasks.filter {
                it.courseName.equals(routine.courseName, ignoreCase = true)
            }
            _uiState.update { it.copy(selectedClassTasks = tasks) }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
