package core.vfs.local

import java.io.File

/**
 * 仅用于本地文件类型
 */
fun File.sizeSafe(): Long = if (isFile) length() else 0L
fun File.lastModifiedSafe(): Long = if (exists()) lastModified() else 0L
