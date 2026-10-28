package com.example.ui.screens.capture

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniTrackApplication
import com.example.data.local.entity.CapturedPageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PageCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UniTrackApplication
    private val dao = app.database.capturedPageDao()

    val capturedPages = dao.getAllCapturedPages()

    private val _selectedPage = MutableStateFlow<CapturedPageEntity?>(null)
    val selectedPage: StateFlow<CapturedPageEntity?> = _selectedPage.asStateFlow()

    fun selectPage(page: CapturedPageEntity) {
        _selectedPage.value = page
    }

    fun clearSelectedPage() {
        _selectedPage.value = null
    }

    fun copyHtmlToClipboard(context: Context, html: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Portal Rendered HTML", html)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Full HTML copied to clipboard! (${html.length} chars)", Toast.LENGTH_SHORT).show()
    }

    fun shareHtml(context: Context, page: CapturedPageEntity) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AIUB Portal Capture: ${page.title}")
                putExtra(Intent.EXTRA_TEXT, "URL: ${page.url}\nType: ${page.pageType}\n\n--- HTML CONTENT ---\n${page.htmlContent}")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Captured Portal HTML"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share chooser", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteCapture(page: CapturedPageEntity) {
        viewModelScope.launch {
            dao.deletePage(page)
            if (_selectedPage.value?.id == page.id) {
                _selectedPage.value = null
            }
        }
    }

    fun clearAllCaptures() {
        viewModelScope.launch {
            dao.clearAll()
            _selectedPage.value = null
        }
    }
}
