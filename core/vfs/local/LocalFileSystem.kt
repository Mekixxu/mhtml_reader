package core.vfs.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import core.common.AppError
import core.common.DispatcherProvider
import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LocalFileSystem(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : IFileSystem {

    override suspend fun openInputStream(path: VfsPath): Result<InputStream> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> Result.success(File(path.filePath).inputStream())
                    is VfsPath.SafTree -> {
                        val input = context.contentResolver.openInputStream(path.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        Result.success(input)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun openOutputStream(path: VfsPath, append: Boolean): Result<OutputStream> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> {
                        val out = File(path.filePath).outputStream()
                        Result.success(out)
                    }
                    is VfsPath.SafTree -> {
                        val out = context.contentResolver.openOutputStream(path.uri, if (append) "wa" else "w")
                        out?.let { Result.success(it) } ?: Result.failure(AppError.NotFound)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun createFile(parentDir: VfsPath, name: String, mimeType: String?): Result<VfsPath> =
        withContext(dispatcherProvider.io) {
            try {
                when (parentDir) {
                    is VfsPath.LocalFile -> {
                        val dir = File(parentDir.filePath)
                        if (!dir.exists() || !dir.isDirectory) return@withContext Result.failure(AppError.NotFound)
                        val file = File(dir, name)
                        if (file.createNewFile()) Result.success(VfsPath.LocalFile(file.absolutePath))
                        else Result.failure(AppError.IoError("Create file failed"))
                    }
                    is VfsPath.SafTree -> {
                        val dir = DocumentFile.fromTreeUri(context, parentDir.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val docFile = dir.createFile(mimeType ?: "application/octet-stream", name)
                            ?: return@withContext Result.failure(AppError.IoError("Create SAF file failed"))
                        Result.success(VfsPath.SafTree(docFile.uri))
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    // 其它接口如前略 (见包1)
    // ...

    override suspend fun list(dir: VfsPath, offset: Int, limit: Int): Result<List<VfsEntry>> =
        withContext(dispatcherProvider.io) {
            try {
                when (dir) {
                    is VfsPath.LocalFile -> {
                        val f = File(dir.filePath)
                        if (!f.exists() || !f.isDirectory) return@withContext Result.failure(AppError.NotFound)
                        val files = f.listFiles()
                            ?.asSequence()
                            ?.drop(offset)
                            ?.take(limit)
                            ?.map {
                                ensureActive()
                                it.toVfsEntry()
                            }
                            ?.toList() ?: emptyList()
                        Result.success(files)
                    }
                    is VfsPath.SafTree -> {
                        val df = DocumentFile.fromTreeUri(context, dir.uri)
                            ?: return@withContext Result.failure(AppError.InvalidUri)
                        val childList = df.listFiles()
                            .asSequence()
                            .drop(offset)
                            .take(limit)
                            .mapNotNull {
                                ensureActive()
                                it.toVfsEntrySaf(context)
                            }
                            .toList()
                        Result.success(childList)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun exists(path: VfsPath): Result<Boolean> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> Result.success(File(path.filePath).exists())
                    is VfsPath.SafTree -> {
                        val single = DocumentFile.fromSingleUri(context, path.uri)
                        val tree = DocumentFile.fromTreeUri(context, path.uri)
                        Result.success(single?.exists() == true || tree?.exists() == true)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun createFolder(path: VfsPath): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> {
                        val target = File(path.filePath)
                        if (target.exists() && target.isDirectory) {
                            Result.success(Unit)
                        } else if (target.mkdirs()) {
                            Result.success(Unit)
                        } else {
                            Result.failure(AppError.IoError("Create folder failed"))
                        }
                    }
                    is VfsPath.SafTree -> {
                        val tree = DocumentFile.fromTreeUri(context, path.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        if (tree.exists() && tree.isDirectory) Result.success(Unit)
                        else Result.failure(AppError.UnsupportedOperation)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun delete(path: VfsPath): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> {
                        val file = File(path.filePath)
                        if (!file.exists()) return@withContext Result.success(Unit)
                        if (file.deleteRecursively()) Result.success(Unit)
                        else Result.failure(AppError.IoError("Delete failed"))
                    }
                    is VfsPath.SafTree -> {
                        val doc = DocumentFile.fromSingleUri(context, path.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        if (doc.delete()) Result.success(Unit)
                        else Result.failure(AppError.IoError("Delete failed"))
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun rename(from: VfsPath, toName: String): Result<VfsPath> =
        withContext(dispatcherProvider.io) {
            try {
                when (from) {
                    is VfsPath.LocalFile -> {
                        val oldFile = File(from.filePath)
                        val parent = oldFile.parentFile ?: return@withContext Result.failure(AppError.NotFound)
                        val newFile = File(parent, toName)
                        if (oldFile.renameTo(newFile)) Result.success(VfsPath.LocalFile(newFile.absolutePath))
                        else Result.failure(AppError.IoError("Rename failed"))
                    }
                    is VfsPath.SafTree -> {
                        val doc = DocumentFile.fromSingleUri(context, from.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val renamed = doc.renameTo(toName)
                        if (!renamed) return@withContext Result.failure(AppError.IoError("Rename failed"))
                        val parent = doc.parentFile ?: return@withContext Result.failure(AppError.NotFound)
                        val updated = parent.safeFindFile(toName)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        Result.success(VfsPath.SafTree(updated.uri))
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun move(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
        withContext(dispatcherProvider.io) {
            try {
                when {
                    from is VfsPath.LocalFile && toDir is VfsPath.LocalFile -> {
                        val src = File(from.filePath)
                        val targetDir = File(toDir.filePath)
                        if (!targetDir.exists() || !targetDir.isDirectory) {
                            return@withContext Result.failure(AppError.NotFound)
                        }
                        val dst = File(targetDir, src.name)
                        if (src.renameTo(dst)) Result.success(VfsPath.LocalFile(dst.absolutePath))
                        else Result.failure(AppError.IoError("Move failed"))
                    }
                    from is VfsPath.SafTree && toDir is VfsPath.SafTree -> {
                        val srcDoc = DocumentFile.fromSingleUri(context, from.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val targetDir = DocumentFile.fromTreeUri(context, toDir.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val copied = srcDoc.copyToDir(context, targetDir)
                            ?: return@withContext Result.failure(AppError.IoError("Move copy failed"))
                        if (!srcDoc.delete()) return@withContext Result.failure(AppError.IoError("Move cleanup failed"))
                        Result.success(VfsPath.SafTree(copied.uri))
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun copy(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
        withContext(dispatcherProvider.io) {
            try {
                when {
                    from is VfsPath.LocalFile && toDir is VfsPath.LocalFile -> {
                        val src = File(from.filePath)
                        val targetDir = File(toDir.filePath)
                        if (!targetDir.exists() || !targetDir.isDirectory) {
                            return@withContext Result.failure(AppError.NotFound)
                        }
                        val dst = File(targetDir, src.name)
                        src.copyTo(dst, overwrite = false)
                        Result.success(VfsPath.LocalFile(dst.absolutePath))
                    }
                    from is VfsPath.SafTree && toDir is VfsPath.SafTree -> {
                        val srcDoc = DocumentFile.fromSingleUri(context, from.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val targetDir = DocumentFile.fromTreeUri(context, toDir.uri)
                            ?: return@withContext Result.failure(AppError.NotFound)
                        val copied = srcDoc.copyToDir(context, targetDir)
                            ?: return@withContext Result.failure(AppError.IoError("Copy failed"))
                        Result.success(VfsPath.SafTree(copied.uri))
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }

    override suspend fun lastModified(path: VfsPath): Result<Long> =
        withContext(dispatcherProvider.io) {
            try {
                when (path) {
                    is VfsPath.LocalFile -> Result.success(File(path.filePath).lastModified())
                    is VfsPath.SafTree -> {
                        val doc = DocumentFile.fromSingleUri(context, path.uri)
                        Result.success(doc?.lastModified() ?: 0L)
                    }
                    else -> Result.failure(AppError.UnsupportedOperation)
                }
            } catch (e: Throwable) {
                Result.failure(AppError.IoError(e.message, e))
            }
        }
}

private fun File.toVfsEntry(): VfsEntry =
    VfsEntry(
        name = name,
        path = VfsPath.LocalFile(absolutePath),
        isDirectory = isDirectory,
        sizeBytes = if (isFile) length() else 0L,
        lastModifiedEpochMs = lastModified(),
        mimeType = null
    )

private fun DocumentFile.toVfsEntrySaf(context: Context): VfsEntry? {
    val resolvedName = name ?: return null
    return VfsEntry(
        name = resolvedName,
        path = VfsPath.SafTree(uri),
        isDirectory = isDirectory,
        sizeBytes = if (isFile) length() else 0L,
        lastModifiedEpochMs = lastModified(),
        mimeType = type
    )
}

private fun DocumentFile.copyToDir(context: Context, targetDir: DocumentFile): DocumentFile? {
    if (!exists() || !isFile) return null
    val target = targetDir.createFile(type ?: "application/octet-stream", name ?: "file") ?: return null
    context.contentResolver.openInputStream(uri).use { input ->
        context.contentResolver.openOutputStream(target.uri).use { output ->
            if (input == null || output == null) return null
            input.copyTo(output, bufferSize = 64 * 1024)
        }
    }
    return target
}
