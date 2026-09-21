package core.reader.web

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import core.database.entity.enums.FileType
import java.io.ByteArrayInputStream

/**
 * 阅读器资源/导航拦截。
 *
 * 安全策略（fail-closed）：
 * - 子资源仅允许 data/about/cid 与缓存根目录内的 file://；
 * - 主框架导航仅允许缓存根目录内的 file://；http(s) 仅在手势触发时交给外部处理，
 *   其余（自动跳转、未知 scheme、intent: 等）一律拦截。
 */
class BlockingResourceWebViewClient(
    private val fileType: FileType,
    private val onOpenLinkInNewTab: NewTabLinkHandler,
    private val allowedCacheRootPath: String? = null,
    private val onPageFinishedCallback: ((url: String) -> Unit)? = null
) : WebViewClient() {
    private val tag = "BlockingWebClient"

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (fileType == FileType.HTML || fileType == FileType.MHTML) {
            val url = request?.url?.toString().orEmpty()

            val allowed =
                url.startsWith("data:") ||
                url.startsWith("about:blank") ||
                url.startsWith("cid:") ||
                isAllowedLocalFile(url)

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

        if (isHttp) {
            if (request?.hasGesture() == true) {
                onOpenLinkInNewTab.onOpenLink(url)
            }
            return true
        }
        // 缓存目录内的本地跳转放行，其余（intent:/market:/tel:/自动跳转等）一律拦截
        if (isAllowedLocalFile(url)) {
            return false
        }
        Log.d(tag, "blocked_navigation fileType=$fileType url=$url")
        return true
    }

    private fun isAllowedLocalFile(url: String): Boolean {
        if (!url.startsWith("file:")) return false
        val root = allowedCacheRootPath ?: return false
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return false
        return path.startsWith(root)
    }
}
