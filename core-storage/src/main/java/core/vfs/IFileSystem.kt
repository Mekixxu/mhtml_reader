package core.vfs

import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import java.io.InputStream
import java.io.OutputStream

interface IFileSystem {
    suspend fun list(dir: VfsPath, offset: Int = 0, limit: Int = 100): Result<List<VfsEntry>>
    suspend fun openInputStream(path: VfsPath): Result<InputStream>
    suspend fun openOutputStream(path: VfsPath, append: Boolean = false): Result<OutputStream>
    suspend fun createFile(parentDir: VfsPath, name: String, mimeType: String? = null): Result<VfsPath>
    suspend fun exists(path: VfsPath): Result<Boolean>
    suspend fun createFolder(path: VfsPath): Result<Unit>
    suspend fun delete(path: VfsPath): Result<Unit>
    suspend fun rename(from: VfsPath, toName: String): Result<VfsPath>
    suspend fun move(from: VfsPath, toDir: VfsPath): Result<VfsPath>
    suspend fun copy(from: VfsPath, toDir: VfsPath): Result<VfsPath>

    /**
     * 移动到明确目标路径。目标已存在时必须失败，禁止覆盖。
     * 默认不支持，由具体实现按需覆盖（本地文件系统已实现）。
     */
    suspend fun moveTo(from: VfsPath, target: VfsPath): Result<VfsPath> =
        Result.failure(core.common.AppError.UnsupportedOperation)

    /**
     * 复制到明确目标路径。目标已存在时必须失败，禁止覆盖。
     * 默认不支持，由具体实现按需覆盖（本地文件系统已实现）。
     */
    suspend fun copyTo(from: VfsPath, target: VfsPath): Result<VfsPath> =
        Result.failure(core.common.AppError.UnsupportedOperation)

    suspend fun lastModified(path: VfsPath): Result<Long>
}
