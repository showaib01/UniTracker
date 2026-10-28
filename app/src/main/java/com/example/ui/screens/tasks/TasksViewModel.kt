package com.example.ui.screens.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseItemType
import com.example.data.local.entity.CourseSettingEntity
import com.example.ui.components.CoursePickerOption
import com.example.util.CourseItemReminderScheduler
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

data class TasksUiState(
    val allItems: List<CourseItemEntity> = emptyList(),
    val filteredItems: List<CourseItemEntity> = emptyList(),
    val overdueItems: List<CourseItemEntity> = emptyList(),
    val todayItems: List<CourseItemEntity> = emptyList(),
    val thisWeekItems: List<CourseItemEntity> = emptyList(),
    val laterItems: List<CourseItemEntity> = emptyList(),
    val doneItems: List<CourseItemEntity> = emptyList(),
    val selectedTypeFilter: String? = null, // null means "All"
    val selectedCourseFilter: String? = null, // null means "All"
    val availableTypes: List<String> = CourseItemType.ALL,
    val availableCourses: List<CoursePickerOption> = emptyList(),
    val showAddEditSheet: Boolean = false,
    val editingItem: CourseItemEntity? = null,
    val snackbarMessage: String? = null,
    val recentlyDeletedItem: CourseItemEntity? = null
)

class TasksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val courseItemDao = app.database.courseItemDao()
    private val routineDao = app.database.routineDao()

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            combine(
                courseItemDao.getAllActiveItems(),
                routineDao.getAllRoutines()
            ) { items, routines ->
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

                _uiState.value = _uiState.value.copy(
                    allItems = items,
                    availableCourses = courseOptions
                )
                applyFilters()
            }.collect {}
        }
    }

    fun selectTypeFilter(type: String?) {
        _uiState.value = _uiState.value.copy(selectedTypeFilter = type)
        applyFilters()
    }

    fun selectCourseFilter(courseKey: String?) {
        _uiState.value = _uiState.value.copy(selectedCourseFilter = courseKey)
        applyFilters()
    }

    private fun applyFilters() {
        val current = _uiState.value
        var filtered = current.allItems

        if (current.selectedTypeFilter != null) {
            filtered = filtered.filter { it.type.equals(current.selectedTypeFilter, ignoreCase = true) }
        }

        if (current.selectedCourseFilter != null) {
            filtered = filtered.filter { it.courseKey == current.selectedCourseFilter }
        }

        val nowMillis = System.currentTimeMillis()
        val nowCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val overdue = mutableListOf<CourseItemEntity>()
        val today = mutableListOf<CourseItemEntity>()
        val thisWeek = mutableListOf<CourseItemEntity>()
        val later = mutableListOf<CourseItemEntity>()
        val done = mutableListOf<CourseItemEntity>()

        for (item in filtered) {
            if (item.status == CourseItemStatus.DONE) {
                done.add(item)
                continue
            }

            val targetCal = Calendar.getInstance().apply {
                timeInMillis = item.dueAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffDays = ((targetCal.timeInMillis - nowCal.timeInMillis) / (24L * 3600 * 1000)).toInt()

            when {
                diffDays < 0 -> overdue.add(item)
                diffDays == 0 -> today.add(item)
                diffDays in 1..7 -> thisWeek.add(item)
                else -> later.add(item)
            }
        }

        _uiState.value = current.copy(
            filteredItems = filtered,
            overdueItems = overdue.sortedBy { it.dueAt },
            todayItems = today.sortedBy { it.dueAt },
            thisWeekItems = thisWeek.sortedBy { it.dueAt },
            laterItems = later.sortedBy { it.dueAt },
            doneItems = done.sortedByDescending { it.dueAt }
        )
    }

    fun openAddItem() {
        _uiState.value = _uiState.value.copy(showAddEditSheet = true, editingItem = null)
    }

    fun openEditItem(item: CourseItemEntity) {
        _uiState.value = _uiState.value.copy(showAddEditSheet = true, editingItem = item)
    }

    fun dismissAddEditSheet() {
        _uiState.value = _uiState.value.copy(showAddEditSheet = false, editingItem = null)
    }

    fun saveItem(item: CourseItemEntity) {
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
}
