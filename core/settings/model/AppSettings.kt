package core.settings.model

/**
 * 应用可配置项（SSOT要求）
 */
data class AppSettings(
    val historyMaxItems: Int = DEFAULT_HISTORY_MAX_ITEMS,
    val historyMaxDays: Int = DEFAULT_HISTORY_MAX_DAYS,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
    val titleCacheMaxDays: Int = DEFAULT_TITLE_CACHE_MAX_DAYS
) {
    companion object {
        const val DEFAULT_HISTORY_MAX_ITEMS = 500
        const val DEFAULT_HISTORY_MAX_DAYS = 365
        const val DEFAULT_CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2GB
        const val DEFAULT_TITLE_CACHE_MAX_DAYS = 90
        const val MIN_HISTORY_MAX_ITEMS = 1
        const val MIN_HISTORY_MAX_DAYS = 1
        const val MIN_CACHE_MAX_BYTES = 50L * 1024 * 1024 // 50MB
        const val MIN_TITLE_CACHE_MAX_DAYS = 1
    }
}
