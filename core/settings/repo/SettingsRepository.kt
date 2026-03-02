package core.settings.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import core.settings.model.AppSettings
import core.settings.store.SettingsKeys
import core.settings.store.settingsDataStore
import core.settings.util.SettingsSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Settings观察与设置。所有IO通过withContext(Dispatchers.IO)
 */
class SettingsRepository(
    private val context: Context
) {
    fun observe(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            historyMaxItems = prefs[SettingsKeys.HISTORY_MAX_ITEMS] ?: AppSettings.DEFAULT_HISTORY_MAX_ITEMS,
            historyMaxDays = prefs[SettingsKeys.HISTORY_MAX_DAYS] ?: AppSettings.DEFAULT_HISTORY_MAX_DAYS,
            cacheMaxBytes = prefs[SettingsKeys.CACHE_MAX_BYTES] ?: AppSettings.DEFAULT_CACHE_MAX_BYTES,
            titleCacheMaxDays = prefs[SettingsKeys.TITLE_CACHE_MAX_DAYS] ?: AppSettings.DEFAULT_TITLE_CACHE_MAX_DAYS
        )
    }

    suspend fun setHistoryMaxItems(value: Int) = withContext(Dispatchers.IO) {
        val v = SettingsSanitizer.clampHistoryMaxItems(value)
        context.settingsDataStore.edit { it[SettingsKeys.HISTORY_MAX_ITEMS] = v }
    }
    suspend fun setHistoryMaxDays(value: Int) = withContext(Dispatchers.IO) {
        val v = SettingsSanitizer.clampHistoryMaxDays(value)
        context.settingsDataStore.edit { it[SettingsKeys.HISTORY_MAX_DAYS] = v }
    }
    suspend fun setCacheMaxBytes(value: Long) = withContext(Dispatchers.IO) {
        val v = SettingsSanitizer.clampCacheMaxBytes(value)
        context.settingsDataStore.edit { it[SettingsKeys.CACHE_MAX_BYTES] = v }
    }
    suspend fun setTitleCacheMaxDays(value: Int) = withContext(Dispatchers.IO) {
        val v = SettingsSanitizer.clampTitleCacheMaxDays(value)
        context.settingsDataStore.edit { it[SettingsKeys.TITLE_CACHE_MAX_DAYS] = v }
    }
}
