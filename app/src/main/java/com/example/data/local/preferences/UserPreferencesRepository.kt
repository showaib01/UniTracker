package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unitrack_settings")

data class UserPreferences(
    val portalUrl: String = "https://portal.aiub.edu/",
    val routineUrl: String = "",
    val attendanceUrl: String = "",
    val studentId: String = "",
    val rememberStudentId: Boolean = false,
    val attendanceThreshold: Int = 80,
    val themeMode: String = "SYSTEM",
    val language: String = "en",
    val lastSyncTimestamp: Long = 0L,
    val isSessionActive: Boolean = false,
    val reminderMinutes: Int = 15,
    val allRemindersEnabled: Boolean = true,
    val defaultReminderOffsets: String = "1440,120"
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val PORTAL_URL = stringPreferencesKey("portal_url")
        val ROUTINE_URL = stringPreferencesKey("routine_url")
        val ATTENDANCE_URL = stringPreferencesKey("attendance_url")
        val STUDENT_ID = stringPreferencesKey("student_id")
        val REMEMBER_STUDENT_ID = booleanPreferencesKey("remember_student_id")
        val ATTENDANCE_THRESHOLD = intPreferencesKey("attendance_threshold")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val IS_SESSION_ACTIVE = booleanPreferencesKey("is_session_active")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val ALL_REMINDERS_ENABLED = booleanPreferencesKey("all_reminders_enabled")
        val DEFAULT_REMINDER_OFFSETS = stringPreferencesKey("default_reminder_offsets")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            portalUrl = preferences[PreferencesKeys.PORTAL_URL] ?: DEFAULT_PORTAL_URL,
            routineUrl = preferences[PreferencesKeys.ROUTINE_URL] ?: "",
            attendanceUrl = preferences[PreferencesKeys.ATTENDANCE_URL] ?: "",
            studentId = preferences[PreferencesKeys.STUDENT_ID] ?: "",
            rememberStudentId = preferences[PreferencesKeys.REMEMBER_STUDENT_ID] ?: false,
            attendanceThreshold = preferences[PreferencesKeys.ATTENDANCE_THRESHOLD] ?: 80,
            themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM",
            language = preferences[PreferencesKeys.LANGUAGE] ?: "en",
            lastSyncTimestamp = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L,
            isSessionActive = preferences[PreferencesKeys.IS_SESSION_ACTIVE] ?: false,
            reminderMinutes = preferences[PreferencesKeys.REMINDER_MINUTES] ?: 15,
            allRemindersEnabled = preferences[PreferencesKeys.ALL_REMINDERS_ENABLED] ?: true,
            defaultReminderOffsets = preferences[PreferencesKeys.DEFAULT_REMINDER_OFFSETS] ?: "1440,120"
        )
    }

    suspend fun updatePortalUrl(url: String) {
        val sanitized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PORTAL_URL] = sanitized
        }
    }

    suspend fun updateRoutineUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUTINE_URL] = url
        }
    }

    suspend fun updateAttendanceUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ATTENDANCE_URL] = url
        }
    }

    suspend fun updateStudentId(studentId: String, remember: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMEMBER_STUDENT_ID] = remember
            if (remember) {
                preferences[PreferencesKeys.STUDENT_ID] = studentId
            } else {
                preferences[PreferencesKeys.STUDENT_ID] = ""
            }
        }
    }

    suspend fun updateAttendanceThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ATTENDANCE_THRESHOLD] = threshold.coerceIn(50, 100)
        }
    }

    suspend fun updateThemeMode(themeMode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode
        }
    }

    suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = lang
        }
    }

    suspend fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun updateSessionActive(isActive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SESSION_ACTIVE] = isActive
        }
    }

    suspend fun updateReminderMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDER_MINUTES] = minutes
        }
    }

    suspend fun updateAllRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALL_REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun updateDefaultReminderOffsets(offsets: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_REMINDER_OFFSETS] = offsets
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        const val DEFAULT_PORTAL_URL = "https://portal.aiub.edu/"
        const val AIUB_HOST = "portal.aiub.edu"
    }
}
