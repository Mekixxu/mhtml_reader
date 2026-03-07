package core.settings.store

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore实例（for DI）
 */
val Context.settingsDataStore by preferencesDataStore(name = "app_settings")
