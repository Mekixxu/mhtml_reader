package com.html_reader

import core.reader.web.WebProgressRestoreGuard
import core.reader.web.WebProgressRestoreGate
import core.reader.web.WebProgressRestoreRequest
import core.reader.web.WebViewMetrics
import core.reader.web.WebViewProgressRestorer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewProgressRestorerTest {

    @Test
    fun targetScrollMapsZeroMiddleAndEndProgress() {
        val metrics = metrics(contentHeightPx = 2_000f, viewportHeightPx = 500f)

        assertEquals(0, WebViewProgressRestorer.targetScrollY(0f, metrics))
        assertEquals(750, WebViewProgressRestorer.targetScrollY(0.5f, metrics))
        assertEquals(1_500, WebViewProgressRestorer.targetScrollY(1f, metrics))
    }

    @Test
    fun shortPageRestoresToTopAndInvalidViewportIsRejected() {
        val shortPage = metrics(contentHeightPx = 300f, viewportHeightPx = 500f)

        assertEquals(0, WebViewProgressRestorer.targetScrollY(0.8f, shortPage))
        assertNull(
            WebViewProgressRestorer.targetScrollY(
                0.5f,
                metrics(contentHeightPx = 2_000f, viewportHeightPx = 0f)
            )
        )
    }

    @Test
    fun targetScrollUsesCurrentWebViewScale() {
        val scaled = metrics(contentHeightPx = 1_000f, viewportHeightPx = 400f).copy(scale = 2f)

        assertEquals(800, WebViewProgressRestorer.targetScrollY(0.5f, scaled))
    }

    @Test
    fun waitsForLateContentHeightChangeBeforeChoosingRestoreRange() = runTest {
        val heights = ArrayDeque(listOf(1_000f, 1_000f, 2_400f, 2_400f, 2_400f))

        val stable = WebViewProgressRestorer.awaitStableMetrics(
            readMetrics = { metrics(contentHeightPx = heights.removeFirst(), viewportHeightPx = 400f) },
            shouldContinue = { true },
            stableSamplesRequired = 3,
            maxAttempts = 8,
            retryDelayMs = 1L
        )

        assertNotNull(stable)
        assertEquals(2_400f, stable!!.contentHeightPx)
        assertEquals(1_000, WebViewProgressRestorer.targetScrollY(0.5f, stable))
    }

    @Test
    fun restoreWaitStopsWhenRequestIsSuperseded() = runTest {
        var valid = true
        var reads = 0
        val result = WebViewProgressRestorer.awaitStableMetrics(
            readMetrics = {
                reads++
                metrics(contentHeightPx = 2_000f, viewportHeightPx = 500f)
            },
            shouldContinue = { valid.also { valid = false } },
            stableSamplesRequired = 4,
            maxAttempts = 8,
            retryDelayMs = 1L
        )

        assertNull(result)
        assertEquals(1, reads)
    }

    @Test
    fun generationGuardRejectsStaleARequestAfterAToBToA() {
        val oldA = WebProgressRestoreRequest("tab-a", generation = 1L, targetUrl = "file:///cache/a")
        val newA = oldA.copy(generation = 3L)

        assertFalse(
            WebProgressRestoreGuard.isCurrent(
                oldA,
                selectedTabId = "tab-a",
                generation = 3L,
                currentTargetUrl = "file:///cache/a",
                loadedUrl = "file:///cache/a",
                viewAlive = true
            )
        )
        assertTrue(
            WebProgressRestoreGuard.isCurrent(
                newA,
                selectedTabId = "tab-a",
                generation = 3L,
                currentTargetUrl = "file:///cache/a",
                loadedUrl = "file:///cache/a",
                viewAlive = true
            )
        )
    }

    @Test
    fun repeatedPageFinishedClaimsRestoreOnlyOncePerGeneration() {
        val gate = WebProgressRestoreGate()
        val request = WebProgressRestoreRequest("tab-a", generation = 4L, targetUrl = "file:///cache/a")

        assertTrue(gate.tryClaim(request, "tab-a", 4L, request.targetUrl, request.targetUrl, request.targetUrl, true))
        assertFalse(gate.tryClaim(request, "tab-a", 4L, request.targetUrl, request.targetUrl, request.targetUrl, true))

        val nextLoad = request.copy(generation = 5L)
        assertTrue(gate.tryClaim(nextLoad, "tab-a", 5L, request.targetUrl, request.targetUrl, request.targetUrl, true))
    }

    private fun metrics(contentHeightPx: Float, viewportHeightPx: Float) = WebViewMetrics(
        contentHeightPx = contentHeightPx,
        viewportHeightPx = viewportHeightPx,
        scale = 1f,
        scrollY = 0
    )
}
