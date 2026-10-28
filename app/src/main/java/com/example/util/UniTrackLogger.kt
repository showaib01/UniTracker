package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UniTrackLogger {
    const val TAG = "UniTrack"
    private const val MAX_LOGS = 200

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logList = mutableListOf<String>()
    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    @Synchronized
    fun d(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG] [DEBUG] $message")
        }
        appendLog("DEBUG", message)
    }

    @Synchronized
    fun i(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG] [INFO] $message")
        }
        appendLog("INFO", message)
    }

    @Synchronized
    fun w(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            System.err.println("[$TAG] [WARN] $message")
        }
        appendLog("WARN", message)
    }

    @Synchronized
    fun e(message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message | ${throwable.message}" else message
        try {
            Log.e(TAG, fullMsg, throwable)
        } catch (_: Throwable) {
            System.err.println("[$TAG] [ERROR] $fullMsg")
        }
        appendLog("ERROR", fullMsg)
    }

    private fun appendLog(level: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] [$level] $message"
        if (logList.size >= MAX_LOGS) {
            logList.removeAt(0)
        }
        logList.add(entry)
        _logsFlow.value = logList.toList()
    }

    @Synchronized
    fun getAllLogsText(): String {
        return logList.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        logList.clear()
        _logsFlow.value = emptyList()
    }
}
