package core.reader.web

/**
 * WebView点击链接后，新tab打开的回调接口。
 */
fun interface NewTabLinkHandler {
    fun onOpenLink(url: String)
}
