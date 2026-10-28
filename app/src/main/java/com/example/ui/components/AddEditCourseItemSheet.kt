package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemStatus
import com.example.data.local.entity.CourseItemType
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CoursePickerOption(
    val courseKey: String,
    val courseName: String,
    val section: String,
    val routineDays: List<String> = emptyList(),
    val room: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCourseItemSheet(
    existingItem: CourseItemEntity? = null,
    preselectedCourseKey: String? = null,
    initialType: String? = null,
    availableCourses: List<CoursePickerOption>,
    onSave: (CourseItemEntity) -> Unit,
    onDelete: ((CourseItemEntity) -> Unit)? = null,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val isEditMode = existingItem != null

    // Find initial course
    val defaultCourse = availableCourses.firstOrNull {
        it.courseKey == (existingItem?.courseKey ?: preselectedCourseKey)
    } ?: availableCourses.firstOrNull()

    var selectedCourse by remember { mutableStateOf(defaultCourse) }
    var selectedType by remember {
        mutableStateOf(existingItem?.type ?: initialType ?: CourseItemType.QUIZ)
    }
    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var hasCustomTitle by remember { mutableStateOf(existingItem != null && existingItem.title.isNotBlank()) }

    var dueAtMillis by remember {
        mutableStateOf(
            existingItem?.dueAt ?: (System.currentTimeMillis() + 86400000L)
        )
    }
    var hasTime by remember { mutableStateOf(existingItem?.hasTime ?: false) }

    var topics by remember { mutableStateOf(existingItem?.topics ?: "") }
    var marksStr by remember {
        mutableStateOf(existingItem?.marks?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var venue by remember { mutableStateOf(existingItem?.venue ?: defaultCourse?.room ?: "") }
    var notes by remember { mutableStateOf(existingItem?.notes ?: "") }

    var selectedOffsets by remember {
        mutableStateOf(
            existingItem?.getReminderOffsetsList()?.toSet() ?: setOf(1440, 120)
        )
    }

    var titleError by remember { mutableStateOf<String?>(null) }
    var courseError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var courseDropdownExpanded by remember { mutableStateOf(false) }

    // Auto-suggest title when type or course changes if user hasn't typed a custom title
    LaunchedEffect(selectedType, selectedCourse) {
        if (!hasCustomTitle && !isEditMode) {
            title = CourseItemType.displayName(selectedType)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceSheet,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = dimens.CardRadius, topEnd = dimens.CardRadius),
        dragHandle = null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_edit_course_item_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // 1. Top Header Row (Title 22sp semibold on left, close icon on right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditMode) "Edit Item" else "New Item",
                    style = typography.screenTitle,
                    color = colors.textPrimary
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(dimens.MinTouchTarget)
                        .testTag("close_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 2. Scrollable Form Content (with imePadding to stay above keyboard)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.ScreenPadding)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(dimens.Space20)
            ) {
                // Section: TYPE
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "TYPE",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        CourseItemType.ALL.forEach { type ->
                            val isSelected = selectedType.equals(type, ignoreCase = true)
                            val chipBg = if (isSelected) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField
                            val chipBorder = if (isSelected) colors.accentCyan else colors.cardBorder
                            val chipText = if (isSelected) colors.accentCyan else colors.textSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(dimens.PillRadius))
                                    .background(chipBg)
                                    .border(dimens.HairlineBorder, chipBorder, RoundedCornerShape(dimens.PillRadius))
                                    .clickable {
                                        selectedType = type
                                        if (!hasCustomTitle) {
                                            title = CourseItemType.displayName(type)
                                        }
                                    }
                                    .padding(horizontal = dimens.Space16, vertical = dimens.Space8)
                                    .testTag("chip_type_$type"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = CourseItemType.displayName(type),
                                    style = typography.labelBadge,
                                    color = chipText,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Section: COURSE
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "COURSE",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )

                    val isCourseFixed = isEditMode || preselectedCourseKey != null
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                .background(if (isCourseFixed) colors.surfaceField.copy(alpha = 0.6f) else colors.surfaceField)
                                .border(dimens.HairlineBorder, colors.cardBorder, RoundedCornerShape(dimens.BoxRadius))
                                .clickable(enabled = !isCourseFixed) {
                                    courseDropdownExpanded = true
                                }
                                .padding(horizontal = dimens.Space16, vertical = dimens.Space12)
                                .testTag("course_dropdown_trigger"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCourse?.let { "${it.courseName} [${it.section}]" } ?: "Select Course",
                                style = typography.body,
                                color = if (selectedCourse != null) colors.textPrimary else colors.textSecondary
                            )
                            if (!isCourseFixed) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = "Select Course",
                                    tint = colors.textSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = courseDropdownExpanded,
                            onDismissRequest = { courseDropdownExpanded = false },
                            modifier = Modifier.background(colors.surfaceSheet)
                        ) {
                            availableCourses.forEach { course ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${course.courseName} [${course.section}]",
                                            style = typography.body,
                                            color = colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedCourse = course
                                        if (venue.isBlank() && course.room.isNotBlank()) {
                                            venue = course.room
                                        }
                                        courseError = null
                                        courseDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    courseError?.let {
                        Text(
                            text = it,
                            style = typography.meta,
                            color = colors.danger
                        )
                    }
                }

                // Section: TITLE
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "TITLE",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = title,
                        onValueChange = {
                            title = it
                            hasCustomTitle = it.isNotBlank()
                            titleError = null
                        },
                        placeholder = "e.g. Quiz 2: Chapter 4",
                        isError = titleError != null,
                        testTag = "input_item_title"
                    )
                    titleError?.let {
                        Text(
                            text = it,
                            style = typography.meta,
                            color = colors.danger
                        )
                    }
                }

                // Section: DATE & TIME
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "DATE & TIME",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space12)
                    ) {
                        // Date selector
                        val dateFormatted = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(Date(dueAtMillis))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                .background(colors.surfaceField)
                                .border(dimens.HairlineBorder, colors.cardBorder, RoundedCornerShape(dimens.BoxRadius))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            cal.set(Calendar.YEAR, y)
                                            cal.set(Calendar.MONTH, m)
                                            cal.set(Calendar.DAY_OF_MONTH, d)
                                            dueAtMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = dimens.Space12, vertical = dimens.Space12)
                                .testTag("picker_date_button"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = "Pick Date",
                                    tint = colors.sand,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(dimens.Space8))
                                Text(
                                    text = dateFormatted,
                                    style = typography.body,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Time selector
                        val timeCal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                        val timeStr24 = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(timeCal.time)
                        val timeDisplay = if (hasTime) DateTimeUtils.formatTime12h(timeStr24) else "Set time"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                .background(if (hasTime) colors.accentCyan.copy(alpha = 0.12f) else colors.surfaceField)
                                .border(
                                    dimens.HairlineBorder,
                                    if (hasTime) colors.accentCyan else colors.cardBorder,
                                    RoundedCornerShape(dimens.BoxRadius)
                                )
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            cal.set(Calendar.MINUTE, minute)
                                            cal.set(Calendar.SECOND, 0)
                                            dueAtMillis = cal.timeInMillis
                                            hasTime = true
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false // 12-hour AM/PM dialog
                                    ).show()
                                }
                                .padding(horizontal = dimens.Space12, vertical = dimens.Space12)
                                .testTag("picker_time_button"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = "Pick Time",
                                    tint = if (hasTime) colors.accentCyan else colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(dimens.Space8))
                                Text(
                                    text = timeDisplay,
                                    style = typography.body,
                                    color = if (hasTime) colors.accentCyan else colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Quick Date Chips: "Next class", "Tomorrow", "In 1 week", "Clear time"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        // Quick chip: Next class
                        selectedCourse?.let { course ->
                            QuickDateChip(
                                label = "Next class",
                                onClick = {
                                    dueAtMillis = DateTimeUtils.calculateNextClassDate(course.routineDays)
                                }
                            )
                        }

                        // Quick chip: Tomorrow
                        QuickDateChip(
                            label = "Tomorrow",
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 10)
                                    set(Calendar.MINUTE, 0)
                                }
                                dueAtMillis = cal.timeInMillis
                            }
                        )

                        // Quick chip: In 1 week
                        QuickDateChip(
                            label = "In 1 week",
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 7)
                                    set(Calendar.HOUR_OF_DAY, 10)
                                    set(Calendar.MINUTE, 0)
                                }
                                dueAtMillis = cal.timeInMillis
                            }
                        )

                        if (hasTime) {
                            QuickDateChip(
                                label = "Clear time",
                                onClick = { hasTime = false }
                            )
                        }
                    }
                }

                // Section: TOPICS / SYLLABUS
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "TOPICS / SYLLABUS",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = topics,
                        onValueChange = { topics = it },
                        placeholder = "Chapters, slide numbers, topics covered...",
                        singleLine = false,
                        testTag = "input_item_topics"
                    )
                }

                // Section: TOTAL MARKS
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "TOTAL MARKS (OPTIONAL)",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = marksStr,
                        onValueChange = { marksStr = it },
                        placeholder = "e.g. 20",
                        keyboardType = KeyboardType.Number,
                        testTag = "input_item_marks"
                    )
                }

                // Section: VENUE / ROOM
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "VENUE / ROOM (OPTIONAL)",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = venue,
                        onValueChange = { venue = it },
                        placeholder = "e.g. Room 3102 / DS0302",
                        testTag = "input_item_venue"
                    )
                }

                // Section: NOTES
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "NOTES (OPTIONAL)",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Additional instructions, submission links...",
                        singleLine = false,
                        testTag = "input_item_notes"
                    )
                }

                // Section: REMINDERS
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "REMINDERS",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        val reminderPresets = listOf(
                            1440 to "1 day before",
                            180 to "3 hours before",
                            60 to "1 hour before",
                            15 to "15 min before"
                        )

                        reminderPresets.forEach { (mins, label) ->
                            val isChecked = selectedOffsets.contains(mins)
                            val chipBg = if (isChecked) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField
                            val chipBorder = if (isChecked) colors.accentCyan else colors.cardBorder
                            val chipText = if (isChecked) colors.accentCyan else colors.textSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(dimens.PillRadius))
                                    .background(chipBg)
                                    .border(dimens.HairlineBorder, chipBorder, RoundedCornerShape(dimens.PillRadius))
                                    .clickable {
                                        selectedOffsets = if (isChecked) {
                                            selectedOffsets - mins
                                        } else {
                                            selectedOffsets + mins
                                        }
                                    }
                                    .padding(horizontal = dimens.Space12, vertical = dimens.Space8),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = typography.labelBadge,
                                    color = chipText,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.Space16))
            }

            // 3. Sticky Footer with Opaque Background & Hairline Border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceSheet)
                    .border(
                        width = dimens.HairlineBorder,
                        color = colors.cardBorder
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .size(dimens.MinTouchTarget)
                                .testTag("delete_item_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete Item",
                                tint = colors.danger
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            variant = AppButtonVariant.GHOST,
                            testTag = "cancel_sheet_button"
                        )

                        AppButton(
                            text = if (isEditMode) "Save Changes" else "Create Item",
                            onClick = {
                                if (title.isBlank()) {
                                    titleError = "Title is required"
                                    return@AppButton
                                }
                                val course = selectedCourse
                                if (course == null) {
                                    courseError = "Please select a course"
                                    return@AppButton
                                }

                                val offsetsList = if (selectedOffsets.isEmpty()) listOf(1440, 120) else selectedOffsets.toList().sortedDescending()
                                val marksVal = marksStr.toDoubleOrNull()

                                val itemToSave = (existingItem ?: CourseItemEntity(
                                    courseKey = course.courseKey,
                                    courseName = course.courseName,
                                    section = course.section,
                                    type = selectedType,
                                    title = title.trim(),
                                    dueAt = dueAtMillis,
                                    hasTime = hasTime,
                                    topics = topics.trim(),
                                    marks = marksVal,
                                    venue = venue.trim(),
                                    notes = notes.trim(),
                                    reminderOffsets = offsetsList.joinToString(",")
                                )).copy(
                                    courseKey = course.courseKey,
                                    courseName = course.courseName,
                                    section = course.section,
                                    type = selectedType,
                                    title = title.trim(),
                                    dueAt = dueAtMillis,
                                    hasTime = hasTime,
                                    topics = topics.trim(),
                                    marks = marksVal,
                                    venue = venue.trim(),
                                    notes = notes.trim(),
                                    reminderOffsets = offsetsList.joinToString(",")
                                )

                                onSave(itemToSave)
                            },
                            variant = AppButtonVariant.PRIMARY,
                            testTag = "save_item_button"
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && existingItem != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = colors.surfaceSheet,
            title = {
                Text(
                    text = "Delete ${CourseItemType.displayName(existingItem.type)}?",
                    style = typography.sectionTitle,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${existingItem.title}\"? This cannot be undone.",
                    style = typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(existingItem)
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = colors.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = "Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    onClick: () -> Unit
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.PillRadius))
            .background(colors.surfaceField)
            .border(dimens.HairlineBorder, colors.cardBorder, RoundedCornerShape(dimens.PillRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.Space12, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.meta,
            color = colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CustomFormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    testTag: String = ""
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val borderColor = if (isError) colors.danger else colors.cardBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.BoxRadius))
            .background(colors.surfaceField)
            .border(dimens.HairlineBorder, borderColor, RoundedCornerShape(dimens.BoxRadius))
            .padding(horizontal = dimens.Space16, vertical = if (singleLine) dimens.Space12 else dimens.Space16)
            .testTag(testTag)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = typography.body,
                color = colors.textSecondary,
                fontSize = 14.sp
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = typography.body.copy(
                color = colors.textPrimary,
                fontSize = 14.sp
            ),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            cursorBrush = SolidColor(colors.accentCyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
