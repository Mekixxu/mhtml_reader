package core.files.domain.usecase

import core.session.repo.FolderSessionRepository
import core.session.entity.FolderSessionEntity
import core.files.domain.model.FolderSession
import core.vfs.model.VfsPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观察所有会话列表，领域模型转换。
 */
class ObserveFolderSessionsUseCase(
    private val sessionRepo: FolderSessionRepository
) {
    fun execute(): Flow<List<FolderSession>> = sessionRepo.observeAll().map { entities ->
        entities.map {
            FolderSession(
                id = it.id,
                name = it.name,
                rootDir = VfsPath.LocalFile(it.rootPath), // todo: 多源适配
                currentDir = VfsPath.LocalFile(it.currentPath),
                createdAt = it.createdAt,
                lastAccess = it.lastAccess
            )
        }
    }
}
