package core.files.domain.usecase

import core.session.repo.FolderSessionRepository
import core.vfs.model.VfsPath

/**
 * 创建新会话。返回新ID。
 */
class CreateFolderSessionUseCase(
    private val sessionRepo: FolderSessionRepository
) {
    suspend fun create(name: String, root: VfsPath): Long {
        return sessionRepo.add(name, root.raw)
    }
}
