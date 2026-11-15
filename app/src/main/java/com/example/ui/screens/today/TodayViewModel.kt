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
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.sync.PortalSyncWorker
import com.example.ui.components.CoursePickerOption
import com.example.util.CourseItemReminderScheduler
import com.example.util.CourseMeetingSlot
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
    val hasAnyRoutines: Boolean = false,
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
    val lastSyncedText: String = "Never",
    val isSessionActive: Boolean = false,
    val isSyncing: Boolean = false,
    val isOffline: Boolean = false,
    val syncMessage: String? = null,
    val facultyNamesByCourseKey: Map<String, String> = emptyMap(),
    // Class Detail Sheet
    val selectedClass: ClassRoutineEntity? = null,
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
                settingsAndPrefsFlow
            ) { routines, dueItems, (settingsList, preferences) ->
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
                        meetingSlots = rList.map { CourseMeetingSlot(it.dayOfWeek, it.startTime, it.endTime) },
                        room = firstR.room
                    )
                }

                _uiState.update { current ->
                    current.copy(
                        dayOfWeek = today,
                        selectedDay = currentSelectedDay,
                        isViewingToday = isToday,
                        hasAnyRoutines = routines.isNotEmpty(),
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

    fun selectDay(dayOfWeek: String) {
        val selectedDayClasses = allRoutinesCache.filter { it.dayOfWeek.equals(dayOfWeek, ignoreCase = true) }
            .sortedBy { it.startTime }
        val today = DateTimeUtils.getCurrentDayOfWeek()
        _uiState.update {
            it.copy(
                selectedDay = dayOfWeek,
                selectedDayHeadline = getDayHeadline(dayOfWeek),
                isViewingToday = dayOfWeek.equals(today, ignoreCase = true),
                classesForSelectedDay = selectedDayClasses
            )
        }
    }

    fun selectClass(routine: ClassRoutineEntity?) {
        if (routine == null) {
            _uiState.update {
                it.copy(
                    selectedClass = null,
                    selectedClassTasks = emptyList()
                )
            }
            return
        }

        viewModelScope.launch {
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
                courseSettingDao.insertOrUpdate(existing.copy(notificationsEnabled = enabled, updatedAt = System.currentTimeMillis()))
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = key,
                        courseName = routine.courseName,
                        section = routine.section,
                        notificationsEnabled = enabled,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            com.example.util.UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(getApplication())
            _uiState.update { it.copy(selectedClassReminderEnabled = enabled) }
        }
    }

    fun toggleTask(task: TaskEntity, completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(status = if (completed) "DONE" else "TODO")
            taskDao.insertTask(updated)
            val routine = _uiState.value.selectedClass
            if (routine != null) {
                val allTasks = taskDao.getAllTasksDirect()
                val tasks = allTasks.filter { it.courseName.equals(routine.courseName, ignoreCase = true) }
                _uiState.update { it.copy(selectedClassTasks = tasks) }
            }
        }
    }

    fun addTaskForCourse(title: String) {
        val routine = _uiState.value.selectedClass ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val task = TaskEntity(
                courseName = routine.courseName,
                title = title,
                dueDateMillis = System.currentTimeMillis() + 86400000L,
                status = "TODO",
                createdAt = System.currentTimeMillis()
            )
            taskDao.insertTask(task)
            val allTasks = taskDao.getAllTasksDirect()
            val tasks = allTasks.filter { it.courseName.equals(routine.courseName, ignoreCase = true) }
            _uiState.update { it.copy(selectedClassTasks = tasks) }
        }
    }

    fun openAddEditSheet(item: CourseItemEntity? = null, courseKey: String? = null, initialType: String? = null) {
        _uiState.update {
            it.copy(
                showAddEditSheet = true,
                addEditItem = item,
                addEditCourseKey = courseKey,
                addEditInitialType = initialType
            )
        }
    }

    fun closeAddEditSheet() {
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
        viewModelScope.launch {
            val id = app.database.courseItemDao().insertItem(item)
            val savedItem = item.copy(id = if (item.id == 0L) id else item.id)
            CourseItemReminderScheduler.scheduleItemReminders(getApplication(), savedItem)
            closeAddEditSheet()
        }
    }

    fun triggerSync() {
        val workRequest = PortalSyncWorker.createOneTimeWorkRequest()
        workManager.enqueue(workRequest)

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = "Syncing with portal...") }
            workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { info ->
                if (info != null && info.state.isFinished) {
                    val msg = info.outputData.getString(PortalSyncWorker.KEY_RESULT_MESSAGE) ?: "Sync finished"
                    val success = info.outputData.getBoolean(PortalSyncWorker.KEY_IS_SUCCESS, true)
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncMessage = if (success) msg else "Sync error: $msg"
                        )
                    }
                }
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    private fun generateWeekDays(routines: List<ClassRoutineEntity>): List<DayChipItem> {
        val list = mutableListOf<DayChipItem>()
        val cal = Calendar.getInstance()
        val todayStr = DateTimeUtils.getCurrentDayOfWeek()
        val dateFormat = SimpleDateFormat("d MMM", Locale.ENGLISH)
        val dayNameFormat = SimpleDateFormat("EEEE", Locale.ENGLISH)

        for (i in 0..6) {
            val dayOfWeekStr = dayNameFormat.format(cal.time)
            val dayAbbr = dayOfWeekStr.take(3).uppercase()
            val dateNum = cal.get(Calendar.DAY_OF_MONTH).toString()
            val fullDate = dateFormat.format(cal.time)
            val isToday = dayOfWeekStr.equals(todayStr, ignoreCase = true)
            val count = routines.count { it.dayOfWeek.equals(dayOfWeekStr, ignoreCase = true) }

            list.add(
                DayChipItem(
                    dayOfWeek = dayOfWeekStr,
                    dayAbbr = dayAbbr,
                    dateNumber = dateNum,
                    fullDateFormatted = fullDate,
                    isToday = isToday,
                    classCount = count
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    private fun getTomorrowDayOfWeek(currentDay: String): String {
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val idx = days.indexOfFirst { it.equals(currentDay, ignoreCase = true) }
        return if (idx != -1) days[(idx + 1) % days.size] else "Monday"
    }

    private fun getDayHeadline(dayOfWeek: String): String {
        val todayStr = DateTimeUtils.getCurrentDayOfWeek()
        if (dayOfWeek.equals(todayStr, ignoreCase = true)) {
            return "Today, " + DateTimeUtils.formatDateHeadline()
        }
        val tomorrowStr = getTomorrowDayOfWeek(todayStr)
        if (dayOfWeek.equals(tomorrowStr, ignoreCase = true)) {
            return "Tomorrow"
        }
        return dayOfWeek
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
