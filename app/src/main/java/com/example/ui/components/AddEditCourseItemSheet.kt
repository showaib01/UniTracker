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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.data.local.entity.CourseItemType
import com.example.ui.theme.UniTrackTheme
import com.example.util.CourseMeetingSlot
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
    val meetingSlots: List<CourseMeetingSlot> = emptyList(),
    val room: String = ""
)

private data class SlotChipData(
    val label: String,
    val timeMillis: Long,
    val room: String
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

    var isManuallyEdited by remember { mutableStateOf(isEditMode) }
    var isVenueManuallyEdited by remember { mutableStateOf(isEditMode && existingItem?.venue?.isNotBlank() == true) }

    // Initial date, time & room calculation
    val initialSlotInfo = defaultCourse?.let { DateTimeUtils.calculateNextMeetingSlotInfo(it.meetingSlots) }
    val initialDueAt = existingItem?.dueAt ?: (initialSlotInfo?.millis ?: (System.currentTimeMillis() + 86400000L))
    val initialHasTime = existingItem?.hasTime ?: (initialSlotInfo?.hasTime ?: false)
    val initialVenue = existingItem?.venue ?: (initialSlotInfo?.room?.ifBlank { defaultCourse?.room ?: "" } ?: "")

    var dueAtMillis by remember { mutableStateOf(initialDueAt) }
    var hasTime by remember { mutableStateOf(initialHasTime) }
    var venue by remember { mutableStateOf(initialVenue) }

    var topics by remember { mutableStateOf(existingItem?.topics ?: "") }
    var marksStr by remember {
        mutableStateOf(existingItem?.marks?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
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
                .navigationBarsPadding()
                .imePadding()
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
                        tint = colors.textSecondary
                    )
                }
            }

            // 2. Scrollable Body Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(dimens.Space20)
            ) {
                // Section: ITEM TYPE SELECTOR
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
                            val containerBg = if (isSelected) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField
                            val contentFg = if (isSelected) colors.accentCyan else colors.textSecondary
                            val borderColor = if (isSelected) colors.accentCyan else colors.cardBorder

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(dimens.PillRadius))
                                    .background(containerBg)
                                    .border(dimens.HairlineBorder, borderColor, RoundedCornerShape(dimens.PillRadius))
                                    .clickable {
                                        selectedType = type
                                    }
                                    .padding(horizontal = dimens.Space16, vertical = dimens.Space8)
                                    .testTag("type_chip_$type"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = CourseItemType.displayName(type),
                                    style = typography.labelBadge,
                                    color = contentFg,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Section: COURSE SELECTOR
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
                                        if (!isEditMode) {
                                            val nextSlot = DateTimeUtils.calculateNextMeetingSlotInfo(course.meetingSlots)
                                            if (!isManuallyEdited) {
                                                dueAtMillis = nextSlot.millis
                                                hasTime = nextSlot.hasTime
                                            }
                                            if (!isVenueManuallyEdited) {
                                                venue = nextSlot.room.ifBlank { course.room }
                                            }
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
                                            isManuallyEdited = true
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

                        // Time selector (12-hour format display with AM/PM)
                        val timeCal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                        val timeStr24 = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(timeCal.time)
                        val timeDisplay = if (hasTime) DateTimeUtils.formatTime12h(timeStr24) else "All day"

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
                                            isManuallyEdited = true
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

                    // Quick Chips: course's actual meeting slots for coming week, Tomorrow, In 1 week, All day toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        selectedCourse?.let { course ->
                            val slotChips = generateCourseSlotChips(course)
                            slotChips.forEach { chip ->
                                QuickDateChip(
                                    label = chip.label,
                                    onClick = {
                                        dueAtMillis = chip.timeMillis
                                        hasTime = true
                                        isManuallyEdited = true
                                        if (chip.room.isNotBlank() && !isVenueManuallyEdited) {
                                            venue = chip.room
                                        }
                                    }
                                )
                            }
                        }

                        // Quick chip: Tomorrow
                        QuickDateChip(
                            label = "Tomorrow",
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = dueAtMillis
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }
                                dueAtMillis = cal.timeInMillis
                                isManuallyEdited = true
                            }
                        )

                        // Quick chip: In 1 week
                        QuickDateChip(
                            label = "In 1 week",
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = dueAtMillis
                                    add(Calendar.DAY_OF_YEAR, 7)
                                }
                                dueAtMillis = cal.timeInMillis
                                isManuallyEdited = true
                            }
                        )

                        // Quick chip: All day / Set time toggle
                        QuickDateChip(
                            label = if (hasTime) "All day" else "Set time",
                            onClick = {
                                hasTime = !hasTime
                                isManuallyEdited = true
                            }
                        )
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
                        placeholder = "e.g. Chapter 4, Slide 12-25, Linked Lists",
                        singleLine = false,
                        testTag = "input_item_topics"
                    )
                }

                // Section: MARKS / WEIGHTAGE & VENUE (Side by side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.Space12)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        Text(
                            text = "MARKS",
                            style = typography.sectionHeaderLabel,
                            color = colors.sand
                        )
                        CustomFormField(
                            value = marksStr,
                            onValueChange = { marksStr = it },
                            placeholder = "e.g. 10",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            testTag = "input_item_marks"
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        Text(
                            text = "ROOM / VENUE",
                            style = typography.sectionHeaderLabel,
                            color = colors.sand
                        )
                        CustomFormField(
                            value = venue,
                            onValueChange = {
                                venue = it
                                isVenueManuallyEdited = true
                            },
                            placeholder = "e.g. DN0212 / Annex",
                            testTag = "input_item_venue"
                        )
                    }
                }

                // Section: NOTES
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "NOTES",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )
                    CustomFormField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Any extra details, group members, links...",
                        singleLine = false,
                        testTag = "input_item_notes"
                    )
                }

                // Section: REMINDER OFFSETS
                Column(verticalArrangement = Arrangement.spacedBy(dimens.Space8)) {
                    Text(
                        text = "REMIND ME",
                        style = typography.sectionHeaderLabel,
                        color = colors.sand
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimens.Space8)
                    ) {
                        listOf(
                            1440 to "1 day before",
                            180 to "3 hours before",
                            120 to "2 hours before",
                            60 to "1 hour before"
                        ).forEach { (offset, labelText) ->
                            val isSelected = selectedOffsets.contains(offset)
                            val containerBg = if (isSelected) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField
                            val contentFg = if (isSelected) colors.accentCyan else colors.textSecondary
                            val borderColor = if (isSelected) colors.accentCyan else colors.cardBorder

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(dimens.PillRadius))
                                    .background(containerBg)
                                    .border(dimens.HairlineBorder, borderColor, RoundedCornerShape(dimens.PillRadius))
                                    .clickable {
                                        selectedOffsets = if (isSelected) {
                                            selectedOffsets - offset
                                        } else {
                                            selectedOffsets + offset
                                        }
                                    }
                                    .padding(horizontal = dimens.Space12, vertical = dimens.Space8)
                                    .testTag("reminder_chip_$offset"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = labelText,
                                    style = typography.meta,
                                    color = contentFg,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
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
                        Spacer(modifier = Modifier.width(1.dp))
                    }

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

                            // Calculate final dueAt: if "All day", compute relative to 8:00 AM of that day
                            val finalDueAt = if (!hasTime) {
                                Calendar.getInstance().apply {
                                    timeInMillis = dueAtMillis
                                    set(Calendar.HOUR_OF_DAY, 8)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            } else dueAtMillis

                            val itemToSave = (existingItem ?: CourseItemEntity(
                                courseKey = course.courseKey,
                                courseName = course.courseName,
                                section = course.section,
                                type = selectedType,
                                title = title.trim(),
                                dueAt = finalDueAt,
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
                                dueAt = finalDueAt,
                                hasTime = hasTime,
                                topics = topics.trim(),
                                marks = marksVal,
                                venue = venue.trim(),
                                notes = notes.trim(),
                                reminderOffsets = offsetsList.joinToString(",")
                            )

                            onSave(itemToSave)
                        },
                        modifier = Modifier.testTag("save_item_button")
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && existingItem != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Item?", style = typography.sectionTitle) },
            text = { Text("Are you sure you want to delete '${existingItem.title}'?", style = typography.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(existingItem)
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

private fun generateCourseSlotChips(course: CoursePickerOption, nowMillis: Long = System.currentTimeMillis()): List<SlotChipData> {
    if (course.meetingSlots.isEmpty()) return emptyList()

    val dayMap = mapOf(
        "SUNDAY" to Calendar.SUNDAY,
        "MONDAY" to Calendar.MONDAY,
        "TUESDAY" to Calendar.TUESDAY,
        "WEDNESDAY" to Calendar.WEDNESDAY,
        "THURSDAY" to Calendar.THURSDAY,
        "FRIDAY" to Calendar.FRIDAY,
        "SATURDAY" to Calendar.SATURDAY
    )

    val chipList = mutableListOf<SlotChipData>()

    for (slot in course.meetingSlots) {
        val targetDayOfWeek = dayMap[slot.dayOfWeek.trim().uppercase()] ?: continue
        val startMins = try {
            val parts = slot.startTime.trim().split(":")
            val h = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 0
            h * 60 + m
        } catch (e: Exception) { 480 }

        val startHour = startMins / 60
        val startMin = startMins % 60

        for (dayOffset in 0..6) {
            val candidateCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }

            if (candidateCal.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                candidateCal.set(Calendar.HOUR_OF_DAY, startHour)
                candidateCal.set(Calendar.MINUTE, startMin)
                candidateCal.set(Calendar.SECOND, 0)
                candidateCal.set(Calendar.MILLISECOND, 0)

                if (candidateCal.timeInMillis > nowMillis) {
                    val dateFormatted = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(candidateCal.timeInMillis))
                    val time12h = DateTimeUtils.formatTime12h(slot.startTime)
                    val roomText = if (slot.room.isNotBlank()) " [${slot.room}]" else ""
                    val label = "$dateFormatted · $time12h$roomText"
                    chipList.add(SlotChipData(label, candidateCal.timeInMillis, slot.room))
                }
            }
        }
    }

    return chipList.sortedBy { it.timeMillis }
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
            color = colors.accentCyan,
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
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    testTag: String = ""
) {
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.BoxRadius))
            .background(colors.surfaceField)
            .border(
                width = dimens.HairlineBorder,
                color = if (isError) colors.danger else colors.cardBorder,
                shape = RoundedCornerShape(dimens.BoxRadius)
            )
            .padding(horizontal = dimens.Space16, vertical = dimens.Space12)
            .testTag(testTag)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = typography.body,
                color = colors.textSecondary.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = typography.body.copy(color = colors.textPrimary),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(colors.accentCyan),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
