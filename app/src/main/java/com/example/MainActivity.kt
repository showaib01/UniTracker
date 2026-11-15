package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.sync.PortalSyncWorker
import com.example.ui.components.AppBackground
import com.example.ui.components.AppTopBar
import com.example.ui.components.GlassBottomBar
import com.example.ui.navigation.Screen
import com.example.ui.screens.course.CourseMaterialsScreen
import com.example.ui.screens.course.CourseScreen
import com.example.ui.screens.course.CourseViewModel
import com.example.ui.screens.course.DownloadsScreen
import com.example.ui.screens.more.NoticesScreen
import com.example.ui.screens.more.SettingsScreen
import com.example.ui.screens.portal.PortalScreen
import com.example.ui.screens.routine.RoutineScreen
import com.example.ui.screens.tasks.TasksScreen
import com.example.ui.screens.today.TodayScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DateTimeUtils
import com.example.util.NotificationNav
import com.example.util.UniTrackLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class NavPayload(
    val target: String,
    val courseKey: String?,
    val itemId: Long?
)

class MainActivity : ComponentActivity() {
    private val _navPayloadState = androidx.compose.runtime.mutableStateOf<NavPayload?>(null)
    val navPayloadState: androidx.compose.runtime.State<NavPayload?> = _navPayloadState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val target = intent.getStringExtra(NotificationNav.EXTRA_NAV_TARGET)
        if (!target.isNullOrBlank()) {
            val courseKey = intent.getStringExtra(NotificationNav.EXTRA_COURSE_KEY)
            val itemId = if (intent.hasExtra(NotificationNav.EXTRA_ITEM_ID)) intent.getLongExtra(NotificationNav.EXTRA_ITEM_ID, -1L) else null
            _navPayloadState.value = NavPayload(target, courseKey, itemId)
            intent.removeExtra(NotificationNav.EXTRA_NAV_TARGET)
            intent.removeExtra(NotificationNav.EXTRA_COURSE_KEY)
            intent.removeExtra(NotificationNav.EXTRA_ITEM_ID)
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

    val navPayload = (context as? MainActivity)?.navPayloadState?.value

    LaunchedEffect(navPayload) {
        if (navPayload != null) {
            when (navPayload.target) {
                NotificationNav.TARGET_COURSE -> {
                    val key = navPayload.courseKey
                    if (!key.isNullOrBlank()) {
                        navController.navigate(Screen.Course.createRoute(key)) {
                            popUpTo(Screen.Today.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(Screen.Today.route) {
                            popUpTo(Screen.Today.route) { inclusive = true }
                        }
                    }
                }
                NotificationNav.TARGET_PORTAL -> {
                    navController.navigate(Screen.Portal.createRoute()) {
                        popUpTo(Screen.Today.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                NotificationNav.TARGET_TASKS -> {
                    navController.navigate(Screen.Tasks.route) {
                        popUpTo(Screen.Today.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                else -> {
                    navController.navigate(Screen.Today.route) {
                        popUpTo(Screen.Today.route) { inclusive = true }
                    }
                }
            }
        }
    }

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

    val isPortalRoute = currentRoute?.startsWith("portal") == true
    val isSettingsSubRoute = isPortalRoute || currentRoute == Screen.CourseMaterials.route || currentRoute == Screen.Downloads.route || currentRoute == Screen.Notices.route
    val isBottomBarVisible = isPortalRoute || currentRoute in bottomNavItems.map { it.route }
    val bottomBarActiveRoute = if (isPortalRoute) Screen.More.route else currentRoute

    val showAppTopBar = currentRoute in listOf(
        Screen.Today.route,
        Screen.Routine.route,
        Screen.Tasks.route
    )

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

    // Unified Top-Level Navigation Helper with Settings Sub-Screen Popfix & non-tab pop loop
    val navigateToTopLevelTab: (String) -> Unit = { route ->
        val currentDestRoute = navController.currentBackStackEntry?.destination?.route
        val tabRoutes = bottomNavItems.map { it.route }

        if (route == Screen.More.route && isSettingsSubRoute) {
            navController.popBackStack(Screen.More.route, inclusive = false)
        } else if (currentRoute == route) {
            navController.popBackStack(route, inclusive = false)
        } else {
            val controller = navController
            while (controller.currentBackStackEntry?.destination?.route != null &&
                controller.currentBackStackEntry?.destination?.route !in tabRoutes) {
                if (!controller.popBackStack()) break
            }
            controller.navigate(route) {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val onBackCallback: () -> Unit = {
        if (!navController.popBackStack()) {
            navigateToTopLevelTab(Screen.Today.route)
        }
    }

    // Unified Single BackHandler Policy
    val isTopLevel = currentRoute in bottomNavItems.map { it.route }
    val isHome = currentRoute == Screen.Today.route

    BackHandler(enabled = !isHome) {
        if (isTopLevel) {
            navigateToTopLevelTab(Screen.Today.route)
        } else {
            onBackCallback()
        }
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
                            navigateToTopLevelTab(Screen.Tasks.route)
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
                        currentRoute = bottomBarActiveRoute,
                        onNavigate = { route ->
                            navigateToTopLevelTab(route)
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
                        onNavigateToPortal = { navController.navigate(Screen.Portal.createRoute()) },
                        onNavigateToTasks = { navigateToTopLevelTab(Screen.Tasks.route) },
                        onNavigateToRoutine = { navigateToTopLevelTab(Screen.Routine.route) },
                        onOpenCourse = { courseKey -> navController.navigate(Screen.Course.createRoute(courseKey)) }
                    )
                }

                composable(Screen.Routine.route) {
                    RoutineScreen(
                        onOpenCourse = { courseKey -> navController.navigate(Screen.Course.createRoute(courseKey)) },
                        onNavigateToPortal = { navController.navigate(Screen.Portal.createRoute()) }
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
                        onNavigateBack = onBackCallback
                    )
                }

                composable(Screen.More.route) {
                    SettingsScreen(
                        onNavigateToPortal = { navController.navigate(Screen.Portal.createRoute()) },
                        onNavigateToNotices = { navController.navigate(Screen.Notices.route) },
                        onNavigateToCourseMaterials = { navController.navigate(Screen.CourseMaterials.route) },
                        onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) }
                    )
                }

                // Sub-screens
                composable(
                    route = "portal?courseKey={courseKey}",
                    arguments = listOf(navArgument("courseKey") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val rawKey = backStackEntry.arguments?.getString("courseKey")
                    val decodedKey = if (!rawKey.isNullOrBlank()) URLDecoder.decode(rawKey, "UTF-8") else null
                    PortalScreen(
                        onNavigateBack = onBackCallback,
                        selectedCourseKey = decodedKey
                    )
                }

                composable(Screen.Notices.route) {
                    NoticesScreen(
                        onNavigateBack = onBackCallback
                    )
                }

                composable(Screen.CourseMaterials.route) {
                    CourseMaterialsScreen(
                        onNavigateBack = onBackCallback,
                        onSelectCourseForDownload = { courseKey -> navController.navigate(Screen.Portal.createRoute(courseKey)) }
                    )
                }

                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        onNavigateBack = onBackCallback
                    )
                }
            }
        }
    }
}
