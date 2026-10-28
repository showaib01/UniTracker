package com.example.data.sync

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.parser.PortalSelectorConfig
import com.example.util.CapturedPageHolder
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class HeadlessPageResult(
    val url: String,
    val html: String,
    val isLoginDetected: Boolean,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object HeadlessWebViewSyncHelper {

    private const val DEFAULT_TIMEOUT_MS = 30_000L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun loadPageAndCaptureHtml(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): HeadlessPageResult = withContext(Dispatchers.Main) {
        UniTrackLogger.d("HeadlessWebViewSync: Initiating load for '$targetUrl' (timeout=${timeoutMs}ms)")

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<HeadlessPageResult> { continuation ->
                var webView: WebView? = null
                val mainHandler = Handler(Looper.getMainLooper())
                var isCompleted = false

                fun finishWith(res: HeadlessPageResult) {
                    if (isCompleted) return
                    isCompleted = true
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (e: Exception) {
                        UniTrackLogger.w("Error destroying headless WebView: ${e.message}")
                    }
                    if (continuation.isActive) {
                        continuation.resume(res)
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {
                            // Ignore cleanup exceptions
                        }
                    }
                }

                try {
                    webView = WebView(context.applicationContext).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            allowFileAccess = false
                            allowContentAccess = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                UniTrackLogger.d("HeadlessWebView onPageStarted: $url")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val currentUrl = url ?: targetUrl
                                UniTrackLogger.d("HeadlessWebView onPageFinished: $currentUrl, waiting 1500ms for JS render...")

                                // Short delay to allow client-side AJAX / DOM to settle
                                mainHandler.postDelayed({
                                    if (isCompleted) return@postDelayed

                                    view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { rawJson ->
                                        UniTrackLogger.d("HeadlessWebView evaluateJavascript received: length=${rawJson?.length}")
                                        if (rawJson == null || rawJson.trim() == "null") {
                                            finishWith(
                                                HeadlessPageResult(
                                                    url = currentUrl,
                                                    html = "",
                                                    isLoginDetected = false,
                                                    isSuccess = false,
                                                    errorMessage = "evaluateJavascript returned null"
                                                )
                                            )
                                            return@evaluateJavascript
                                        }

                                        val decodedHtml = CapturedPageHolder.decodeJsHtml(rawJson)
                                        val isLogin = PortalSelectorConfig.isLoginPage(currentUrl, decodedHtml)
                                        UniTrackLogger.d("HeadlessWebView: Captured ${decodedHtml.length} chars. isLogin=$isLogin")

                                        finishWith(
                                            HeadlessPageResult(
                                                url = currentUrl,
                                                html = decodedHtml,
                                                isLoginDetected = isLogin,
                                                isSuccess = true
                                            )
                                        )
                                    }
                                }, 1500)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                UniTrackLogger.e("HeadlessWebView onReceivedError: code=$errorCode, desc=$description, url=$failingUrl")
                            }
                        }

                        loadUrl(targetUrl)
                    }
                } catch (e: Exception) {
                    UniTrackLogger.e("HeadlessWebView failed to initialize: ${e.message}", e)
                    finishWith(
                        HeadlessPageResult(
                            url = targetUrl,
                            html = "",
                            isLoginDetected = false,
                            isSuccess = false,
                            errorMessage = e.message
                        )
                    )
                }
            }
        }

        result ?: HeadlessPageResult(
            url = targetUrl,
            html = "",
            isLoginDetected = false,
            isSuccess = false,
            errorMessage = "Timed out after ${timeoutMs / 1000}s"
        )
    }
}
