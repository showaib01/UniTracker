package com.example.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.CourseItemEntity
import com.example.data.local.entity.CourseItemType
import com.example.ui.components.AddEditCourseItemSheet
import com.example.ui.screens.course.CourseItemCard
import com.example.ui.theme.UniTrackTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("tasks_screen"),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.showAddEditSheet) {
                FloatingActionButton(
                    onClick = { viewModel.openAddItem() },
                    containerColor = colors.accentCyan,
                    contentColor = colors.background,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_add_task")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Item",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimens.ScreenPadding),
            contentPadding = PaddingValues(top = dimens.Space16, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.Space20)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tasks & Deadlines",
                        style = typography.dateHeadline.copy(fontSize = 24.sp),
                        color = colors.ink
                    )

                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                            .background(colors.surface, RoundedCornerShape(999.dp))
                            .clickable { viewModel.openAddItem() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.accentNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "New Item",
                                style = typography.metaText.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                color = colors.accentNavy
                            )
                        }
                    }
                }
            }

            // Filter Chips: Types
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "FILTER BY TYPE",
                        style = typography.sectionTitle,
                        color = colors.secondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPill(
                            label = "All",
                            isSelected = uiState.selectedTypeFilter == null,
                            onClick = { viewModel.selectTypeFilter(null) }
                        )

                        CourseItemType.ALL.forEach { type ->
                            FilterPill(
                                label = CourseItemType.displayName(type),
                                isSelected = uiState.selectedTypeFilter.equals(type, ignoreCase = true),
                                onClick = {
                                    val next = if (uiState.selectedTypeFilter.equals(type, ignoreCase = true)) null else type
                                    viewModel.selectTypeFilter(next)
                                }
                            )
                        }
                    }
                }
            }

            // Filter Chips: Courses
            if (uiState.availableCourses.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "FILTER BY COURSE",
                            style = typography.sectionTitle,
                            color = colors.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterPill(
                                label = "All Courses",
                                isSelected = uiState.selectedCourseFilter == null,
                                onClick = { viewModel.selectCourseFilter(null) }
                            )

                            uiState.availableCourses.forEach { course ->
                                FilterPill(
                                    label = course.courseName,
                                    isSelected = uiState.selectedCourseFilter == course.courseKey,
                                    onClick = {
                                        val next = if (uiState.selectedCourseFilter == course.courseKey) null else course.courseKey
                                        viewModel.selectCourseFilter(next)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Empty State
            if (uiState.filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.hairline, RoundedCornerShape(dimens.RadiusCard))
                            .background(colors.surface, RoundedCornerShape(dimens.RadiusCard))
                            .padding(vertical = dimens.Space32, horizontal = dimens.Space16),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No items match your filter",
                                style = typography.classTitle.copy(fontSize = 15.sp),
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + to add a quiz, assignment, or exam",
                                style = typography.metaText,
                                color = colors.secondary
                            )
                        }
                    }
                }
            }

            // Group 1: OVERDUE
            if (uiState.overdueItems.isNotEmpty()) {
                item {
                    Text(
                        text = "OVERDUE (${uiState.overdueItems.size})",
                        style = typography.sectionTitle,
                        color = colors.red
                    )
                }

                items(uiState.overdueItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }

            // Group 2: TODAY
            if (uiState.todayItems.isNotEmpty()) {
                item {
                    Text(
                        text = "TODAY (${uiState.todayItems.size})",
                        style = typography.sectionTitle,
                        color = colors.accentNavy
                    )
                }

                items(uiState.todayItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }

            // Group 3: THIS WEEK
            if (uiState.thisWeekItems.isNotEmpty()) {
                item {
                    Text(
                        text = "THIS WEEK (${uiState.thisWeekItems.size})",
                        style = typography.sectionTitle,
                        color = colors.secondary
                    )
                }

                items(uiState.thisWeekItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }

            // Group 4: LATER
            if (uiState.laterItems.isNotEmpty()) {
                item {
                    Text(
                        text = "LATER (${uiState.laterItems.size})",
                        style = typography.sectionTitle,
                        color = colors.secondary
                    )
                }

                items(uiState.laterItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }

            // Group 5: DONE
            if (uiState.doneItems.isNotEmpty()) {
                item {
                    Text(
                        text = "COMPLETED (${uiState.doneItems.size})",
                        style = typography.sectionTitle,
                        color = colors.secondary
                    )
                }

                items(uiState.doneItems, key = { it.id }) { item ->
                    CourseItemCard(
                        item = item,
                        onClick = { viewModel.openEditItem(item) },
                        onToggleDone = { done -> viewModel.toggleItemDone(item, done) }
                    )
                }
            }
        }

        // Add / Edit Item Sheet
        if (uiState.showAddEditSheet) {
            AddEditCourseItemSheet(
                existingItem = uiState.editingItem,
                preselectedCourseKey = null,
                initialType = null,
                availableCourses = uiState.availableCourses,
                onSave = { item -> viewModel.saveItem(item) },
                onDelete = { item -> viewModel.deleteItem(item) },
                onDismiss = { viewModel.dismissAddEditSheet() }
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = UniTrackTheme.colors
    val dimens = UniTrackTheme.dimens
    val bg = if (isSelected) colors.accentCyan.copy(alpha = 0.15f) else colors.surfaceField
    val border = if (isSelected) colors.accentCyan else colors.cardBorder
    val textCol = if (isSelected) colors.accentCyan else colors.textSecondary

    Box(
        modifier = Modifier
            .border(dimens.HairlineBorder, border, RoundedCornerShape(dimens.PillRadius))
            .background(bg, RoundedCornerShape(dimens.PillRadius))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textCol
        )
    }
}
