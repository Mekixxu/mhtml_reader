package core.reader.web

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import core.database.entity.enums.FileType
import java.io.ByteArrayInputStream

class BlockingResourceWebViewClient(
    private val fileType: FileType,
    private val onOpenLinkInNewTab: NewTabLinkHandler,
    private val onPageFinishedCallback: ((url: String) -> Unit)? = null
) : WebViewClient() {
    private val tag = "BlockingWebClient"

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (fileType == FileType.HTML || fileType == FileType.MHTML) {
            val url = request?.url?.toString().orEmpty()

            val allowed =
                url.startsWith("file://") ||
                url.startsWith("content://") ||
                url.startsWith("data:") ||
                url.startsWith("about:blank") ||
                url.startsWith("cid:")

            if (!allowed) {
                Log.d(tag, "blocked_resource fileType=$fileType url=$url")
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val safeUrl = url ?: return
        onPageFinishedCallback?.invoke(safeUrl)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString().orEmpty()
        val isHttp = url.startsWith("http://") || url.startsWith("https://")
        val hasGesture = request?.hasGesture() ?: true

        if (isHttp && hasGesture) {
            onOpenLinkInNewTab.onOpenLink(url)
            return true
        }
        return false
    }
}

