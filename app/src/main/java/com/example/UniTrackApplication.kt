package com.example

import android.app.Application
import com.example.data.local.UniTrackDatabase
import com.example.data.local.preferences.UserPreferencesRepository

class UniTrackApplication : Application() {

    val database: UniTrackDatabase by lazy {
        UniTrackDatabase.getDatabase(this)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            val config = androidx.work.Configuration.Builder().build()
            androidx.work.WorkManager.initialize(this, config)
        } catch (e: Exception) {
            // Already initialized
        }

        try {
            com.example.data.sync.PortalSyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            // Safe fallback during test execution
        }
    }

    companion object {
        lateinit var instance: UniTrackApplication
            private set
    }
}
