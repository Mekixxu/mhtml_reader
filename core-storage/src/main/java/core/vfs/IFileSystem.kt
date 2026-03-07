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
    suspend fun lastModified(path: VfsPath): Result<Long>
}
