package core.reader.pdf

/**
 * PDF 渲染请求键：相同 tab 的相同页码才能复用已应用结果。
 */
data class PdfRenderKey(
    val tabId: String,
    val pageIndex: Int
)

/**
 * PDF 内容代次与渲染键仲裁：
 * - 每次选择内容调用 [beginContentLoad]，旧代次的异步结果全部作废；
 * - [shouldSchedule] 防止同页重复渲染，[PdfRenderKey] 包含 tabId，因此 A0→B0 必须重新渲染。
 */
class PdfContentState {
    var generation: Long = 0L
        private set

    private var appliedKey: PdfRenderKey? = null
    private var pendingKey: PdfRenderKey? = null

    fun beginContentLoad(): Long {
        generation += 1
        appliedKey = null
        pendingKey = null
        return generation
    }

    fun invalidate() {
        generation += 1
        pendingKey = null
        appliedKey = null
    }

    fun isCurrent(candidate: Long): Boolean = candidate == generation

    fun shouldSchedule(key: PdfRenderKey, force: Boolean): Boolean =
        force || (key != appliedKey && key != pendingKey)

    fun markPending(key: PdfRenderKey) {
        pendingKey = key
    }

    fun clearPending() {
        pendingKey = null
    }

    fun markApplied(key: PdfRenderKey) {
        appliedKey = key
        pendingKey = null
    }
}
