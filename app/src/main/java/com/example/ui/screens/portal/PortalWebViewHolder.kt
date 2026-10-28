package com.example.ui.screens.portal

import android.webkit.WebView
import com.example.util.UniTrackLogger
import java.lang.ref.WeakReference

object PortalWebViewHolder {
    @Volatile
    private var activeWebViewRef: WeakReference<WebView>? = null

    fun registerWebView(webView: WebView) {
        activeWebViewRef = WeakReference(webView)
        UniTrackLogger.d("PortalWebViewHolder: Registered active WebView instance (url='${webView.url}')")
    }

    fun getWebView(): WebView? {
        val wv = activeWebViewRef?.get()
        UniTrackLogger.d("PortalWebViewHolder: getWebView() -> exists=${wv != null}, url='${wv?.url}'")
        return wv
    }

    fun clear() {
        activeWebViewRef = null
        UniTrackLogger.d("PortalWebViewHolder: cleared active reference")
    }
}
