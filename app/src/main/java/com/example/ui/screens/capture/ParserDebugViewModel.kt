package com.example.ui.screens.capture

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.CapturedPageEntity
import com.example.util.CapturedPageHolder
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

data class MatchResult(
    val index: Int,
    val tagName: String,
    val text: String,
    val htmlSnippet: String
)

data class ParserDebugUiState(
    val currentHtml: String = "",
    val pageTitle: String = "No Page Loaded",
    val pageUrl: String = "",
    val hasIframes: Boolean = false,
    val timestamp: Long = 0L,
    val cssSelector: String = "table",
    val matchResults: List<MatchResult> = emptyList(),
    val totalMatches: Int = 0,
    val isTesting: Boolean = false,
    val errorMessage: String? = null
)

class ParserDebugViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val dao = app.database.capturedPageDao()

    private val _uiState = MutableStateFlow(ParserDebugUiState())
    val uiState: StateFlow<ParserDebugUiState> = _uiState.asStateFlow()

    init {
        // 1. Observe shared live captured page state
        viewModelScope.launch {
            CapturedPageHolder.livePage.collect { live ->
                if (live.html.isNotBlank()) {
                    UniTrackLogger.d("ParserDebugViewModel received live page update: length=${live.html.length}, url=${live.url}")
                    _uiState.update {
                        it.copy(
                            currentHtml = live.html,
                            pageTitle = live.title.ifBlank { "Live Captured Page" },
                            pageUrl = live.url,
                            hasIframes = live.hasIframes,
                            timestamp = live.timestamp,
                            errorMessage = null
                        )
                    }
                    testSelector(_uiState.value.cssSelector)
                }
            }
        }

        // 2. Fallback to latest database capture if live holder is empty
        viewModelScope.launch {
            dao.getLatestCapturedPage().collect { latest ->
                if (latest != null && _uiState.value.currentHtml.isEmpty()) {
                    loadPage(latest)
                }
            }
        }
    }

    fun loadPage(page: CapturedPageEntity) {
        val containsIframes = page.htmlContent.contains("<iframe", ignoreCase = true)
        _uiState.update {
            it.copy(
                currentHtml = page.htmlContent,
                pageTitle = page.title,
                pageUrl = page.url,
                hasIframes = containsIframes,
                timestamp = page.capturedAt,
                errorMessage = null
            )
        }
        testSelector(_uiState.value.cssSelector)
    }

    fun onSelectorChanged(selector: String) {
        _uiState.update { it.copy(cssSelector = selector) }
    }

    fun testSelector(selector: String = _uiState.value.cssSelector) {
        val html = _uiState.value.currentHtml
        if (html.isBlank()) {
            _uiState.update {
                it.copy(
                    matchResults = emptyList(),
                    totalMatches = 0,
                    errorMessage = "No HTML captured yet. Open the Portal tab and tap 'Capture'."
                )
            }
            return
        }

        if (selector.isBlank()) {
            _uiState.update { it.copy(matchResults = emptyList(), totalMatches = 0, errorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null) }
            try {
                val results = withContext(Dispatchers.Default) {
                    val doc: Document = Jsoup.parse(html)
                    val elements = doc.select(selector)
                    elements.take(40).mapIndexed { idx, el ->
                        MatchResult(
                            index = idx + 1,
                            tagName = el.tagName(),
                            text = el.text().take(180),
                            htmlSnippet = el.outerHtml().take(250)
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        matchResults = results,
                        totalMatches = results.size,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        matchResults = emptyList(),
                        totalMatches = 0,
                        errorMessage = "Invalid CSS Selector: ${e.message}"
                    )
                }
            }
        }
    }

    fun copyRawHtml(context: Context) {
        val html = _uiState.value.currentHtml
        if (html.isBlank()) {
            Toast.makeText(context, "No HTML to copy", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Raw HTML", html)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied full HTML (${html.length} chars)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Clipboard failed (size too large). Use 'Share as File'.", Toast.LENGTH_LONG).show()
        }
    }

    fun copyCleanedHtml(context: Context) {
        val html = _uiState.value.currentHtml
        if (html.isBlank()) {
            Toast.makeText(context, "No HTML to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val cleaned = CapturedPageHolder.stripScriptsAndStyles(html)
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Cleaned HTML", cleaned)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied cleaned HTML (${cleaned.length} chars, scripts & styles removed)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Clipboard failed. Use 'Share as File'.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareHtmlAsFile(context: Context) {
        val html = _uiState.value.currentHtml
        if (html.isBlank()) {
            Toast.makeText(context, "No HTML to share", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "html_captures")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val time = System.currentTimeMillis()
                val file = File(cacheDir, "portal_capture_$time.html")
                file.writeText(html)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "AIUB Portal Capture: ${_uiState.value.pageTitle}")
                    putExtra(Intent.EXTRA_TEXT, "Captured from: ${_uiState.value.pageUrl}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(intent, "Share HTML File")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                UniTrackLogger.e("Failed to share HTML as file: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
