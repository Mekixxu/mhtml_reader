package core.files.domain.usecase

import core.vfs.IFileSystem
import core.vfs.FileSystemResolver
import core.files.domain.model.SUPPORTED_EXTENSIONS
import core.files.domain.model.DirectoryState
import core.domain.model.SortOption
import core.vfs.model.VfsEntry
import core.session.repo.FolderSessionRepository
import core.vfs.model.VfsPath
import core.common.AppError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * 目录流式观察(支持切换/过滤/排序/搜索)。一切在IO线程。
 * sort/query发生变化都重新pull目录。
 */
class ObserveDirectoryUseCase(
    private val vfsResolver: FileSystemResolver,
    private val sessionRepo: FolderSessionRepository,
    private val dispatcherProvider: core.common.DispatcherProvider
) {
    fun execute(
        sessionId: Long,
        sort: Flow<SortOption>,
        query: Flow<String>
    ): Flow<DirectoryState> =
        combine(
            sessionRepo.observeAll().map { it.find { s -> s.id == sessionId } },
            sort, query
        ) { sessionEntity, sortOption, q ->
            Triple(sessionEntity, sortOption, q)
        }
            .filter { (session, _, _) -> session != null }
            .distinctUntilChanged()
            .flatMapLatest { (session, sortOption, q) ->
                flow {
                    val sessionEntity = session!!
                    val currentDir = VfsPath.LocalFile(sessionEntity.currentPath)
                    val fs = vfsResolver.resolve(currentDir)
                    val entries: List<VfsEntry> = try {
                        fs.list(currentDir).getOrElse { throw it }
                    } catch (e: AppError) {
                        emit(DirectoryState(sessionId, currentDir, emptyList(), sortOption, q))
                        return@flow
                    }
                    // 先目录，后文件，文件按类型白名单过滤
                    val folders = entries.filter { it.isDirectory }
                    val files = entries.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS }
                    val filteredFiles = if (q.isBlank()) files else files.filter { it.name.contains(q, ignoreCase = true) }
                    val filteredFolders = if (q.isBlank()) folders else folders.filter { it.name.contains(q, ignoreCase = true) }
                    val foldersPlusFiles = when (sortOption) {
                        SortOption.NAME_ASC -> (filteredFolders + filteredFiles).sortedBy { it.name }
                        SortOption.NAME_DESC -> (filteredFolders + filteredFiles).sortedByDescending { it.name }
                        SortOption.SIZE_ASC -> (filteredFolders + filteredFiles).sortedBy { it.sizeBytes }
                        SortOption.SIZE_DESC -> (filteredFolders + filteredFiles).sortedByDescending { it.sizeBytes }
                        SortOption.MODIFIED_ASC -> (filteredFolders + filteredFiles).sortedBy { it.lastModifiedEpochMs }
                        SortOption.MODIFIED_DESC -> (filteredFolders + filteredFiles).sortedByDescending { it.lastModifiedEpochMs }
                    }
                    emit(DirectoryState(sessionId, currentDir, foldersPlusFiles, sortOption, q))
                }.flowOn(dispatcherProvider.io)
            }
}
