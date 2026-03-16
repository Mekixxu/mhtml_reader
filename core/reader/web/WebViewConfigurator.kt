package core.reader.web

import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView 初始化配置。统一设置缓存JS缩放等。
 */
object WebViewConfigurator {
    fun configure(webView: WebView, darkMode: Boolean = false) {
        val settings = webView.settings
        settings.javaScriptEnabled = false // 默认禁止
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // settings.cacheMode = WebSettings.LOAD_NO_CACHE // Removed as requested
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.domStorageEnabled = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (darkMode) {
            // 可预留夜间模式
        }
    }
}
