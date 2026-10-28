package com.example.ui.screens.portal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.UniTrackTheme
import com.example.util.UniTrackLogger
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToCaptures: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = UniTrackTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var showStudentIdDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
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

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("portal_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        // Portal Top Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
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

                    // Action buttons: Capture Page and Debug
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                UniTrackLogger.d("Capture button clicked in PortalScreen UI")
                                viewModel.captureCurrentPage()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentCyan,
                                contentColor = colors.background
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("capture_page_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capture",
                                tint = colors.background,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Capture",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.background,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onNavigateToDebug,
                            modifier = Modifier.testTag("open_parser_debug_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Parser Debug",
                                tint = colors.accentCyan
                            )
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

                // Session Status and Domain Information Bar
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

        // Capture Success / Diagnostic Feedback Banner
        if (uiState.captureMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.captureMessage?.contains("error", ignoreCase = true) == true)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (uiState.captureMessage?.contains("error", ignoreCase = true) == true)
                                Icons.Default.Warning
                            else Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.captureMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row {
                        TextButton(onClick = onNavigateToDebug) {
                            Text("Inspect", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(
                            onClick = { viewModel.clearCaptureMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Embedded WebView
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        configureWebView(this, context)

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
                                url?.let {
                                    viewModel.onPageFinished(it, view?.title, view?.canGoBack() ?: false)
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

                        // Register with singleton holder so it is immediately accessible
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
