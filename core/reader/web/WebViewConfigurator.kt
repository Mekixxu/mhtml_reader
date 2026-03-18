package core.reader.web

import android.webkit.WebSettings
import android.webkit.WebView

object WebViewConfigurator {
    enum class RenderProfile {
        DEFAULT,
        MHTML_DESKTOP,
        MHTML_FALLBACK
    }

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun configure(
        webView: WebView,
        darkMode: Boolean = false,
        profile: RenderProfile = RenderProfile.DEFAULT
    ) {
        val settings = webView.settings
        val defaultUserAgent = WebSettings.getDefaultUserAgent(webView.context)
        settings.javaScriptEnabled = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.domStorageEnabled = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.userAgentString = defaultUserAgent
        webView.setInitialScale(0)

        when (profile) {
            RenderProfile.DEFAULT -> Unit
            RenderProfile.MHTML_DESKTOP -> {
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = DESKTOP_USER_AGENT
            }
            RenderProfile.MHTML_FALLBACK -> {
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = defaultUserAgent
            }
        }

        if (darkMode) {
            Unit
        }
    }
}
