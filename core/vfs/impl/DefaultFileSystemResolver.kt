package core.vfs.impl

import core.vfs.IFileSystem
import core.vfs.FileSystemResolver
import core.vfs.model.VfsPath
import core.vfs.local.LocalFileSystem

/**
 * v1.0 实现，只支持本地，本地SAF后续补齐。
 */
class DefaultFileSystemResolver(
    private val localFileSystem: LocalFileSystem
) : FileSystemResolver {
    override fun resolve(path: VfsPath): IFileSystem {
        // 未来可按path.scheme切换SMB/FTP等
        return localFileSystem
    }
}
