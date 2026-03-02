package core.files.domain.usecase

import core.session.repo.FolderSessionRepository
import core.vfs.model.VfsPath

class UpdateCurrentDirUseCase(
    private val sessionRepo: FolderSessionRepository
) {
    suspend fun setCurrentDir(sessionId: Long, dir: VfsPath) {
        sessionRepo.updateCurrentDir(sessionId, dir.raw)
    }
}
