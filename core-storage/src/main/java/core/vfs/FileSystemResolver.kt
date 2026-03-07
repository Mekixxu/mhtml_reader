package core.vfs

import core.vfs.model.VfsPath

interface FileSystemResolver {
    fun resolve(path: VfsPath): IFileSystem
}
