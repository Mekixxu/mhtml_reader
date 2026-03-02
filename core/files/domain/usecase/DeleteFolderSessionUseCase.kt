package core.files.domain.usecase

import core.session.repo.FolderSessionRepository

class DeleteFolderSessionUseCase(
    private val sessionRepo: FolderSessionRepository
) {
    suspend fun delete(sessionId: Long) = sessionRepo.delete(sessionId)
}
