package core.reader.model

/**
 * 阅读进度快照。兼容WebView(row)和PDF(page)。
 */
data class ReadingPosition(
    val progress: Float, // WebView归一化滚动百分比
    val pageIndex: Int   // PDF页码，如果无则-1
)
