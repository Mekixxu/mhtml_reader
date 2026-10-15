package com.html_reader

import android.content.Context
import android.webkit.WebView
import android.view.View
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.common.DispatcherProvider
import core.data.repo.HistoryRepository
import core.database.AppDatabase
import core.database.entity.enums.FileType
import core.reader.model.ReaderTab
import core.reader.web.WebProgressRestoreRequest
import core.reader.web.WebProgressSamplingState
import core.reader.web.WebViewProgressTracker
import core.reader.web.WebViewProgressWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowEnvironmentPermissions::class])
@OptIn(ExperimentalCoroutinesApi::class)
class WebProgressRestoreIntegrationTest {
    @Test
    fun fragmentTimeoutThenLateLayoutAndPausePreserveDatabaseProgress() = runTest {
        exerciseRestore(lateDuringRetry = false)
    }

    @Test
    fun fragmentRetryRestoresAndTailWritesOriginalDatabaseProgress() = runTest {
        exerciseRestore(lateDuringRetry = true)
    }

    private suspend fun TestScope.exerciseRestore(lateDuringRetry: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        try {
            val repository = repository(db)
            repository.saveWebProgress(PATH, "test", FileType.MHTML, 0.6f)
            val fragment = ReaderFragment()
            activity.get().supportFragmentManager.beginTransaction().add(fragment, "restore-test").commitNow()
            assertNotNull(fragment.view)
            val web = createWebView(context, lateDuringRetry)
            val writer = WebViewProgressWriter(this, repository)
            val tracker = WebViewProgressTracker(web, PATH, "test", FileType.MHTML, this,
                onSnapshot = { writer.enqueue(it) })
            configure(fragment, web, repository, writer, tracker)
            invokeRestore(fragment)
            // Successful scroll also triggers one tracker metrics read before activation.
            assertEquals(if (lateDuringRetry) 30 else 72, web.reads)
            web.ready = true
            activity.pause().resume()
            val snapshot = tracker.stopAndFlush()
            if (lateDuringRetry) assertNotNull(snapshot) else assertNull(snapshot)
            writer.awaitWrites()
            assertEquals(0.6f, db.historyDao().getByPath(PATH)!!.progress, 0.001f)
        } finally {
            activity.pause().stop().destroy()
            db.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun createWebView(context: Context, lateDuringRetry: Boolean): MetricsWebView {
        val web = MetricsWebView(context, lateDuringRetry)
        web.loadUrl(URL)
        web.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        web.layout(0, 0, 500, 500)
        // Robolectric's WebView layout is a no-op; set viewport bounds explicitly.
        web.bottom = 500
        web.right = 500
        assertEquals(500, web.height)
        assertEquals(1f, web.scale, 0f)
        return web
    }

    private fun TestScope.repository(db: AppDatabase): HistoryRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
        }
        return HistoryRepository(db.historyDao(), provider)
    }

    private fun configure(
        fragment: ReaderFragment, web: WebView, repository: HistoryRepository,
        writer: WebViewProgressWriter, tracker: WebViewProgressTracker
    ) {
        setField(fragment, "webPreview", web)
        setField(fragment, "historyRepository", repository)
        setField(fragment, "webProgressWriter", writer)
        setField(fragment, "webProgressTracker", tracker)
        setField(fragment, "selectedTabId", "tab")
        setField(fragment, "activeWebTargetUrl", URL)
        setField(fragment, "webContentGeneration", 1L)
        val state = field(fragment, "webProgressSamplingState") as WebProgressSamplingState
        state.beginContent(1L)
        state.onResume(1L)
        tracker.startTracking()
    }

    private suspend fun invokeRestore(fragment: ReaderFragment): Unit = suspendCoroutine { continuation ->
        val method = ReaderFragment::class.java.getDeclaredMethod(
            "restoreWebProgress", ReaderTab::class.java, WebProgressRestoreRequest::class.java,
            String::class.java, Continuation::class.java
        ).apply { isAccessible = true }
        val tab = ReaderTab("tab", PATH, null, FileType.MHTML, null, null, "test", 0f, -1)
        val result = method.invoke(fragment, tab, WebProgressRestoreRequest("tab", 1L, URL), URL, continuation)
        if (result !== COROUTINE_SUSPENDED) continuation.resume(Unit)
    }

    private fun field(fragment: ReaderFragment, name: String): Any? =
        ReaderFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun setField(fragment: ReaderFragment, name: String, value: Any) {
        ReaderFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
    }

    private class MetricsWebView(context: Context, private val lateDuringRetry: Boolean) : WebView(context) {
        var ready = false
        var reads = 0
            private set
        override fun getUrl(): String = URL
        override fun getContentHeight(): Int {
            reads++
            return if (ready || (lateDuringRetry && reads > 24)) 2_000 else 0
        }

        @Suppress("DEPRECATION")
        override fun getScale(): Float = 1f
    }

    private companion object {
        const val PATH = "/docs/restore.mhtml"
        const val URL = "file:///cache/restore.mhtml"
    }
}
