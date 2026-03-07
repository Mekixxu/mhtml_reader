package com.html_reader

import android.content.Context

class AppSessionSourceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_session_source_store", Context.MODE_PRIVATE)

    fun setNetworkConfigId(sessionId: Long, networkConfigId: Long?) {
        val key = "network_config_$sessionId"
        if (networkConfigId == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putLong(key, networkConfigId).apply()
        }
    }

    fun getNetworkConfigId(sessionId: Long): Long? {
        val key = "network_config_$sessionId"
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun removeSession(sessionId: Long) {
        prefs.edit().remove("network_config_$sessionId").apply()
    }
}
