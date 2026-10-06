package com.html_reader.files

import android.content.Context
import android.content.SharedPreferences

/**
 * 每会话的列表滚动位置持久化（path + position + top）。
 *
 * - 目录路径本身持久化在 FolderSession.currentPath（Room）；
 *   这里只负责「列表内滚动位置」，使 FTP/SMB 与本地会话一致地恢复浏览位置。
 * - 每会话仅保留一条（离开该会话/页面时覆盖写入），避免无限增长。
 * - 写入为 SharedPreferences 异步 apply，主线程开销可忽略。
 */
object FilesScrollStateStore {
    const val PREFS_NAME = "files_browser_settings"
    private const val KEY_PATH_PREFIX = "scroll_path_"
    private const val KEY_POSITION_PREFIX = "scroll_position_"
    private const val KEY_TOP_PREFIX = "scroll_top_"

    data class ScrollState(val position: Int, val top: Int)

    fun save(context: Context, sessionId: Long, path: String, position: Int, top: Int) {
        prefs(context).edit()
            .putString(KEY_PATH_PREFIX + sessionId, path)
            .putInt(KEY_POSITION_PREFIX + sessionId, position)
            .putInt(KEY_TOP_PREFIX + sessionId, top)
            .apply()
    }

    /** 会话与路径均匹配时返回上次滚动位置；否则返回 null（列表从顶部开始）。 */
    fun restore(context: Context, sessionId: Long, path: String): ScrollState? {
        val prefs = prefs(context)
        if (prefs.getString(KEY_PATH_PREFIX + sessionId, null) != path) return null
        val position = prefs.getInt(KEY_POSITION_PREFIX + sessionId, Int.MIN_VALUE)
        if (position == Int.MIN_VALUE) return null
        return ScrollState(position, prefs.getInt(KEY_TOP_PREFIX + sessionId, 0))
    }

    fun clear(context: Context, sessionId: Long) {
        prefs(context).edit()
            .remove(KEY_PATH_PREFIX + sessionId)
            .remove(KEY_POSITION_PREFIX + sessionId)
            .remove(KEY_TOP_PREFIX + sessionId)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
