package com.example.ui.screens.portal

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.CapturedPageEntity
import com.example.data.local.preferences.UserPreferencesRepository
import com.example.data.parser.AttendanceParser
import com.example.data.parser.PortalSelectorConfig
import com.example.data.parser.RoutineParser
import com.example.util.CapturedPageHolder
import com.example.util.UniTrackLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortalUiState(
    val currentUrl: String = UserPreferencesRepository.DEFAULT_PORTAL_URL,
    val pageTitle: String = "AIUB Portal",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val isSessionActive: Boolean = false,
    val isSessionExpiredPrompt: Boolean = false,
    val studentId: String = "",
    val rememberStudentId: Boolean = false,
    val lastCapturedHtml: String? = null,
    val captureMessage: String? = null,
    val captureWarnings: List<String> = emptyList()
)

class PortalViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val routineDao = app.database.routineDao()
    private val attendanceDao = app.database.attendanceDao()
    private val capturedPageDao = app.database.capturedPageDao()
    private val preferencesRepository = app.preferencesRepository

    private val routineParser = RoutineParser()
    private val attendanceParser = AttendanceParser()

    private val _uiState = MutableStateFlow(PortalUiState())
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        currentUrl = if (current.currentUrl.isBlank()) prefs.portalUrl else current.currentUrl,
                        studentId = prefs.studentId,
                        rememberStudentId = prefs.rememberStudentId,
                        isSessionActive = prefs.isSessionActive
                    )
                }
            }
        }
    }

    fun onPageStarted(url: String) {
        UniTrackLogger.d("PortalViewModel onPageStarted: $url")
        _uiState.update { it.copy(isLoading = true, currentUrl = url) }
        checkSessionStatus(url, null)
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean) {
        UniTrackLogger.d("PortalViewModel onPageFinished: $url (title='$title')")
        _uiState.update {
            it.copy(
                isLoading = false,
                currentUrl = url,
                pageTitle = title ?: "AIUB Portal",
                canGoBack = canGoBack
            )
        }
        checkSessionStatus(url, null)
        autoCaptureRoutineIfPresent(url, title)
    }

    private fun autoCaptureRoutineIfPresent(url: String, title: String?) {
        val lowerUrl = url.lowercase()
        if (!lowerUrl.contains("aiub.edu") || lowerUrl.contains("/login")) return

        viewModelScope.launch {
            delay(1500)
            val webView = PortalWebViewHolder.getWebView() ?: return@launch
            val currentUrl = webView.url ?: url
            if (!currentUrl.lowercase().contains("aiub.edu") || currentUrl.lowercase().contains("/login")) return@launch

            webView.evaluateJavascript(
                "(function(){ return document.querySelector('div.scheduleTable') !== null ? document.documentElement.outerHTML : ''; })();"
            ) { rawJson ->
                if (rawJson == null || rawJson.trim() == "null" || rawJson.trim() == "\"\"") return@evaluateJavascript
                val decoded = CapturedPageHolder.decodeJsHtml(rawJson)
                if (decoded.isBlank() || !decoded.contains("scheduleTable")) return@evaluateJavascript

                val parseResult = routineParser.parse(decoded)
                if (parseResult.data.isNotEmpty()) {
                    UniTrackLogger.i("Auto-capture: Found scheduleTable on $currentUrl with ${parseResult.data.size} classes")
                    CapturedPageHolder.update(
                        currentUrl,
                        webView.title ?: title ?: "Class Schedule",
                        decoded,
                        "Auto-captured while browsing"
                    )

                    viewModelScope.launch {
                        routineDao.replaceAllRoutines(parseResult.data)
                        preferencesRepository.updateRoutineUrl(currentUrl)
                        preferencesRepository.updateSessionActive(true)
                        preferencesRepository.updateLastSyncTimestamp()

                        val msg = "Routine updated (${parseResult.data.size} classes)"
                        UniTrackLogger.i(msg)
                        _toastEvent.emit(msg)
                        _uiState.update { it.copy(captureMessage = msg, isSessionActive = true) }
                    }
                }
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    private fun checkSessionStatus(url: String, html: String?) {
        val isLogin = PortalSelectorConfig.isLoginPage(url, html)
        val isLoggedIn = PortalSelectorConfig.isLoggedIn(html)

        UniTrackLogger.d("checkSessionStatus: url=$url, isLogin=$isLogin, isLoggedIn=$isLoggedIn")

        val wasActive = _uiState.value.isSessionActive

        if (isLogin) {
            if (wasActive) {
                UniTrackLogger.w("checkSessionStatus: Transitioned to login. Marking session expired.")
                _uiState.update { it.copy(isSessionActive = false, isSessionExpiredPrompt = true) }
                viewModelScope.launch { preferencesRepository.updateSessionActive(false) }
            }
        } else if (isLoggedIn || (url.contains("portal.aiub.edu") && !url.contains("/login", ignoreCase = true))) {
            _uiState.update { it.copy(isSessionActive = true, isSessionExpiredPrompt = false) }
            viewModelScope.launch { preferencesRepository.updateSessionActive(true) }
        }
    }

    fun dismissSessionExpiredPrompt() {
        _uiState.update { it.copy(isSessionExpiredPrompt = false) }
    }

    fun saveStudentIdPreference(studentId: String, remember: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateStudentId(studentId, remember)
        }
    }

    /**
     * Captures HTML from the live WebView reference on the main thread using evaluateJavascript.
     */
    fun captureCurrentPage() {
        UniTrackLogger.d("PortalViewModel: captureCurrentPage() tapped")

        val webView = PortalWebViewHolder.getWebView()
        if (webView == null) {
            val errorMsg = "Capture error: WebView not initialized or lost reference."
            UniTrackLogger.e(errorMsg)
            _uiState.update { it.copy(captureMessage = errorMsg, captureWarnings = listOf(errorMsg)) }
            viewModelScope.launch { _toastEvent.emit(errorMsg) }
            return
        }

        val url = webView.url ?: ""
        val title = webView.title ?: "AIUB Portal"

        if (url.isBlank()) {
            val errorMsg = "Capture error: No URL currently loaded in WebView."
            UniTrackLogger.e(errorMsg)
            _uiState.update { it.copy(captureMessage = errorMsg, captureWarnings = listOf(errorMsg)) }
            viewModelScope.launch { _toastEvent.emit(errorMsg) }
            return
        }

        if (PortalSelectorConfig.isLoginPage(url, null)) {
            UniTrackLogger.w("Warning: Capturing on a login page ($url)")
        }

        UniTrackLogger.d("PortalViewModel: Calling evaluateJavascript on '$url'")
        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { rawJson ->
            UniTrackLogger.d("PortalViewModel evaluateJavascript returned: length=${rawJson?.length}")
            if (rawJson == null || rawJson.trim() == "null" || rawJson.isBlank()) {
                val errorMsg = "Capture error: JavaScript returned null or empty DOM."
                UniTrackLogger.e(errorMsg)
                _uiState.update { it.copy(captureMessage = errorMsg, captureWarnings = listOf(errorMsg)) }
                viewModelScope.launch { _toastEvent.emit(errorMsg) }
                return@evaluateJavascript
            }

            val decodedHtml = CapturedPageHolder.decodeJsHtml(rawJson)
            if (decodedHtml.isBlank()) {
                val errorMsg = "Capture error: Decoded HTML content is blank."
                UniTrackLogger.e(errorMsg)
                _uiState.update { it.copy(captureMessage = errorMsg, captureWarnings = listOf(errorMsg)) }
                viewModelScope.launch { _toastEvent.emit(errorMsg) }
                return@evaluateJavascript
            }

            UniTrackLogger.i("PortalViewModel: Successfully decoded ${decodedHtml.length} chars of HTML from $url")

            // Update shared state holder immediately
            CapturedPageHolder.update(
                url = url,
                title = title,
                html = decodedHtml,
                message = "Captured ${decodedHtml.length} characters from $url"
            )

            // Parse & save to Room
            onHtmlCaptured(decodedHtml, url, title)
        }
    }

    private fun onHtmlCaptured(html: String, url: String, title: String) {
        viewModelScope.launch {
            val lowerUrl = url.lowercase()

            val isRoutine = lowerUrl.contains("routine") || routineParser.canParse(html)
            val isAttendance = lowerUrl.contains("attendance") || attendanceParser.canParse(html)

            val pageType = when {
                isRoutine -> "ROUTINE"
                isAttendance -> "ATTENDANCE"
                lowerUrl.contains("grade") || lowerUrl.contains("result") -> "RESULTS"
                lowerUrl.contains("notice") -> "NOTICES"
                lowerUrl.contains("login") -> "LOGIN"
                else -> "PORTAL_PAGE"
            }

            var summaryText = "Captured ${html.length} characters from $url"
            val warningsList = mutableListOf<String>()

            if (html.contains("<iframe", ignoreCase = true)) {
                warningsList.add("Note: Page contains iframes. Main frame HTML captured.")
            }

            if (isRoutine) {
                // Auto-save routine URL for background sync
                preferencesRepository.updateRoutineUrl(url)
                UniTrackLogger.i("Auto-saved routine URL: $url")

                val result = routineParser.parse(html)
                warningsList.addAll(result.warnings)
                if (result.data.isNotEmpty()) {
                    routineDao.replaceAllRoutines(result.data)
                    preferencesRepository.updateLastSyncTimestamp()
                    preferencesRepository.updateSessionActive(true)
                    summaryText = "Captured ${html.length} chars: Saved ${result.data.size} classes to Routine & remembered URL!"
                } else {
                    summaryText = "Captured ${html.length} chars: 0 classes parsed (${result.warnings.firstOrNull() ?: "check table"})"
                }
            } else if (isAttendance) {
                // Auto-save attendance URL for background sync
                preferencesRepository.updateAttendanceUrl(url)
                UniTrackLogger.i("Auto-saved attendance URL: $url")

                val result = attendanceParser.parse(html)
                warningsList.addAll(result.warnings)
                if (result.data.isNotEmpty()) {
                    attendanceDao.syncAttendancePreservingCorrections(result.data)
                    preferencesRepository.updateLastSyncTimestamp()
                    preferencesRepository.updateSessionActive(true)
                    summaryText = "Captured ${html.length} chars: Saved ${result.data.size} courses to Attendance & remembered URL!"
                } else {
                    summaryText = "Captured ${html.length} chars: 0 courses parsed (${result.warnings.firstOrNull() ?: "check table"})"
                }
            }

            // Save to Room captured pages
            val entity = CapturedPageEntity(
                title = title.ifBlank { "Captured $pageType" },
                url = url,
                pageType = pageType,
                htmlContent = html,
                capturedAt = System.currentTimeMillis()
            )
            val id = capturedPageDao.insertPage(entity)

            val fullMsg = if (warningsList.isNotEmpty()) {
                "$summaryText (${warningsList.size} warnings) [ID #$id]"
            } else {
                "$summaryText [ID #$id]"
            }

            _uiState.update {
                it.copy(
                    lastCapturedHtml = html,
                    captureMessage = fullMsg,
                    captureWarnings = warningsList
                )
            }
            _toastEvent.emit(fullMsg)
        }
    }

    fun clearCaptureMessage() {
        _uiState.update { it.copy(captureMessage = null, captureWarnings = emptyList()) }
    }

    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies {
            viewModelScope.launch {
                preferencesRepository.updateSessionActive(false)
                _toastEvent.emit("Cookies cleared. Session ended.")
            }
        }
    }
}
