package core.reader.web

import android.webkit.WebView
import core.data.repo.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * WebView 滚动进度节流监控：
 * progress = scrollY / (contentHeightPx - viewportHeightPx)
 */
class WebViewProgressTracker(
    private val webView: WebView,
    private val sourcePathRaw: String,
    private val historyRepo: HistoryRepository,
    private val coroutineScope: CoroutineScope
) {
    private var job: Job? = null
    private val debounceMs = 500L

    @Suppress("DEPRECATION")
    fun startTracking() {
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // WebView.scale 在 API 30+ 已废弃，但当前 WebView 尚无等价的
            // 内容缩放读取替代 API；保留并在上方抑制 deprecation。
            val contentHeightPx = webView.contentHeight * webView.scale
            val viewportPx = webView.height.toFloat()

            val denominator = max(1f, contentHeightPx - viewportPx)
            val progress = (scrollY / denominator).coerceIn(0f, 1f)

            job?.cancel()
            job = coroutineScope.launch {
                delay(debounceMs)
                historyRepo.updateProgress(sourcePathRaw, progress, -1)
            }
        }
    }

    fun stopTracking() {
        job?.cancel()
        webView.setOnScrollChangeListener(null)
    }
}

