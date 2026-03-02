package core.fileops.model

import core.vfs.model.VfsPath
import core.fileops.model.ConflictStrategy

/**
 * 文件操作请求封装类
 */
sealed class FileOpRequest {
    data class CreateFolder(val parentDir: VfsPath, val name: String) : FileOpRequest()
    data class Delete(val target: VfsPath, val recursive: Boolean = true) : FileOpRequest()
    data class Rename(val target: VfsPath, val newName: String) : FileOpRequest()
    data class Copy(
        val from: VfsPath,
        val toDir: VfsPath,
        val conflict: ConflictStrategy = ConflictStrategy.FAIL
    ) : FileOpRequest()
    data class Move(
        val from: VfsPath,
        val toDir: VfsPath,
        val conflict: ConflictStrategy = ConflictStrategy.FAIL
    ) : FileOpRequest()
}
