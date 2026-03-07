package core.fileops.util

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext
import core.common.DispatcherProvider
import core.common.AppError
import core.fileops.model.ConflictStrategy

/**
 * 文件名冲突解决，支持AUTO_RENAME。
 */
class NameConflictResolver(
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider
) {
    /**
     * 如果冲突且策略为AUTO_RENAME，自动生成file(1).ext，否则按策略处理
     */
    suspend fun resolve(
        toDir: VfsPath,
        desiredName: String,
        strategy: ConflictStrategy
    ): VfsPath = withContext(dispatcherProvider.io) {
        val baseName = FileNameUtils.getNameWithoutExtension(desiredName)
        val ext = FileNameUtils.getExtension(desiredName)

        var nameToTry = desiredName
        var idx = 1
        var resolved: VfsPath? = null
        while (resolved == null) {
            val target = FileNameUtils.childPath(toDir, nameToTry)
            val exists = fileSystem.exists(target).getOrDefault(false)
            if (!exists) {
                resolved = target
                continue
            }
            if (strategy == ConflictStrategy.FAIL) throw AppError.IoError("Name conflict: $nameToTry")
            nameToTry = "$baseName($idx)${if (ext.isNotEmpty()) ".$ext" else ""}"
            idx++
        }
        resolved
    }
}
