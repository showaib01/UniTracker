package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        UniTrackLogger.i("UniTrack: BootReceiver onReceive action=$action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(context)
            } catch (e: Exception) {
                UniTrackLogger.e("UniTrack: Error rescheduling alarms in BootReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
