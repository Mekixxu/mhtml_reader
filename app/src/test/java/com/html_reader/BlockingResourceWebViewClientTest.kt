package com.html_reader

import android.net.Uri
import android.webkit.WebResourceRequest
import core.database.entity.enums.FileType
import core.reader.web.BlockingResourceWebViewClient
import core.reader.web.NewTabLinkHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockingResourceWebViewClientTest {

    private val cacheRoot = "/data/user/0/com.html_reader/cache/app_cache"

    private fun request(
        url: String,
        gesture: Boolean = false,
        mainFrame: Boolean = true
    ) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = gesture
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private fun client(
        root: String? = cacheRoot,
        opened: MutableList<String> = mutableListOf()
    ) = BlockingResourceWebViewClient(
        fileType = FileType.MHTML,
        allowedCacheRootPath = root,
        onOpenLinkInNewTab = NewTabLinkHandler { opened += it }
    ) to opened

    @Test
    fun httpWithGesture_opensExternallyAndBlocksInWebView() {
        val (client, opened) = client()
        val blocked = client.shouldOverrideUrlLoading(null, request("https://example.com", gesture = true))
        assertTrue(blocked)
        assertEquals(listOf("https://example.com"), opened)
    }

    @Test
    fun httpWithoutGesture_blocksAndDoesNotOpenExternally() {
        val (client, opened) = client()
        val blocked = client.shouldOverrideUrlLoading(null, request("http://example.com/redirect", gesture = false))
        assertTrue(blocked)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun unknownScheme_isBlocked() {
        val (client, _) = client()
        assertTrue(client.shouldOverrideUrlLoading(null, request("intent://scan/#Intent;scheme=zxing;end")))
        assertTrue(client.shouldOverrideUrlLoading(null, request("market://details?id=x")))
    }

    @Test
    fun fileInsideCacheRoot_isAllowed() {
        val (client, _) = client()
        assertEquals(
            false,
            client.shouldOverrideUrlLoading(null, request("file://$cacheRoot/mhtml/key/content.mhtml"))
        )
    }

    @Test
    fun fileOutsideCacheRoot_isBlocked() {
        val (client, _) = client()
        assertTrue(client.shouldOverrideUrlLoading(null, request("file:///etc/hosts")))
        assertTrue(client.shouldOverrideUrlLoading(null, request("file:///data/user/0/com.html_reader/files/secret.mhtml")))
    }

    @Test
    fun fileWithNullRoot_isBlocked() {
        val (client, _) = client(root = null)
        assertTrue(client.shouldOverrideUrlLoading(null, request("file://$cacheRoot/mhtml/key/content.mhtml")))
    }

    @Test
    fun fileTraversal_isBlocked() {
        val (client, _) = client()
        assertTrue(
            client.shouldOverrideUrlLoading(
                null,
                request("file://$cacheRoot/../../databases/history.db")
            )
        )
    }

    @Test
    fun encodedTraversal_isBlocked() {
        val (client, _) = client()
        assertTrue(
            client.shouldOverrideUrlLoading(
                null,
                request("file://$cacheRoot/%2e%2e/%2e%2e/databases/history.db")
            )
        )
    }

    @Test
    fun siblingDirectoryPrefix_isBlocked() {
        val (client, _) = client()
        assertTrue(
            client.shouldOverrideUrlLoading(
                null,
                request("file://${cacheRoot}X/evil.mhtml")
            )
        )
    }

    @Test
    fun interceptRequest_blocksRemoteResources() {
        val (client, _) = client()
        val response = client.shouldInterceptRequest(null, request("https://example.com/a.png"))
        assertNotNull(response)
        assertEquals("text/plain", response?.mimeType)
    }

    @Test
    fun interceptRequest_allowsCacheLocalResources() {
        val (client, _) = client()
        assertNull(client.shouldInterceptRequest(null, request("file://$cacheRoot/mhtml/key/img.png")))
        assertNull(client.shouldInterceptRequest(null, request("data:image/png;base64,AAAA")))
    }
}
