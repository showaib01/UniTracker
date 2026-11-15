package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity

object NotificationNav {
    const val EXTRA_NAV_TARGET = "nav_target"
    const val EXTRA_COURSE_KEY = "nav_course_key"
    const val EXTRA_ITEM_ID = "nav_item_id"

    const val TARGET_COURSE = "COURSE"
    const val TARGET_TODAY = "TODAY"
    const val TARGET_TASKS = "TASKS"
    const val TARGET_PORTAL = "PORTAL"

    fun buildContentIntent(
        context: Context,
        requestCode: Int,
        target: String,
        courseKey: String? = null,
        itemId: Long? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET, target)
            if (!courseKey.isNullOrBlank()) putExtra(EXTRA_COURSE_KEY, courseKey)
            if (itemId != null && itemId > 0L) putExtra(EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
