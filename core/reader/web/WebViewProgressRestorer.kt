package core.reader.web

import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class WebViewMetrics(
    val contentHeightPx: Float,
    val viewportHeightPx: Float,
    val scale: Float,
    val scrollY: Int
) {
    val maxScrollY: Int?
        get() {
            if (!contentHeightPx.isFinite() ||
                !viewportHeightPx.isFinite() ||
                !scale.isFinite() ||
                contentHeightPx <= 0f ||
                viewportHeightPx <= 0f ||
                scale <= 0f
            ) return null
            return (contentHeightPx * scale - viewportHeightPx).coerceAtLeast(0f).roundToInt()
        }
}

data class WebProgressRestoreRequest(
    val tabId: String,
    val generation: Long,
    val targetUrl: String
)

enum class WebProgressRestoreResult {
    APPLIED, USER_TOOK_CONTROL, DIMENSIONS_UNAVAILABLE, SUPERSEDED
}

object WebProgressRestoreGuard {
    fun isCurrent(
        request: WebProgressRestoreRequest,
        selectedTabId: String?,
        generation: Long,
        currentTargetUrl: String?,
        loadedUrl: String,
        viewAlive: Boolean
    ): Boolean =
        viewAlive &&
            request.tabId == selectedTabId &&
            request.generation == generation &&
            request.targetUrl == currentTargetUrl &&
            loadedUrl.startsWith(request.targetUrl)
}

class WebProgressRestoreGate {
    private var claimedGeneration: Long? = null
    private var attemptGeneration: Long? = null
    private var attempts = 0

    fun release(request: WebProgressRestoreRequest) {
        if (claimedGeneration == request.generation) claimedGeneration = null
    }

    fun tryClaim(
        request: WebProgressRestoreRequest,
        selectedTabId: String?,
        generation: Long,
        currentTargetUrl: String?,
        loadedUrl: String,
        webViewUrl: String?,
        viewAlive: Boolean
    ): Boolean {
        if (claimedGeneration == request.generation) return false
        if (!WebProgressRestoreGuard.isCurrent(
                request = request,
                selectedTabId = selectedTabId,
                generation = generation,
                currentTargetUrl = currentTargetUrl,
                loadedUrl = loadedUrl,
                viewAlive = viewAlive
            )
        ) return false
        if (webViewUrl?.startsWith(request.targetUrl) != true) return false
        if (attemptGeneration != request.generation) {
            attemptGeneration = request.generation
            attempts = 0
        }
        if (attempts >= MAX_CLAIMS_PER_GENERATION) return false
        attempts++
        claimedGeneration = request.generation
        return true
    }

    private companion object {
        const val MAX_CLAIMS_PER_GENERATION = 2
    }
}

object WebViewProgressRestorer {
    suspend fun restore(
        progress: Float,
        readMetrics: () -> WebViewMetrics,
        isCurrent: () -> Boolean,
        userTookControl: () -> Boolean,
        scrollTo: (Int) -> Unit,
        retryWindows: Int = DEFAULT_RETRY_WINDOWS
    ): WebProgressRestoreResult {
        repeat(retryWindows.coerceIn(1, DEFAULT_RETRY_WINDOWS)) { window ->
            if (!isCurrent()) return WebProgressRestoreResult.SUPERSEDED
            if (userTookControl()) return WebProgressRestoreResult.USER_TOOK_CONTROL
            val metrics = awaitStableMetrics(
                readMetrics = readMetrics,
                shouldContinue = { isCurrent() && !userTookControl() }
            )
            if (!isCurrent()) return WebProgressRestoreResult.SUPERSEDED
            if (userTookControl()) return WebProgressRestoreResult.USER_TOOK_CONTROL
            val target = metrics?.let { targetScrollY(progress, it) }
            if (target != null) {
                scrollTo(target)
                return WebProgressRestoreResult.APPLIED
            }
            if (window < retryWindows - 1) delay(RETRY_WINDOW_DELAY_MS)
        }
        return WebProgressRestoreResult.DIMENSIONS_UNAVAILABLE
    }

    suspend fun awaitStableMetrics(
        readMetrics: () -> WebViewMetrics,
        shouldContinue: () -> Boolean,
        stableSamplesRequired: Int = DEFAULT_STABLE_SAMPLES,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
    ): WebViewMetrics? {
        val requiredStableSamples = stableSamplesRequired.coerceAtLeast(1)
        val attempts = maxAttempts.coerceAtLeast(1)
        var lastRange: Int? = null
        var stableSamples = 0
        var lastValidMetrics: WebViewMetrics? = null

        repeat(attempts) { attempt ->
            if (!shouldContinue()) return null
            val metrics = readMetrics()
            val maxScrollY = metrics.maxScrollY
            if (maxScrollY != null) {
                lastValidMetrics = metrics
                if (maxScrollY == lastRange) {
                    stableSamples++
                } else {
                    lastRange = maxScrollY
                    stableSamples = 1
                }
                if (stableSamples >= requiredStableSamples) return metrics
            }
            if (attempt < attempts - 1) delay(retryDelayMs)
        }

        return lastValidMetrics.takeIf { shouldContinue() }
    }

    fun targetScrollY(progress: Float, metrics: WebViewMetrics): Int? {
        val maxScrollY = metrics.maxScrollY ?: return null
        val normalized = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return (maxScrollY * normalized).roundToInt()
    }

    private const val DEFAULT_STABLE_SAMPLES = 5
    private const val DEFAULT_MAX_ATTEMPTS = 24
    private const val DEFAULT_RETRY_DELAY_MS = 50L
    private const val DEFAULT_RETRY_WINDOWS = 3
    private const val RETRY_WINDOW_DELAY_MS = 250L
}
