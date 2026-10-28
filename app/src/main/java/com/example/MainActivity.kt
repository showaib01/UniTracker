package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.sync.PortalSyncWorker
import com.example.ui.components.AppBackground
import com.example.ui.components.AppTopBar
import com.example.ui.components.GlassBottomBar
import com.example.ui.navigation.Screen
import com.example.ui.screens.capture.PageCaptureScreen
import com.example.ui.screens.capture.ParserDebugScreen
import com.example.ui.screens.capture.ParserDebugViewModel
import com.example.ui.screens.course.CourseScreen
import com.example.ui.screens.course.CourseViewModel
import com.example.ui.screens.more.DebugLogScreen
import com.example.ui.screens.more.DesignSystemScreen
import com.example.ui.screens.more.MoreScreen
import com.example.ui.screens.more.NoticesScreen
import com.example.ui.screens.more.SettingsScreen
import com.example.ui.screens.portal.PortalScreen
import com.example.ui.screens.routine.RoutineScreen
import com.example.ui.screens.tasks.TasksScreen
import com.example.ui.screens.today.TodayScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import com.example.util.UniTrackLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Trigger on-open sync if last sync was > 6 hours ago and session is active
        lifecycleScope.launch {
            val app = application as UniTrackApplication
            val prefs = app.preferencesRepository.userPreferencesFlow.first()
            val sixHoursMs = 6 * 3600 * 1000L
            if (System.currentTimeMillis() - prefs.lastSyncTimestamp > sixHoursMs && prefs.isSessionActive) {
                UniTrackLogger.i("MainActivity: Last sync > 6 hours old. Automatically starting sync.")
                PortalSyncWorker.triggerManualSync(this@MainActivity)
            }
        }

        setContent {
            val app = application as UniTrackApplication
            val prefs by app.preferencesRepository.userPreferencesFlow.collectAsState(
                initial = com.example.data.local.preferences.UserPreferences()
            )

            val isDark = when (prefs.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                MainAppScreen(
                    isDark = isDark,
                    onToggleTheme = {
                        lifecycleScope.launch {
                            val newMode = if (isDark) "LIGHT" else "DARK"
                            app.preferencesRepository.updateThemeMode(newMode)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication

    // Due soon & overdue items for top bar badge
    val upcomingItems by app.database.courseItemDao().getUpcomingActiveItems(20).collectAsState(initial = emptyList())
    val overdueCount = upcomingItems.count {
        val info = DateTimeUtils.formatDueBadgeText(it.dueAt, it.hasTime)
        info.urgency == "OVERDUE" || info.diffDays == 0
    }

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                UniTrackLogger.i("POST_NOTIFICATIONS permission granted")
            } else {
                UniTrackLogger.w("POST_NOTIFICATIONS permission denied by user")
            }
        }

        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 4 Bottom Navigation items
    val bottomNavItems = listOf(
        Screen.Today,
        Screen.Routine,
        Screen.Tasks,
        Screen.More
    )

    val isBottomBarVisible = currentRoute in bottomNavItems.map { it.route }
    val showAppTopBar = currentRoute in bottomNavItems.map { it.route }

    val topBarTitle = when (currentRoute) {
        Screen.Today.route -> "UniTrack"
        Screen.Routine.route -> "Routine"
        Screen.Tasks.route -> "Tasks & Quizzes"
        Screen.More.route -> "Settings & Tools"
        else -> "UniTrack"
    }

    val topBarSubtitle = when (currentRoute) {
        Screen.Today.route -> "Academic Routine"
        Screen.Routine.route -> "Class Timetable"
        Screen.Tasks.route -> "Academic Planner"
        Screen.More.route -> "System Preferences"
        else -> "AIUB Student"
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (showAppTopBar) {
                    AppTopBar(
                        title = topBarTitle,
                        subtitle = topBarSubtitle,
                        badgeCount = overdueCount,
                        onNotificationsClick = {
                            if (currentRoute != Screen.Tasks.route) {
                                navController.navigate(Screen.Tasks.route)
                            }
                        },
                        isDark = isDark,
                        onThemeToggle = onToggleTheme
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    GlassBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Today.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Today.route) {
                    TodayScreen(
                        onNavigateToPortal = { navController.navigate(Screen.Portal.route) },
                        onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                        onNavigateToRoutine = { navController.navigate(Screen.Routine.route) },
                        onOpenCourse = { courseKey -> navController.navigate(Screen.Course.createRoute(courseKey)) }
                    )
                }

                composable(Screen.Routine.route) {
                    RoutineScreen(
                        onOpenCourse = { courseKey -> navController.navigate(Screen.Course.createRoute(courseKey)) }
                    )
                }

                composable(Screen.Tasks.route) {
                    TasksScreen(
                        onOpenCourse = { courseKey -> navController.navigate(Screen.Course.createRoute(courseKey)) }
                    )
                }

                composable(
                    route = Screen.Course.route,
                    arguments = listOf(navArgument("courseKey") { type = NavType.StringType })
                ) { backStackEntry ->
                    val rawKey = backStackEntry.arguments?.getString("courseKey") ?: ""
                    val decodedKey = URLDecoder.decode(rawKey, "UTF-8")
                    val courseVm: CourseViewModel = viewModel {
                        CourseViewModel(context.applicationContext as android.app.Application, decodedKey)
                    }
                    CourseScreen(
                        viewModel = courseVm,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.More.route) {
                    MoreScreen(
                        onNavigateToPortal = { navController.navigate(Screen.Portal.route) },
                        onNavigateToCapture = { navController.navigate(Screen.PageCapture.route) },
                        onNavigateToDebug = { navController.navigate(Screen.ParserDebug.route) },
                        onNavigateToNotices = { navController.navigate(Screen.Notices.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToDebugLogs = { navController.navigate(Screen.DebugLog.route) },
                        onNavigateToDesignSystem = { navController.navigate(Screen.DesignSystem.route) }
                    )
                }

                // Sub-screens
                composable(Screen.Portal.route) {
                    PortalScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDebug = { navController.navigate(Screen.ParserDebug.route) },
                        onNavigateToCaptures = { navController.navigate(Screen.PageCapture.route) }
                    )
                }

                composable(Screen.PageCapture.route) {
                    val debugVm: ParserDebugViewModel = viewModel()
                    PageCaptureScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDebugWithPage = { page ->
                            debugVm.loadPage(page)
                            navController.navigate(Screen.ParserDebug.route)
                        }
                    )
                }

                composable(Screen.ParserDebug.route) {
                    ParserDebugScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDebugLogs = { navController.navigate(Screen.DebugLog.route) },
                        onNavigateToDesignSystem = { navController.navigate(Screen.DesignSystem.route) }
                    )
                }

                composable(Screen.Notices.route) {
                    NoticesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.DebugLog.route) {
                    DebugLogScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.DesignSystem.route) {
                    DesignSystemScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
