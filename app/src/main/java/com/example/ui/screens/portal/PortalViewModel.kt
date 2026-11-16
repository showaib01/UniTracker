package com.example.ui.screens.portal

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.auth.CredentialStore
import com.example.data.auth.LoginResult
import com.example.data.auth.PortalAuth
import com.example.data.local.entity.CapturedPageEntity
import com.example.data.parser.PageState
import com.example.data.parser.RoutineParser
import com.example.util.CapturedPageHolder
import com.example.util.UniTrackLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PortalEvent {
    data class SignedIn(val landingUrl: String) : PortalEvent
}

sealed class AutoSignInState {
    object Idle : AutoSignInState()
    data class Running(val attempt: Int, val max: Int) : AutoSignInState()
    data class Failed(val reason: String, val result: LoginResult? = null) : AutoSignInState()
}

data class PortalUiState(
    val currentUrl: String = "https://portal.aiub.edu/Student",
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
    val captureWarnings: List<String> = emptyList(),
    val captchaBitmap: Bitmap? = null,
    val isManualCaptchaStage: Boolean = false,
    val signInError: String? = null
)

class PortalViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val routineDao = app.database.routineDao()
    private val capturedPageDao = app.database.capturedPageDao()
    private val preferencesRepository = app.preferencesRepository

    private val routineParser = RoutineParser()

    private val _uiState = MutableStateFlow(PortalUiState())
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    private val _autoSignInState = MutableStateFlow<AutoSignInState>(AutoSignInState.Idle)
    val autoSignInState: StateFlow<AutoSignInState> = _autoSignInState.asStateFlow()

    private val _events = MutableSharedFlow<PortalEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PortalEvent> = _events.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var autoSignInJob: Job? = null

    private var manualCaptchaDeferred: CompletableDeferred<String?>? = null
    private var manualRefreshCallback: (suspend () -> Bitmap?)? = null

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        currentUrl = if (current.currentUrl.isBlank()) "https://portal.aiub.edu/Student" else current.currentUrl,
                        studentId = prefs.studentId,
                        rememberStudentId = prefs.rememberStudentId,
                        isSessionActive = prefs.isSessionActive
                    )
                }
            }
        }

        viewModelScope.launch {
            delay(300)
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (!prefs.isSessionActive && prefs.studentId.isNotBlank()) {
                val creds = CredentialStore(application).load()
                if (creds != null) {
                    startLogin(application, creds.first, creds.second, prefs.rememberStudentId)
                }
            }
        }
    }

    fun onPageStarted(url: String) {
        UniTrackLogger.d("PortalViewModel onPageStarted: $url")
        _uiState.update { it.copy(isLoading = true, currentUrl = url) }
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

    fun onLoginConfirmed() {
        _uiState.update { it.copy(isSessionActive = true, isSessionExpiredPrompt = false, isManualCaptchaStage = false, captchaBitmap = null) }
        viewModelScope.launch { preferencesRepository.updateSessionActive(true) }
    }

    fun handlePageState(state: PageState, url: String, hasSavedCreds: Boolean, isSheetVisible: Boolean) {
        val wasActive = _uiState.value.isSessionActive

        when (state) {
            PageState.APP -> {
                onLoginConfirmed()
            }
            PageState.LOGIN -> {
                if (wasActive && _autoSignInState.value !is AutoSignInState.Running) {
                    _uiState.update { it.copy(isSessionActive = false) }
                    viewModelScope.launch { preferencesRepository.updateSessionActive(false) }

                    if (!hasSavedCreds && !isSheetVisible) {
                        _uiState.update { it.copy(isSessionExpiredPrompt = true) }
                    }
                }
            }
            PageState.OTHER -> {}
        }
    }

    fun startLogin(context: Context, studentId: String, password: String, remember: Boolean) {
        if (studentId.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(signInError = "Please fill in both Student ID and Password.") }
            return
        }
        if (remember) {
            CredentialStore(context).save(studentId, password)
            saveStudentIdPreference(studentId, true)
        } else {
            CredentialStore(context).clear()
            saveStudentIdPreference(studentId, false)
        }

        _uiState.update { it.copy(signInError = null) }
        autoSignInJob?.cancel()

        autoSignInJob = viewModelScope.launch {
            _autoSignInState.value = AutoSignInState.Running(1, PortalAuth.MAX_SOLVER_ATTEMPTS)
            val result = try {
                PortalAuth.login(
                    context = context,
                    username = studentId,
                    password = password,
                    manualCaptcha = { bitmap, refresh ->
                        _uiState.update { it.copy(captchaBitmap = bitmap, isManualCaptchaStage = true, isLoading = false) }
                        manualRefreshCallback = refresh
                        val deferred = CompletableDeferred<String?>()
                        manualCaptchaDeferred = deferred
                        deferred.await()
                    },
                    onProgress = { attempt, max ->
                        _autoSignInState.update { AutoSignInState.Running(attempt, max) }
                    }
                )
            } catch (e: CancellationException) {
                _autoSignInState.value = AutoSignInState.Idle
                _uiState.update { it.copy(isManualCaptchaStage = false, captchaBitmap = null) }
                throw e
            }

            _uiState.update { it.copy(isManualCaptchaStage = false, captchaBitmap = null) }
            manualCaptchaDeferred = null
            manualRefreshCallback = null

            when (result) {
                is LoginResult.Success -> {
                    _autoSignInState.value = AutoSignInState.Idle
                    onLoginConfirmed()
                    _events.emit(PortalEvent.SignedIn(result.landingUrl))
                }
                is LoginResult.WrongCredentials -> {
                    CredentialStore(context).clear()
                    val err = "Incorrect Student ID or Password."
                    _uiState.update { it.copy(signInError = err) }
                    _autoSignInState.value = AutoSignInState.Failed(err, result)
                }
                is LoginResult.Locked, is LoginResult.CaptchaFailed, is LoginResult.NetworkError, is LoginResult.Cancelled -> {
                    val errorMsg = when (result) {
                        is LoginResult.Locked -> "Account is locked."
                        is LoginResult.NetworkError -> "Network error. Check your connection."
                        else -> "Sign in failed or cancelled."
                    }
                    _uiState.update { it.copy(signInError = errorMsg) }
                    _autoSignInState.value = AutoSignInState.Failed(errorMsg, result)
                }
            }
        }
    }

    fun submitManualCaptcha(answer: String) {
        if (answer.isBlank()) {
            _uiState.update { it.copy(signInError = "Please enter the captcha answer.") }
            return
        }
        _uiState.update { it.copy(signInError = null) }
        manualCaptchaDeferred?.complete(answer.trim())
    }

    fun refreshManualCaptcha() {
        val refresh = manualRefreshCallback ?: return
        viewModelScope.launch {
            val newBmp = refresh()
            if (newBmp != null) {
                _uiState.update { it.copy(captchaBitmap = newBmp) }
            }
        }
    }

    fun cancelAutoSignIn() {
        autoSignInJob?.cancel()
        autoSignInJob = null
        manualCaptchaDeferred?.complete(null)
        manualCaptchaDeferred = null
        manualRefreshCallback = null
        _autoSignInState.value = AutoSignInState.Idle
        _uiState.update { it.copy(isManualCaptchaStage = false, captchaBitmap = null, signInError = null) }
    }

    fun dismissSessionExpiredPrompt() {
        _uiState.update { it.copy(isSessionExpiredPrompt = false) }
    }

    fun saveStudentIdPreference(studentId: String, remember: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateStudentId(studentId, remember)
        }
    }

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

        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { rawJson ->
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

            CapturedPageHolder.update(
                url = url,
                title = title,
                html = decodedHtml,
                message = "Captured ${decodedHtml.length} characters from $url"
            )

            onHtmlCaptured(decodedHtml, url, title)
        }
    }

    private fun onHtmlCaptured(html: String, url: String, title: String) {
        viewModelScope.launch {
            val lowerUrl = url.lowercase()
            val isRoutine = lowerUrl.contains("routine") || routineParser.canParse(html)

            val pageType = when {
                isRoutine -> "ROUTINE"
                lowerUrl.contains("grade") || lowerUrl.contains("result") -> "RESULTS"
                lowerUrl.contains("notice") -> "Notices"
                lowerUrl.contains("login") -> "LOGIN"
                else -> "PORTAL_PAGE"
            }

            var summaryText = "Captured ${html.length} characters from $url"
            val warningsList = mutableListOf<String>()

            if (html.contains("<iframe", ignoreCase = true)) {
                warningsList.add("Note: Page contains iframes. Main frame HTML captured.")
            }

            if (isRoutine) {
                preferencesRepository.updateRoutineUrl(url)
                val result = routineParser.parse(html)
                warningsList.addAll(result.warnings)
                if (result.data.isNotEmpty()) {
                    routineDao.replaceAllRoutines(result.data)
                    preferencesRepository.updateLastSyncTimestamp()
                    preferencesRepository.updateSessionActive(true)
                    com.example.util.UniTrackAlarmScheduler.scheduleAllRemindersForNext14Days(app)
                    summaryText = "Captured ${html.length} chars: Saved ${result.data.size} classes to Routine & remembered URL!"
                }
            }

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

    override fun onCleared() {
        super.onCleared()
        autoSignInJob?.cancel()
    }
}
