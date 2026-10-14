package com.html_reader

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import core.database.entity.enums.FileType
import core.reader.web.WebViewProgressTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewProgressTrackerTest {

    @Test
    fun stopWithinDebounceFlushesLatestSnapshotAndCancelsDelayedWrite() = runTest {
        val writes = mutableListOf<Float>()
        val tracker = tracker(this, writes)
        tracker.startTracking()
        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 250, contentHeightPx = 1_200f, viewportHeightPx = 200f)

        advanceTimeBy(499)
        assertEquals(emptyList<Float>(), writes)

        tracker.stopAndFlush()
        runCurrent()

        assertEquals(listOf(0.25f), writes)
        tracker.startTracking()
        advanceTimeBy(1_000)
        assertEquals(listOf(0.25f), writes)
        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 500, contentHeightPx = 1_200f, viewportHeightPx = 200f)
        tracker.stopAndFlush()
        assertEquals(listOf(0.25f, 0.5f), writes)
    }

    @Test
    fun debounceWritesOnlyMostRecentScrollSnapshot() = runTest {
        val writes = mutableListOf<Float>()
        val tracker = tracker(this, writes)
        tracker.startTracking()
        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 100, contentHeightPx = 1_000f, viewportHeightPx = 200f)
        advanceTimeBy(300)
        tracker.onScrollChanged(scrollY = 400, contentHeightPx = 1_000f, viewportHeightPx = 200f)
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf(0.5f), writes)
        tracker.stopAndFlush()
    }

    @Test
    fun samplingWaitsForActivationAndRejectsInvalidViewport() = runTest {
        val writes = mutableListOf<Float>()
        val tracker = tracker(this, writes)
        tracker.startTracking()
        tracker.onScrollChanged(scrollY = 0, contentHeightPx = 2_000f, viewportHeightPx = 500f)
        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 100, contentHeightPx = 2_000f, viewportHeightPx = 0f)
        runCurrent()

        assertNull(tracker.stopAndFlush())
        assertEquals(emptyList<Float>(), writes)
    }

    @Test
    fun samplingResumesOnlyAfterExplicitActivation() = runTest {
        val writes = mutableListOf<Float>()
        val tracker = tracker(this, writes)
        tracker.startTracking()
        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 250, contentHeightPx = 1_200f, viewportHeightPx = 200f)
        tracker.stopAndFlush()

        tracker.startTracking()
        tracker.onScrollChanged(scrollY = 500, contentHeightPx = 1_200f, viewportHeightPx = 200f)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(0.25f), writes)

        tracker.enableSampling()
        tracker.onScrollChanged(scrollY = 500, contentHeightPx = 1_200f, viewportHeightPx = 200f)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(0.25f, 0.5f), writes)
    }

    private fun tracker(
        scope: kotlinx.coroutines.CoroutineScope,
        writes: MutableList<Float>
    ): WebViewProgressTracker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return WebViewProgressTracker(
            webView = WebView(context),
            sourcePathRaw = "/docs/read.mhtml",
            title = "read.mhtml",
            fileType = FileType.MHTML,
            coroutineScope = scope,
            debounceMs = 500L,
            onSnapshot = { writes += it.progress },
            readCurrentSnapshot = { null }
        )
    }
}
