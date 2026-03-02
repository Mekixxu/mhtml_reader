package core.settings.util

import core.settings.model.AppSettings

/**
 * 用户设置安全范围过滤
 */
object SettingsSanitizer {
    fun clampHistoryMaxItems(v: Int): Int = v.coerceAtLeast(AppSettings.MIN_HISTORY_MAX_ITEMS)
    fun clampHistoryMaxDays(v: Int): Int = v.coerceAtLeast(AppSettings.MIN_HISTORY_MAX_DAYS)
    fun clampCacheMaxBytes(v: Long): Long = v.coerceAtLeast(AppSettings.MIN_CACHE_MAX_BYTES)
    fun clampTitleCacheMaxDays(v: Int): Int = v.coerceAtLeast(AppSettings.MIN_TITLE_CACHE_MAX_DAYS)
}
