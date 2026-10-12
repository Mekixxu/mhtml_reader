package core.fileops.util

import core.common.AppError
import core.common.EntryNameValidator
import core.vfs.model.VfsPath

/**
 * 文件名工具。支持多扩展名和路径拼装。
 */
object FileNameUtils {
    fun getNameWithoutExtension(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)
    fun getExtension(fileName: String): String =
        fileName.substringAfterLast('.', "")

    fun childPath(dir: VfsPath, name: String): VfsPath {
        if (!EntryNameValidator.isValid(name)) {
            throw AppError.InvalidName
        }
        return when (dir) {
            is VfsPath.LocalFile -> VfsPath.LocalFile(
                if (dir.filePath.endsWith("/")) dir.filePath + name else dir.filePath + "/" + name
            )
            is VfsPath.SafTree -> VfsPath.SafTree(dir.uri) // 补充SAF路径生成逻辑具体实现
            else -> throw AppError.UnsupportedOperation
        }
    }
}
