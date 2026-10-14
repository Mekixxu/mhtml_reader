package core.reader.web

import android.view.View
import android.webkit.WebView
import core.database.entity.enums.FileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * WebView 滚动进度节流监控。采样需在恢复历史位置后显式启用。
 */
class WebViewProgressTracker(
    private val webView: WebView,
    private val sourcePathRaw: String,
    private val title: String,
    private val fileType: FileType,
    private val coroutineScope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val onSnapshot: (WebProgressSnapshot) -> Unit,
    private val readCurrentSnapshot: (() -> WebProgressSnapshot?)? = null
) {
    private var pendingWriteJob: Job? = null
    private var listenerAttached = false
    private var latestSnapshot: WebProgressSnapshot? = null
    private var baselineScrollY: Int? = null
    private val samplingEnabled = AtomicBoolean(false)
    private val debounceGeneration = AtomicLong()

    @Suppress("DEPRECATION")
    private val scrollListener = View.OnScrollChangeListener { _, _, scrollY, _, _ ->
        onScrollChanged(
            scrollY = scrollY,
            contentHeightPx = webView.contentHeight * webView.scale,
            viewportHeightPx = webView.height.toFloat()
        )
    }

    fun startTracking() {
        if (listenerAttached) return
        webView.setOnScrollChangeListener(scrollListener)
        listenerAttached = true
        if (samplingEnabled.get()) baselineScrollY = webView.scrollY
    }

    fun enableSampling() {
        if (samplingEnabled.get()) return
        latestSnapshot = null
        baselineScrollY = webView.scrollY
        samplingEnabled.set(true)
    }

    fun pauseForRestore() {
        samplingEnabled.set(false)
        invalidatePendingWrite()
        latestSnapshot = null
        baselineScrollY = null
    }

    fun stopAndFlush(): WebProgressSnapshot? {
        val shouldSample = samplingEnabled.getAndSet(false)
        if (!listenerAttached && pendingWriteJob == null) {
            latestSnapshot = null
            baselineScrollY = null
            return null
        }
        invalidatePendingWrite()
        if (listenerAttached) {
            webView.setOnScrollChangeListener(null)
            listenerAttached = false
        }
        val snapshot = if (shouldSample) currentSnapshot() ?: latestSnapshot else null
        latestSnapshot = null
        baselineScrollY = null
        snapshot?.let(onSnapshot)
        return snapshot
    }

    fun onScrollChanged(scrollY: Int, contentHeightPx: Float, viewportHeightPx: Float) {
        if (!listenerAttached || !samplingEnabled.get()) return
        if (baselineScrollY == scrollY) return
        baselineScrollY = null
        val snapshot = progressSnapshot(scrollY, contentHeightPx, viewportHeightPx) ?: return
        latestSnapshot = snapshot
        scheduleWrite(snapshot)
    }

    fun stopTracking() {
        stopAndFlush()
    }

    @Suppress("DEPRECATION")
    private fun currentSnapshot(): WebProgressSnapshot? {
        val snapshotReader = readCurrentSnapshot
        if (snapshotReader != null) return snapshotReader()
        return progressSnapshot(
            scrollY = webView.scrollY,
            contentHeightPx = webView.contentHeight * webView.scale,
            viewportHeightPx = webView.height.toFloat()
        )
    }

    private fun scheduleWrite(snapshot: WebProgressSnapshot) {
        pendingWriteJob?.cancel()
        val generation = debounceGeneration.incrementAndGet()
        val generationState = debounceGeneration
        val samplingState = samplingEnabled
        val save = onSnapshot
        pendingWriteJob = coroutineScope.launch {
            delay(debounceMs)
            if (samplingState.get() && generationState.get() == generation) {
                save(snapshot)
            }
        }
    }

    private fun invalidatePendingWrite() {
        debounceGeneration.incrementAndGet()
        pendingWriteJob?.cancel()
        pendingWriteJob = null
    }

    private fun progressSnapshot(
        scrollY: Int,
        contentHeightPx: Float,
        viewportHeightPx: Float
    ): WebProgressSnapshot? {
        if (viewportHeightPx <= 0f || contentHeightPx <= 0f) return null
        val denominator = max(0f, contentHeightPx - viewportHeightPx)
        val progress = if (denominator == 0f) {
            0f
        } else {
            (scrollY / denominator).coerceIn(0f, 1f)
        }
        return WebProgressSnapshot(
            sourcePathRaw = sourcePathRaw,
            title = title,
            fileType = fileType,
            progress = progress
        )
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 500L
    }
}

