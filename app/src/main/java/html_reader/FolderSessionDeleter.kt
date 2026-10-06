package com.html_reader

import android.content.Context
import com.html_reader.files.FilesScrollStateStore
import core.session.repo.FolderSessionRepository

/**
 * 目录会话删除：同步清理绑定与滚动状态，并修复悬空的当前会话指向。
 *
 * 悬空的当前会话 id 会导致下次进入文件页时 switchToSession 静默返回（页面空白），
 * 因此删除当前会话时必须把当前指向切到剩余会话（或置空由 ensureDefaultSession 兜底）。
 */
object FolderSessionDeleter {
    suspend fun delete(
        context: Context,
        sessionId: Long,
        folderSessionRepository: FolderSessionRepository,
        sessionSourceStore: AppSessionSourceStore,
        currentSessionStore: AppCurrentSessionStore
    ) {
        folderSessionRepository.delete(sessionId)
        sessionSourceStore.removeSession(sessionId)
        FilesScrollStateStore.clear(context, sessionId)
        if (currentSessionStore.get() == sessionId) {
            val remaining = folderSessionRepository.getAll()
            currentSessionStore.set(remaining.firstOrNull()?.id)
        }
    }
}
