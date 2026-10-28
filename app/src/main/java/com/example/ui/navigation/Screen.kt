package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector? = null) {
    // 4 Bottom Bar tabs only: Today, Routine, Tasks, More
    object Today : Screen("today", com.example.R.string.nav_today, Icons.Outlined.Today)
    object Routine : Screen("routine", com.example.R.string.nav_routine, Icons.Outlined.CalendarMonth)
    object Tasks : Screen("tasks", com.example.R.string.nav_tasks, Icons.Outlined.CheckCircle)
    object More : Screen("more", com.example.R.string.nav_more, Icons.Outlined.Menu)

    // Sub-screens
    object Portal : Screen("portal", com.example.R.string.portal_title)
    object PageCapture : Screen("page_capture", com.example.R.string.capture_title)
    object ParserDebug : Screen("parser_debug", com.example.R.string.debug_title)
    object Settings : Screen("settings", com.example.R.string.settings_title)
    object Notices : Screen("notices", com.example.R.string.more_notices)
    object DebugLog : Screen("debug_log", com.example.R.string.debug_title)
    object DesignSystem : Screen("design_system", com.example.R.string.app_name)
    object Course : Screen("course/{courseKey}", com.example.R.string.app_name) {
        fun createRoute(courseKey: String): String = "course/${java.net.URLEncoder.encode(courseKey, "UTF-8")}"
    }
}
