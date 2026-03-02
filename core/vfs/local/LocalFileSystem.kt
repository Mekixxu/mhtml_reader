package core.vfs.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import core.common.AppError
import core.common.DispatcherProvider
import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
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
}
