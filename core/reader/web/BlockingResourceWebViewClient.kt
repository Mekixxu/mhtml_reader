package core.reader.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import core.database.entity.enums.FileType
import java.io.ByteArrayInputStream

/**
 * 外链资源阻断（SSOT/B）：
 * - 对 HTML/MHTML（从缓存文件打开）：阻止所有网络外链资源加载
 * - 仅允许本地：file://, content://, data:, about:blank
 * - 对 http/https 页面：不拦截资源
 *
 * 链接点击：仅当 hasGesture==true 的 http/https 才新 tab 打开，避免页面自动跳转被误拦。
 */
class BlockingResourceWebViewClient(
    private val fileType: FileType,
    private val onOpenLinkInNewTab: NewTabLinkHandler
) : WebViewClient() {

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
                // 返回空响应比 null InputStream 更安全
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }
        return super.shouldInterceptRequest(view, request)
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

