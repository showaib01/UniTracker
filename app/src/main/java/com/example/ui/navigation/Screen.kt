package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector? = null) {
    // 4 Bottom Bar tabs only: Today, Routine, Tasks, Settings
    object Today : Screen("today", com.example.R.string.nav_today, Icons.Outlined.Today)
    object Routine : Screen("routine", com.example.R.string.nav_routine, Icons.Outlined.CalendarMonth)
    object Tasks : Screen("tasks", com.example.R.string.nav_tasks, Icons.Outlined.CheckCircle)
    object More : Screen("more", com.example.R.string.settings_title, Icons.Outlined.Settings)

    // Sub-screens
    object Portal : Screen("portal?courseKey={courseKey}", com.example.R.string.portal_title) {
        fun createRoute(courseKey: String? = null): String = if (courseKey != null) "portal?courseKey=${java.net.URLEncoder.encode(courseKey, "UTF-8")}" else "portal"
    }
    object Settings : Screen("settings", com.example.R.string.settings_title)
    object Notices : Screen("notices", com.example.R.string.more_notices)
    object CourseMaterials : Screen("course_materials", com.example.R.string.app_name)
    object Downloads : Screen("downloads", com.example.R.string.app_name)
    object Course : Screen("course/{courseKey}", com.example.R.string.app_name) {
        fun createRoute(courseKey: String): String = "course/${java.net.URLEncoder.encode(courseKey, "UTF-8")}"
    }
}
