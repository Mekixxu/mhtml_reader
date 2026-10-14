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
        claimedGeneration = request.generation
        return true
    }
}

object WebViewProgressRestorer {
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
}
