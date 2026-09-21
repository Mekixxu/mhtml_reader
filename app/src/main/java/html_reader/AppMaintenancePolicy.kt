package com.html_reader

import core.settings.model.AppSettings

/**
 * 应用维护任务的唯一参数源。
 *
 * 当前生效路径是 HtmlReaderApp 中注册的两个 App Worker；
 * core/work 下的 Hilt Worker 已参与编译，仅作为后续 DI 迁移路径保留，
 * 不与这里重复调度。
 */
object AppMaintenancePolicy {
    const val HISTORY_MAX_ITEMS = AppSettings.DEFAULT_HISTORY_MAX_ITEMS
    const val HISTORY_MAX_DAYS = AppSettings.DEFAULT_HISTORY_MAX_DAYS
    const val ORPHAN_DAYS_UNUSED = 3
    const val TITLE_CACHE_MAX_DAYS = AppSettings.DEFAULT_TITLE_CACHE_MAX_DAYS

    const val HISTORY_RETENTION_WORK_NAME = "history_retention_daily"
    const val ORPHAN_CACHE_WORK_NAME = "orphan_cache_cleanup_daily"
}
