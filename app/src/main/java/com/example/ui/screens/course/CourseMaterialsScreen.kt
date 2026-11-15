package com.example.ui.screens.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UniTrackApplication
import com.example.data.local.entity.CourseSettingEntity
import com.example.ui.components.AppBackground
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.theme.UniTrackTheme

@Composable
fun CourseMaterialsScreen(
    onNavigateBack: () -> Unit,
    onSelectCourseForDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val routinesFlow = remember { app.database.routineDao().getAllRoutines() }
    val routines by routinesFlow.collectAsState(initial = emptyList())

    val distinctCourses = remember(routines) {
        routines.distinctBy { CourseSettingEntity.makeKey(it.courseName, it.section) }
    }

    AppBackground(modifier = modifier.testTag("course_materials_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Sub-screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp).testTag("course_materials_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(dimens.Space8))
                Text(
                    text = "Course Materials",
                    style = typography.screenTitle,
                    color = colors.textPrimary
                )
            }

            if (distinctCourses.isEmpty()) {
                EmptyState(
                    title = "No active courses",
                    message = "Log in to AIUB Student Portal to sync your course schedule first.",
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.padding(dimens.ScreenPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = dimens.ScreenPadding,
                        vertical = dimens.Space8
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.Space12)
                ) {
                    items(distinctCourses, key = { CourseSettingEntity.makeKey(it.courseName, it.section) }) { course ->
                        val key = CourseSettingEntity.makeKey(course.courseName, course.section)
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("course_material_row_$key"),
                            onClick = { onSelectCourseForDownload(key) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = { onSelectCourseForDownload(key) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(dimens.BoxRadius))
                                            .background(colors.accentCyan.copy(alpha = 0.12f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = colors.accentCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(dimens.Space12))
                                    Column {
                                        Text(
                                            text = course.courseName,
                                            style = typography.body,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${course.courseCode.ifBlank { "Sec" }}: ${course.section} • Room: ${course.room}",
                                            style = typography.meta,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = "Open Portal Mode",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
