package com.example

import android.app.Application
import com.example.data.local.UniTrackDatabase
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.data.util.SampleDataGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UniTrackApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: UniTrackDatabase by lazy {
        UniTrackDatabase.getDatabase(this)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        applicationScope.launch {
            SampleDataGenerator.populateInitialDataIfEmpty(database)
        }

        try {
            com.example.data.sync.PortalSyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            // Safe fallback during Robolectric test execution
        }
    }

    companion object {
        lateinit var instance: UniTrackApplication
            private set
    }
}
