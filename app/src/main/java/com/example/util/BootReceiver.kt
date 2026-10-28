package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.UniTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val app = context.applicationContext as UniTrackApplication
            CoroutineScope(Dispatchers.IO).launch {
                val items = app.database.courseItemDao().getPendingItemsDueAfter(System.currentTimeMillis())
                CourseItemReminderScheduler.rescheduleAll(context, items)
            }
        }
    }
}
