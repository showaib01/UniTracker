package com.example.ui.screens.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseSettingEntity
import com.example.ui.components.CoursePickerOption
import com.example.util.CourseItemReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CourseUiState(
    val courseKey: String = "",
    val courseName: String = "",
    val section: String = "",
    val routines: List<ClassRoutineEntity> = emptyList(),
    val setting: CourseSettingEntity? = null,
    val attendance: AttendanceEntity? = null,
    val attendanceThreshold: Int = 80,
    val upcomingItems: List<CourseItemEntity> = emptyList(),
    val pastItems: List<CourseItemEntity> = emptyList(),
    val isPastExpanded: Boolean = false,
    val isRemindersMuted: Boolean = false,
    val isDropped: Boolean = false,
    val showAddEditSheet: Boolean = false,
    val editingItem: CourseItemEntity? = null,
    val initialAddType: String? = null,
    val snackbarMessage: String? = null,
    val recentlyDeletedItem: CourseItemEntity? = null
)

class CourseViewModel(
    application: Application,
    private val targetCourseKey: String
) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val courseItemDao = app.database.courseItemDao()
    private val routineDao = app.database.routineDao()
    private val attendanceDao = app.database.attendanceDao()
    private val courseSettingDao = app.database.courseSettingDao()
    private val prefsRepo = app.preferencesRepository

    private val _uiState = MutableStateFlow(CourseUiState(courseKey = targetCourseKey))
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    init {
        loadCourseData()
    }

    private fun loadCourseData() {
        viewModelScope.launch {
            val allRoutines = routineDao.getAllRoutines().first()
            val matchedRoutines = allRoutines.filter { routine ->
                val key = CourseSettingEntity.makeKey(routine.courseName, routine.section)
                key == targetCourseKey || routine.courseName.equals(targetCourseKey, ignoreCase = true)
            }

            val firstRoutine = matchedRoutines.firstOrNull()
            val cName = firstRoutine?.courseName ?: targetCourseKey.substringBefore("_")
            val sec = firstRoutine?.section ?: targetCourseKey.substringAfter("_", "")

            val key = CourseSettingEntity.makeKey(cName, sec)

            combine(
                courseItemDao.getItemsForCourse(key),
                attendanceDao.getAllAttendance(),
                courseSettingDao.getSetting(key),
                prefsRepo.userPreferencesFlow
            ) { items, attendanceList, setting, prefs ->
                val att = attendanceList.firstOrNull {
                    it.courseName.equals(cName, ignoreCase = true) &&
                            (sec.isBlank() || it.section.equals(sec, ignoreCase = true))
                } ?: attendanceList.firstOrNull { it.courseName.equals(cName, ignoreCase = true) }

                val now = System.currentTimeMillis()
                val upcoming = items.filter { it.status == CourseItemStatus.TODO }
                    .sortedBy { it.dueAt }
                val past = items.filter { it.status != CourseItemStatus.TODO }
                    .sortedByDescending { it.dueAt }

                val isMuted = setting?.notificationsEnabled == false
                val isDrop = setting?.isDropped == true

                _uiState.value = _uiState.value.copy(
                    courseKey = key,
                    courseName = cName,
                    section = sec,
                    routines = matchedRoutines,
                    setting = setting,
                    attendance = att,
                    attendanceThreshold = prefs.attendanceThreshold,
                    upcomingItems = upcoming,
                    pastItems = past,
                    isRemindersMuted = isMuted,
                    isDropped = isDrop
                )
            }.collect {}
        }
    }

    fun togglePastExpanded() {
        _uiState.value = _uiState.value.copy(isPastExpanded = !_uiState.value.isPastExpanded)
    }

    fun openAddItem(type: String? = null) {
        _uiState.value = _uiState.value.copy(
            showAddEditSheet = true,
            editingItem = null,
            initialAddType = type
        )
    }

    fun openEditItem(item: CourseItemEntity) {
        _uiState.value = _uiState.value.copy(
            showAddEditSheet = true,
            editingItem = item,
            initialAddType = item.type
        )
    }

    fun dismissAddEditSheet() {
        _uiState.value = _uiState.value.copy(
            showAddEditSheet = false,
            editingItem = null,
            initialAddType = null
        )
    }

    fun saveItem(item: CourseItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = if (item.id == 0L) {
                courseItemDao.insertItem(item)
            } else {
                courseItemDao.updateItem(item)
                item.id
            }
            CourseItemReminderScheduler.scheduleItemReminders(
                app,
                item.copy(id = id)
            )
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

    fun deleteItem(item: CourseItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            courseItemDao.deleteItem(item)
            CourseItemReminderScheduler.cancelItemReminders(app, item.id)
            _uiState.value = _uiState.value.copy(
                recentlyDeletedItem = item,
                snackbarMessage = "Deleted ${item.title}"
            )
            dismissAddEditSheet()
        }
    }

    fun undoDelete() {
        val deleted = _uiState.value.recentlyDeletedItem ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val id = courseItemDao.insertItem(deleted)
            CourseItemReminderScheduler.scheduleItemReminders(app, deleted.copy(id = id))
            _uiState.value = _uiState.value.copy(
                recentlyDeletedItem = null,
                snackbarMessage = null
            )
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun toggleMuteReminders() {
        val curKey = _uiState.value.courseKey
        val newMuted = !_uiState.value.isRemindersMuted
        viewModelScope.launch(Dispatchers.IO) {
            val existing = courseSettingDao.getSettingDirect(curKey)
            if (existing != null) {
                courseSettingDao.setNotificationsEnabled(curKey, !newMuted)
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = curKey,
                        courseName = _uiState.value.courseName,
                        section = _uiState.value.section,
                        notificationsEnabled = !newMuted,
                        isDropped = false
                    )
                )
            }
        }
    }

    fun toggleDropCourse() {
        val curKey = _uiState.value.courseKey
        val newDropped = !_uiState.value.isDropped
        viewModelScope.launch(Dispatchers.IO) {
            val existing = courseSettingDao.getSettingDirect(curKey)
            if (existing != null) {
                courseSettingDao.setDropped(curKey, newDropped)
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = curKey,
                        courseName = _uiState.value.courseName,
                        section = _uiState.value.section,
                        notificationsEnabled = true,
                        isDropped = newDropped
                    )
                )
            }
        }
    }
}
