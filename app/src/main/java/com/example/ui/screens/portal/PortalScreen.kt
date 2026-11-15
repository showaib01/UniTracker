package com.example.ui.screens.portal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.auth.CredentialStore
import com.example.data.auth.TesseractCaptchaSolver
import com.example.data.download.CourseDownloadManager
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.parser.PortalSelectorConfig
import com.example.ui.components.GlassCard
import com.example.ui.theme.UniTrackTheme
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class DownloadPendingParams(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?
)

private class PortalWebAppBridge(
    private val viewModel: PortalViewModel
) {
    @JavascriptInterface
    fun onLoginSubmit(id: String, pass: String) {
        val webView = PortalWebViewHolder.getWebView()
        val currentUrl = webView?.url ?: ""
        if (currentUrl.startsWith("https://portal.aiub.edu", ignoreCase = true)) {
            viewModel.onCredentialsCaptured(id, pass)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
    onNavigateBack: () -> Unit,
    selectedCourseKey: String? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToCaptures: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.UniTrackApplication
    val scope = rememberCoroutineScope()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val routinesFlow = remember { app.database.routineDao().getAllRoutines() }
    val routines by routinesFlow.collectAsState(initial = emptyList())

    val distinctCourses = remember(routines) {
        routines.distinctBy { CourseSettingEntity.makeKey(it.courseName, it.section) }
    }

    val initialCourse = remember(distinctCourses, selectedCourseKey) {
        distinctCourses.firstOrNull { CourseSettingEntity.makeKey(it.courseName, it.section) == selectedCourseKey }
    }

    var activeCourseKey by remember { mutableStateOf(selectedCourseKey) }
    var activeCourseFolder by remember {
        mutableStateOf(
            if (initialCourse != null) Pair(initialCourse.courseCode.ifBlank { "COURSE" }, initialCourse.courseName) else null
        )
    }

    var pendingDownload by remember { mutableStateOf<DownloadPendingParams?>(null) }
    var showFolderPickerSheet by remember { mutableStateOf(false) }

    // Toolbar collapse/expand state (default collapsed)
    var isToolbarExpanded by remember { mutableStateOf(false) }

    // Auto-login attempt tracking
    var autoLoginAttempts by remember { mutableIntStateOf(0) }
    var lastVisitedUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
            if (msg.contains("error", ignoreCase = true)) {
                // Keep error feedback visible
            }
        }
    }

    // Handle Download Manager snackbars with "Open" action
    LaunchedEffect(Unit) {
        CourseDownloadManager.snackbarEvents.collectLatest { (message, filePath) ->
            if (filePath != null) {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Open",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        val file = File(filePath)
                        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open Downloaded File"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Handle system back gesture
    BackHandler(enabled = uiState.canGoBack) {
        val webView = PortalWebViewHolder.getWebView()
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onNavigateBack()
        }
    }

    if (uiState.isSessionExpiredPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSessionExpiredPrompt() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = stringResource(R.string.portal_session_expired)) },
            text = { Text("Your portal session has expired or redirected to login. Please log in again to sync your routine and attendance.") },
            confirmButton = {
                Button(onClick = { viewModel.dismissSessionExpiredPrompt() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("portal_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Stable WebView in background filling the screen
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        configureWebView(this, context)

                        addJavascriptInterface(PortalWebAppBridge(viewModel), "UniTrackBridge")

                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            if (activeCourseFolder != null) {
                                val (cCode, cName) = activeCourseFolder!!
                                CourseDownloadManager.enqueueDownload(
                                    context = context,
                                    url = url,
                                    userAgent = userAgent,
                                    contentDisposition = contentDisposition,
                                    mimeType = mimeType,
                                    courseKey = activeCourseKey ?: "OTHER",
                                    courseCode = cCode,
                                    courseName = cName
                                )
                            } else {
                                pendingDownload = DownloadPendingParams(url, userAgent, contentDisposition, mimeType)
                                showFolderPickerSheet = true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUri = request?.url ?: return false
                                val host = targetUri.host?.lowercase() ?: ""

                                if (host == "portal.aiub.edu" || host.endsWith(".aiub.edu")) {
                                    return false
                                }

                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, targetUri)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Could not open external link", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    if (it != lastVisitedUrl) {
                                        autoLoginAttempts = 0
                                        lastVisitedUrl = it
                                    }
                                    viewModel.onPageStarted(it)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val currentUrlStr = url ?: view?.url ?: ""
                                viewModel.onPageFinished(currentUrlStr, view?.title, view?.canGoBack() ?: false)

                                if (PortalSelectorConfig.isLoginPage(currentUrlStr, null)) {
                                    // 1. Inject first-login capture listener
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            if (window.uniTrackCaptured) return;
                                            window.uniTrackCaptured = true;
                                            var u = document.querySelector('input[name*="username"], input[name*="Username"], input[id*="username"], input[type="text"]');
                                            var p = document.querySelector('input[type="password"]');
                                            var f = document.querySelector('form');
                                            var btn = document.querySelector('button[type="submit"], input[type="submit"], #btnLogin, .btn-login');
                                            
                                            function sendCreds() {
                                                var id = u ? u.value : '';
                                                var pass = p ? p.value : '';
                                                if (id && pass && window.UniTrackBridge) {
                                                    window.UniTrackBridge.onLoginSubmit(id, pass);
                                                }
                                            }
                                            if (f) f.addEventListener('submit', sendCreds);
                                            if (btn) btn.addEventListener('click', sendCreds);
                                        })();
                                        """.trimIndent(),
                                        null
                                    )

                                    // 2. Auto-login if credentials exist
                                    val credStore = CredentialStore(context)
                                    if (credStore.hasCredentials()) {
                                        view?.evaluateJavascript("(function(){ return document.body ? document.body.innerText : ''; })();") { bodyText ->
                                            val text = bodyText?.lowercase() ?: ""
                                            val isErrorOnPage = text.contains("invalid student id or password") ||
                                                    text.contains("invalid user") ||
                                                    text.contains("incorrect password")

                                            if (isErrorOnPage) {
                                                UniTrackLogger.w("PortalScreen: Wrong credentials error detected on page. Clearing CredentialStore.")
                                                credStore.clear()
                                                autoLoginAttempts = 3
                                                Toast.makeText(context, "Saved login cleared due to incorrect credentials", Toast.LENGTH_SHORT).show()
                                            } else if (autoLoginAttempts < 3) {
                                                autoLoginAttempts++
                                                val savedCreds = credStore.load()
                                                if (savedCreds != null) {
                                                    scope.launch(Dispatchers.Default) {
                                                        UniTrackLogger.i("PortalScreen: Running auto-login attempt $autoLoginAttempts/3")
                                                        val captchaAns = TesseractCaptchaSolver().solveCaptcha(context, view, currentUrlStr)
                                                        if (!captchaAns.isNullOrBlank()) {
                                                            withContext(Dispatchers.Main) {
                                                                val (sId, sPass) = savedCreds
                                                                val escapedId = sId.replace("'", "\\'")
                                                                val escapedPass = sPass.replace("'", "\\'")
                                                                val jsSubmit = """
                                                                    (function() {
                                                                        var u = document.querySelector('input[name*="username"], input[name*="Username"], input[id*="username"], input[type="text"]');
                                                                        var p = document.querySelector('input[type="password"]');
                                                                        var c = document.querySelector('input[name*="captcha"], input[id*="captcha"], input[name*="Captcha"]');
                                                                        if (u) u.value = '$escapedId';
                                                                        if (p) p.value = '$escapedPass';
                                                                        if (c) c.value = '$captchaAns';
                                                                        var btn = document.querySelector('button[type="submit"], input[type="submit"], #btnLogin, .btn-login');
                                                                        if (btn) btn.click();
                                                                        else {
                                                                            var f = document.querySelector('form');
                                                                            if (f) f.submit();
                                                                        }
                                                                    })();
                                                                """.trimIndent()
                                                                view?.evaluateJavascript(jsSubmit, null)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                viewModel.onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (title != null && view?.url != null) {
                                    viewModel.onPageFinished(view.url ?: "", title, view.canGoBack())
                                }
                            }
                        }

                        PortalWebViewHolder.registerWebView(this)
                        loadUrl(uiState.currentUrl)
                    }
                },
                update = { webView ->
                    PortalWebViewHolder.registerWebView(webView)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("portal_webview")
            )

            // 2. Top Overlay: Toolbar Toggle Handle (Pill with Chevron) + Collapsible Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Toggle Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = colors.surfaceSheet.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .testTag("portal_toolbar_toggle")
                            .clickable { isToolbarExpanded = !isToolbarExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isToolbarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Toolbar",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isToolbarExpanded) "Hide Menu" else "Portal Menu",
                                style = typography.meta,
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Collapsible Portal Top Toolbar
                AnimatedVisibility(
                    visible = isToolbarExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val webView = PortalWebViewHolder.getWebView()
                                            if (webView?.canGoBack() == true) {
                                                webView.goBack()
                                            } else {
                                                onNavigateBack()
                                            }
                                        },
                                        modifier = Modifier.testTag("portal_back_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }

                                    IconButton(
                                        onClick = {
                                            val webView = PortalWebViewHolder.getWebView()
                                            webView?.loadUrl(uiState.currentUrl)
                                        },
                                        modifier = Modifier.testTag("portal_refresh_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                    }

                                    IconButton(
                                        onClick = {
                                            val webView = PortalWebViewHolder.getWebView()
                                            webView?.loadUrl("https://portal.aiub.edu/")
                                        },
                                        modifier = Modifier.testTag("portal_home_button")
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = "Portal Home")
                                    }
                                }

                                // Active Course Folder Chip in Toolbar when in download mode
                                if (activeCourseFolder != null) {
                                    Surface(
                                        color = colors.accentCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = colors.accentCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = activeCourseFolder!!.second.take(16) + if (activeCourseFolder!!.second.length > 16) "..." else "",
                                                style = typography.meta,
                                                color = colors.accentCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Progress Bar
                            if (uiState.isLoading) {
                                LinearProgressIndicator(
                                    progress = { uiState.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = colors.accentCyan
                                )
                            }

                            // Session Status & Domain Information Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceField)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "SSL Secure",
                                        tint = colors.statusSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = uiState.currentUrl.removePrefix("https://").take(32) + if (uiState.currentUrl.length > 32) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                Surface(
                                    color = if (uiState.isSessionActive) colors.statusSuccess.copy(alpha = 0.15f) else colors.statusDanger.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (uiState.isSessionActive) "Active Session" else "Not Logged In",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isSessionActive) colors.statusSuccess else colors.statusDanger,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Download Course Folder Selection Bottom Sheet
    if (showFolderPickerSheet && pendingDownload != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showFolderPickerSheet = false
                pendingDownload = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceSheet
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.downloads_select_course_folder_title),
                    style = typography.sectionTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "Select which course folder this file belongs to:",
                    style = typography.meta,
                    color = colors.textSecondary
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(distinctCourses, key = { CourseSettingEntity.makeKey(it.courseName, it.section) }) { course ->
                        val key = CourseSettingEntity.makeKey(course.courseName, course.section)
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val folderPair = Pair(course.courseCode.ifBlank { "COURSE" }, course.courseName)
                                    activeCourseKey = key
                                    activeCourseFolder = folderPair

                                    val params = pendingDownload!!
                                    CourseDownloadManager.enqueueDownload(
                                        context = context,
                                        url = params.url,
                                        userAgent = params.userAgent,
                                        contentDisposition = params.contentDisposition,
                                        mimeType = params.mimeType,
                                        courseKey = key,
                                        courseCode = folderPair.first,
                                        courseName = folderPair.second
                                    )
                                    showFolderPickerSheet = false
                                    pendingDownload = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = colors.accentCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(course.courseName, style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("${course.courseCode} - Sec ${course.section}", style = typography.meta, color = colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val folderPair = Pair("GENERAL", "Other")
                                    activeCourseKey = "OTHER"
                                    activeCourseFolder = folderPair

                                    val params = pendingDownload!!
                                    CourseDownloadManager.enqueueDownload(
                                        context = context,
                                        url = params.url,
                                        userAgent = params.userAgent,
                                        contentDisposition = params.contentDisposition,
                                        mimeType = params.mimeType,
                                        courseKey = "OTHER",
                                        courseCode = "GENERAL",
                                        courseName = "Other"
                                    )
                                    showFolderPickerSheet = false
                                    pendingDownload = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = colors.sand, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.downloads_select_course_folder_other), style = typography.body, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(
    webView: WebView,
    context: Context
) {
    val settings: WebSettings = webView.settings

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    settings.allowFileAccess = false
    settings.allowContentAccess = false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }

    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)
}
