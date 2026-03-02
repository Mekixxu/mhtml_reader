package core.vfs

import core.vfs.model.VfsPath

/**
 * 文件系统选择器。后续可支持SMB/FTP。
 */
interface FileSystemResolver {
    fun resolve(path: VfsPath): IFileSystem
}
