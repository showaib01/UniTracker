package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONTokener

data class LiveCapturedPage(
    val url: String = "",
    val title: String = "",
    val html: String = "",
    val timestamp: Long = 0L,
    val hasIframes: Boolean = false,
    val statusMessage: String? = null
)

object CapturedPageHolder {

    private val _livePage = MutableStateFlow(LiveCapturedPage())
    val livePage: StateFlow<LiveCapturedPage> = _livePage.asStateFlow()

    fun update(url: String, title: String, html: String, message: String? = null) {
        val containsIframes = html.contains("<iframe", ignoreCase = true)
        _livePage.value = LiveCapturedPage(
            url = url,
            title = title,
            html = html,
            timestamp = System.currentTimeMillis(),
            hasIframes = containsIframes,
            statusMessage = message
        )
        UniTrackLogger.d("CapturedPageHolder updated: length=${html.length}, url=$url, hasIframes=$containsIframes")
    }

    /**
     * Decodes the raw JSON-encoded string returned by evaluateJavascript.
     */
    fun decodeJsHtml(raw: String?): String {
        if (raw == null || raw.trim() == "null" || raw.isBlank()) {
            return ""
        }
        return try {
            val tokener = JSONTokener(raw)
            val value = tokener.nextValue()
            value.toString()
        } catch (e: Exception) {
            UniTrackLogger.w("JSONTokener unescape fallback: ${e.message}")
            var s = raw.trim()
            if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                s = s.substring(1, s.length - 1)
            }
            s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
                .replace("\\u003C", "<")
                .replace("\\u003c", "<")
                .replace("\\u003E", ">")
                .replace("\\u003e", ">")
                .replace("\\u0026", "&")
        }
    }

    /**
     * Strips <script> and <style> tags to reduce size for clipboard and cleaner parsing inspection.
     */
    fun stripScriptsAndStyles(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .trim()
    }
}
