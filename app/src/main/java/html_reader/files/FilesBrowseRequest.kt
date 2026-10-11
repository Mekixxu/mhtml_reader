package com.html_reader.files

import java.io.File

/**
 * 本地目录加载的共享代次：每次发起加载都使之前的请求失效。
 */
internal class FilesBrowseRequestGuard {
    private var currentGeneration = 0L

    fun begin(): Long {
        currentGeneration += 1
        return currentGeneration
    }

    fun isCurrent(generation: Long): Boolean = generation == currentGeneration
}

/**
 * 本地目录加载请求快照。IO 完成后同时校验代次、来源、会话和目录，避免旧结果污染当前页面。
 */
internal data class LocalBrowseRequest(
    val generation: Long,
    val sessionId: Long?,
    val source: BrowseSource,
    val directory: File
) {
    fun matches(source: BrowseSource, sessionId: Long?, directory: File?): Boolean =
        this.source == source &&
            this.sessionId == sessionId &&
            directory != null &&
            directory.absolutePath == this.directory.absolutePath
}
