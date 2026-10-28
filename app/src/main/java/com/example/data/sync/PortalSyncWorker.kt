package com.example.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.UniTrackApplication
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.data.parser.AttendanceParser
import com.example.data.parser.PortalSelectorConfig
import com.example.data.parser.RoutineParser
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PortalSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val app = context.applicationContext as UniTrackApplication
    private val routineDao = app.database.routineDao()
    private val attendanceDao = app.database.attendanceDao()
    private val prefsRepo = app.preferencesRepository

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        UniTrackLogger.d("PortalSyncWorker: Starting sync job (attempt=$runAttemptCount)")

        try {
            val prefs = prefsRepo.userPreferencesFlow.first()
            val portalBaseUrl = prefs.portalUrl.ifBlank { UserPreferencesRepository.DEFAULT_PORTAL_URL }.trimEnd('/')

            // Check CookieManager session
            val cookies = CookieManager.getInstance().getCookie(portalBaseUrl)
            UniTrackLogger.d("PortalSyncWorker: Cookie check for '$portalBaseUrl' -> present=${!cookies.isNullOrBlank()}, storedSessionActive=${prefs.isSessionActive}")

            val hasSessionCookies = !cookies.isNullOrBlank() && (
                    cookies.contains("ASP.NET_SessionId") ||
                            cookies.contains(".ASPX") ||
                            cookies.contains("session", ignoreCase = true)
                    )

            if (!hasSessionCookies && !prefs.isSessionActive) {
                UniTrackLogger.w("PortalSyncWorker: Session inactive or cookies missing. Halting.")
                postSessionExpiredNotification(context)
                prefsRepo.updateSessionActive(false)
                return@withContext Result.success(
                    workDataOf(
                        KEY_RESULT_MESSAGE to "Login required",
                        KEY_IS_SUCCESS to false
                    )
                )
            }

            var routineSavedCount = 0
            var attendanceSavedCount = 0
            var parseErrorMessage: String? = null

            // 1. Sync Routine: use saved home/routine URL or fallback to portalBase + "/Student"
            val routineUrl = if (prefs.routineUrl.isNotBlank()) prefs.routineUrl.trim() else "$portalBaseUrl/Student"
            UniTrackLogger.d("PortalSyncWorker: Syncing routine from '$routineUrl'")

            val routineResult = HeadlessWebViewSyncHelper.loadPageAndCaptureHtml(context, routineUrl)

            if (routineResult.isLoginDetected) {
                UniTrackLogger.w("PortalSyncWorker: Login redirected during routine sync.")
                postSessionExpiredNotification(context)
                prefsRepo.updateSessionActive(false)
                return@withContext Result.success(
                    workDataOf(
                        KEY_RESULT_MESSAGE to "Login required",
                        KEY_IS_SUCCESS to false
                    )
                )
            }

            if (routineResult.isSuccess && routineResult.html.isNotBlank()) {
                val parserResult = RoutineParser().parse(routineResult.html)
                UniTrackLogger.d("PortalSyncWorker: RoutineParser parsed ${parserResult.data.size} items with ${parserResult.warnings.size} warnings")
                if (parserResult.data.isNotEmpty()) {
                    routineDao.replaceAllRoutines(parserResult.data)
                    routineSavedCount = parserResult.data.size
                    // Save routine URL if we synced from /Student successfully
                    if (prefs.routineUrl.isBlank() && routineResult.url.isNotBlank()) {
                        prefsRepo.updateRoutineUrl(routineResult.url)
                    }
                } else {
                    parseErrorMessage = parserResult.warnings.firstOrNull() ?: "No classes found in schedule"
                }
            } else {
                parseErrorMessage = routineResult.errorMessage ?: "Failed to load routine page"
            }

            // 2. Sync Attendance (optional if attendanceUrl has been captured)
            val attendanceUrl = prefs.attendanceUrl.trim()
            if (attendanceUrl.isNotBlank()) {
                UniTrackLogger.d("PortalSyncWorker: Syncing attendance from '$attendanceUrl'")
                val attendanceResult = HeadlessWebViewSyncHelper.loadPageAndCaptureHtml(context, attendanceUrl)

                if (attendanceResult.isLoginDetected) {
                    UniTrackLogger.w("PortalSyncWorker: Login redirected during attendance sync.")
                    postSessionExpiredNotification(context)
                    prefsRepo.updateSessionActive(false)
                    return@withContext Result.success(
                        workDataOf(
                            KEY_RESULT_MESSAGE to "Login required",
                            KEY_IS_SUCCESS to false
                        )
                    )
                }

                if (attendanceResult.isSuccess && attendanceResult.html.isNotBlank()) {
                    val parserResult = AttendanceParser().parse(attendanceResult.html)
                    if (parserResult.data.isNotEmpty()) {
                        attendanceDao.syncAttendancePreservingCorrections(parserResult.data)
                        attendanceSavedCount = parserResult.data.size
                    }
                }
            }

            // Update timestamp & session status if any data was parsed
            if (routineSavedCount > 0 || attendanceSavedCount > 0) {
                prefsRepo.updateLastSyncTimestamp()
                prefsRepo.updateSessionActive(true)
            }

            // Format UI result message
            val resultMessage = when {
                routineSavedCount > 0 -> {
                    if (attendanceSavedCount > 0) "Synced $routineSavedCount classes, $attendanceSavedCount courses"
                    else "Synced $routineSavedCount classes"
                }
                attendanceSavedCount > 0 -> "Synced $attendanceSavedCount courses"
                parseErrorMessage != null -> "Parse failed: $parseErrorMessage"
                else -> "Synced 0 classes"
            }

            UniTrackLogger.i("PortalSyncWorker finished: $resultMessage")

            Result.success(
                workDataOf(
                    KEY_RESULT_MESSAGE to resultMessage,
                    KEY_IS_SUCCESS to (routineSavedCount > 0 || attendanceSavedCount > 0),
                    KEY_ROUTINE_COUNT to routineSavedCount,
                    KEY_ATTENDANCE_COUNT to attendanceSavedCount
                )
            )
        } catch (e: Exception) {
            UniTrackLogger.e("PortalSyncWorker unexpected exception: ${e.message}", e)
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_RESULT_MESSAGE to "Parse failed: ${e.message}",
                        KEY_IS_SUCCESS to false
                    )
                )
            }
        }
    }

    private fun postSessionExpiredNotification(ctx: Context) {
        val channelId = "unitrack_sync_channel"
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Portal Sync & Session",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications regarding AIUB portal session status and background synchronization"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                UniTrackLogger.w("POST_NOTIFICATIONS permission not granted. Skipping system notification.")
                return
            }
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            ctx, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("UniTrack: Portal Session Expired")
            .setContentText("Session expired. Open UniTrack and log in once to refresh your routine.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Session expired. Open UniTrack and log in once to refresh your routine."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SESSION, notification)
        UniTrackLogger.d("PortalSyncWorker: Session expired notification posted.")
    }

    companion object {
        const val WORK_NAME_PERIODIC = "unitrack_portal_periodic_sync"
        const val WORK_NAME_ONETIME = "unitrack_portal_manual_sync"
        const val NOTIFICATION_ID_SESSION = 1001

        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_IS_SUCCESS = "is_success"
        const val KEY_ROUTINE_COUNT = "routine_count"
        const val KEY_ATTENDANCE_COUNT = "attendance_count"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<PortalSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            UniTrackLogger.d("Scheduled periodic sync (12h interval, network connected)")
        }

        fun createOneTimeWorkRequest(): androidx.work.OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<PortalSyncWorker>()
                .setConstraints(constraints)
                .build()
        }

        fun triggerManualSync(context: Context) {
            val oneTimeRequest = createOneTimeWorkRequest()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            UniTrackLogger.d("Triggered manual sync (ExistingWorkPolicy.REPLACE)")
        }
    }
}
