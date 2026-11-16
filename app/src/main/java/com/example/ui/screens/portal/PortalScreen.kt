package com.example.ui.screens.portal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.auth.PortalAuth
import com.example.data.download.CourseDownloadManager
import com.example.data.local.entity.CourseSettingEntity
import com.example.data.parser.PageState
import com.example.data.parser.PortalSelectorConfig
import com.example.ui.components.GlassCard
import com.example.ui.theme.UniTrackTheme
import com.example.util.UniTrackLogger
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
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
        try {
            val currentUrl = viewModel.uiState.value.currentUrl
            if (currentUrl.startsWith("https://portal.aiub.edu", ignoreCase = true)) {
                viewModel.onCredentialsCaptured(id, pass)
            }
        } catch (_: Throwable) {
            // Safe bridge guard
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
    onNavigateBack: () -> Unit,
    selectedCourseKey: String? = null,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.UniTrackApplication
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val uiState by viewModel.uiState.collectAsState()
    val autoSignInState by viewModel.autoSignInState.collectAsState()
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
    var showSignInSheet by remember { mutableStateOf(false) }
    var userChoseManual by remember { mutableStateOf(false) }
    var prefilledStudentId by remember { mutableStateOf(uiState.studentId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PortalEvent.SignedIn -> {
                    val cookieManager = CookieManager.getInstance()
                    val webView = PortalWebViewHolder.getWebView()
                    for (cookieStr in PortalAuth.cookiesForWebView()) {
                        cookieManager.setCookie("https://portal.aiub.edu", "$cookieStr; Path=/")
                    }
                    if (webView != null) {
                        cookieManager.setAcceptThirdPartyCookies(webView, true)
                    }
                    cookieManager.flush()
                    webView?.loadUrl(event.landingUrl)
                    showSignInSheet = false
                }
                is PortalEvent.WrongCredentials -> {
                    prefilledStudentId = event.studentId
                    showSignInSheet = true
                }
                is PortalEvent.ManualRequired -> {
                    val creds = CredentialStore(context).load()
                    if (creds != null) {
                        val webView = PortalWebViewHolder.getWebView()
                        val idJson = JSONObject.quote(creds.first)
                        val passJson = JSONObject.quote(creds.second)
                        webView?.evaluateJavascript(
                            """
                            (function() {
                                var u = document.querySelector('input[name="UserName"]');
                                var p = document.querySelector('input[name="Password"]');
                                if (u) { u.value = $idJson; u.dispatchEvent(new Event('input', {bubbles:true})); u.dispatchEvent(new Event('change', {bubbles:true})); }
                                if (p) { p.value = $passJson; p.dispatchEvent(new Event('input', {bubbles:true})); p.dispatchEvent(new Event('change', {bubbles:true})); }
                                var c = document.querySelector('#CaptchaInputText');
                                if (c) c.focus();
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    val snackResult = snackbarHostState.showSnackbar(
                        message = "Couldn't read the captcha automatically. Type it yourself or tap Retry.",
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Long
                    )
                    if (snackResult == SnackbarResult.ActionPerformed) {
                        viewModel.startAutoSignIn(context, isRetry = true)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
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

    // Session expired AlertDialog (must NOT show while auto sign-in is Running)
    if (uiState.isSessionExpiredPrompt && autoSignInState !is AutoSignInState.Running) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSessionExpiredPrompt() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = stringResource(R.string.portal_session_expired)) },
            text = { Text("Your portal session has expired or redirected to login. Please log in again to sync your routine.") },
            confirmButton = {
                Button(onClick = { viewModel.dismissSessionExpiredPrompt() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize().testTag("portal_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // WebView filling screen
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
                                url?.let { viewModel.onPageStarted(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val currentUrlStr = url ?: view?.url ?: ""
                                viewModel.onPageFinished(currentUrlStr, view?.title, view?.canGoBack() ?: false)

                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.evaluateJavascript(
                                        "(function(){ if (document.querySelector(\"a[href*='/Login/Logout']\")) return 'app'; if (document.querySelector('input[name=\"UserName\"]') && document.querySelector('input[name=\"Password\"]')) return 'login'; return 'other'; })()"
                                    ) { rawJson ->
                                        val clean = rawJson?.replace("\"", "")?.trim() ?: "other"
                                        val state = PortalSelectorConfig.classifyPage(clean, currentUrlStr)

                                        val credStore = CredentialStore(context)
                                        val hasSavedCreds = credStore.hasCredentials()

                                        viewModel.handlePageState(state, currentUrlStr, hasSavedCreds, showSignInSheet)

                                        when (state) {
                                            PageState.APP -> {
                                                showSignInSheet = false
                                            }
                                            PageState.LOGIN -> {
                                                if (viewModel.canStartAutoAttempt(hasSavedCreds, isRetry = false, isSessionExpiredTransition = uiState.isSessionActive, userChoseManual = userChoseManual)) {
                                                    viewModel.startAutoSignIn(context, isSessionExpiredTransition = uiState.isSessionActive)
                                                } else if (!hasSavedCreds && !userChoseManual) {
                                                    showSignInSheet = true
                                                }

                                                // Inject 1.3 capture listener
                                                view.evaluateJavascript(
                                                    """
                                                    (function() {
                                                        if (location.origin !== 'https://portal.aiub.edu') return;
                                                        if (window.uniTrackCaptured) return;
                                                        var u = document.querySelector('input[name="UserName"]');
                                                        var p = document.querySelector('input[name="Password"]');
                                                        if (!u || !p) return;
                                                        var f = u.closest('form');
                                                        if (!f) return;
                                                        window.uniTrackCaptured = true;
                                                        
                                                        function sendCreds() {
                                                            var id = u ? u.value : '';
                                                            var pass = p ? p.value : '';
                                                            if (id && pass && window.UniTrackBridge) {
                                                                window.UniTrackBridge.onLoginSubmit(id, pass);
                                                            }
                                                        }
                                                        f.addEventListener('submit', sendCreds, true);
                                                        var btn = f.querySelector('button[type="submit"], input[type="submit"]');
                                                        if (btn) btn.addEventListener('click', sendCreds, true);
                                                    })();
                                                    """.trimIndent(),
                                                    null
                                                )
                                            }
                                            PageState.OTHER -> {}
                                        }
                                    }
                                }, 300)
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

            // Auto sign-in overlay while Running (ONLY when sheet is not visible)
            if (autoSignInState is AutoSignInState.Running && !showSignInSheet) {
                val running = autoSignInState as AutoSignInState.Running
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(modifier = Modifier.padding(24.dp)) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = colors.accentCyan)
                            Text(
                                text = "Signing in securely...",
                                style = typography.body,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Reading captcha... (${running.attempt}/${running.max})",
                                style = typography.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.cancelAutoSignIn() },
                                modifier = Modifier.testTag("portal_signin_cancel")
                            ) {
                                Text("Cancel", color = colors.statusDanger)
                            }
                        }
                    }
                }
            }
        }
    }

    // First-time Sign In Bottom Sheet
    if (showSignInSheet) {
        val isRunning = autoSignInState is AutoSignInState.Running
        val progressText = if (autoSignInState is AutoSignInState.Running) {
            val r = autoSignInState as AutoSignInState.Running
            "Reading captcha... (${r.attempt}/${r.max})"
        } else null
        val signInError by viewModel.signInError.collectAsState()

        SignInSheet(
            isRunning = isRunning,
            progressText = progressText,
            errorText = signInError,
            prefilledStudentId = prefilledStudentId,
            onSignIn = { id, pass ->
                viewModel.startAutoSignIn(context, studentId = id, password = pass, isRetry = true)
            },
            onCancel = { viewModel.cancelAutoSignIn() },
            onUseWebsiteLogin = {
                userChoseManual = true
                showSignInSheet = false
            },
            onDismiss = { showSignInSheet = false }
        )
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
    settings.userAgentString = PortalAuth.DESKTOP_USER_AGENT

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
