package com.example.ui.screens.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.ClassRoutineEntity
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseSettingEntity
import com.example.util.CourseItemReminderScheduler
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
    private val courseSettingDao = app.database.courseSettingDao()

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
                courseSettingDao.getSetting(key)
            ) { items, setting ->
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
                    upcomingItems = upcoming,
                    pastItems = past,
                    isRemindersMuted = isMuted,
                    isDropped = isDrop
                )
            }.collect {}
        }
    }

    fun toggleReminders(muted: Boolean) {
        viewModelScope.launch {
            val currentSetting = uiState.value.setting
            val enabled = !muted
            if (currentSetting != null) {
                courseSettingDao.insertOrUpdate(currentSetting.copy(notificationsEnabled = enabled, updatedAt = System.currentTimeMillis()))
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = uiState.value.courseKey,
                        courseName = uiState.value.courseName,
                        section = uiState.value.section,
                        notificationsEnabled = enabled,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            com.example.util.UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(getApplication())
        }
    }

    fun toggleDropped(dropped: Boolean) {
        viewModelScope.launch {
            val currentSetting = uiState.value.setting
            if (currentSetting != null) {
                courseSettingDao.insertOrUpdate(currentSetting.copy(isDropped = dropped, updatedAt = System.currentTimeMillis()))
            } else {
                courseSettingDao.insertOrUpdate(
                    CourseSettingEntity(
                        courseKey = uiState.value.courseKey,
                        courseName = uiState.value.courseName,
                        section = uiState.value.section,
                        isDropped = dropped,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            com.example.util.UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(getApplication())
        }
    }

    fun toggleItemDone(item: CourseItemEntity, done: Boolean) {
        viewModelScope.launch {
            val newStatus = if (done) CourseItemStatus.DONE else CourseItemStatus.TODO
            val updated = item.copy(status = newStatus)
            courseItemDao.insertItem(updated)
            if (done) {
                CourseItemReminderScheduler.cancelItemReminders(getApplication<UniTrackApplication>(), item.id)
                _uiState.value = _uiState.value.copy(snackbarMessage = "Marked as done", recentlyDeletedItem = null)
            } else {
                CourseItemReminderScheduler.scheduleItemReminders(getApplication<UniTrackApplication>(), updated)
                _uiState.value = _uiState.value.copy(snackbarMessage = "Marked as todo", recentlyDeletedItem = null)
            }
        }
    }

    fun deleteItem(item: CourseItemEntity) {
        viewModelScope.launch {
            CourseItemReminderScheduler.cancelItemReminders(getApplication<UniTrackApplication>(), item.id)
            courseItemDao.deleteItem(item)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Item deleted", recentlyDeletedItem = item)
        }
    }

    fun undoDelete() {
        val item = uiState.value.recentlyDeletedItem ?: return
        viewModelScope.launch {
            courseItemDao.insertItem(item)
            CourseItemReminderScheduler.scheduleItemReminders(getApplication<UniTrackApplication>(), item)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Restored", recentlyDeletedItem = null)
        }
    }

    fun openAddItem(type: String? = null) {
        _uiState.value = _uiState.value.copy(showAddEditSheet = true, editingItem = null, initialAddType = type)
    }

    fun openEditItem(item: CourseItemEntity) {
        _uiState.value = _uiState.value.copy(showAddEditSheet = true, editingItem = item, initialAddType = null)
    }

    fun closeAddEditSheet() {
        _uiState.value = _uiState.value.copy(showAddEditSheet = false, editingItem = null, initialAddType = null)
    }

    fun saveCourseItem(item: CourseItemEntity) {
        viewModelScope.launch {
            val id = courseItemDao.insertItem(item)
            val savedItem = item.copy(id = if (item.id == 0L) id else item.id)
            CourseItemReminderScheduler.scheduleItemReminders(getApplication<UniTrackApplication>(), savedItem)
            closeAddEditSheet()
            _uiState.value = _uiState.value.copy(snackbarMessage = "Item saved")
        }
    }

    fun togglePastExpanded() {
        _uiState.value = _uiState.value.copy(isPastExpanded = !uiState.value.isPastExpanded)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
