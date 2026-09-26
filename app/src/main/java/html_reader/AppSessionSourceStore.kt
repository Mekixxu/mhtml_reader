package com.html_reader

import android.content.Context

class AppSessionSourceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_session_source_store", Context.MODE_PRIVATE)

    private fun keyFor(sessionId: Long) = "$KEY_NETWORK_CONFIG_PREFIX$sessionId"

    fun setNetworkConfigId(sessionId: Long, networkConfigId: Long?) {
        val key = keyFor(sessionId)
        if (networkConfigId == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putLong(key, networkConfigId).apply()
        }
    }

    fun getNetworkConfigId(sessionId: Long): Long? {
        val key = keyFor(sessionId)
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    /**
     * 反向查找：该网络配置已绑定的会话 id（用于重进时复用会话、保留浏览位置）。
     */
    fun findSessionIdByConfigId(networkConfigId: Long): Long? =
        prefs.all.entries
            .firstOrNull { (key, value) ->
                key.startsWith(KEY_NETWORK_CONFIG_PREFIX) && value is Long && value == networkConfigId
            }
            ?.key
            ?.removePrefix(KEY_NETWORK_CONFIG_PREFIX)
            ?.toLongOrNull()

    fun removeSession(sessionId: Long) {
        prefs.edit().remove(keyFor(sessionId)).apply()
    }

    private companion object {
        const val KEY_NETWORK_CONFIG_PREFIX = "network_config_"
    }
}
