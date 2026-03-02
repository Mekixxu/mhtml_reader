package core.settings.store

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * DataStore key定义
 */
object SettingsKeys {
    val HISTORY_MAX_ITEMS = intPreferencesKey("history_max_items")
    val HISTORY_MAX_DAYS = intPreferencesKey("history_max_days")
    val CACHE_MAX_BYTES = longPreferencesKey("cache_max_bytes")
    val TITLE_CACHE_MAX_DAYS = intPreferencesKey("title_cache_max_days")
}
