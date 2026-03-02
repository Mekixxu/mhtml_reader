package core.cache

import android.content.Context
import core.cache.model.CacheOpenResult
import core.cache.model.ContentType
import core.cache.model.CopyProgress
import core.common.AppError
import core.common.DispatcherProvider
import core.common.HashUtils
import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * 统一缓存打开、流式拷贝
 * - 缓存分区按类型/[contentType]/[cacheKey]/content.[ext]组织提高维护与调试便利
 * - cacheKey生成纳入contentType、路径、size
 * - extName按内容类型、若未知直接抛InvalidContentType
 */
class CacheOpenManager(
    private val context: Context,
    private val cacheRoot: File, // e.g. context.cacheDir/app_cache/
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun openToCache(
        src: VfsPath,
        totalBytes: Long,
        contentType: ContentType,
        extName: String? = null
    ): Flow<Result<CopyProgress>> = flow {
        withContext(dispatcherProvider.io) {
            // 生成cacheKey
            val cacheKey = generateCacheKey(src, contentType, totalBytes)
            val typeDir = cacheRoot.resolve(contentType.name.lowercase())
            typeDir.mkdirs()
            val cacheDir = typeDir.resolve(cacheKey)
            cacheDir.mkdirs()
            val fileExt = extName
                ?: when (contentType) {
                    ContentType.PDF -> "pdf"
                    ContentType.MHTML -> "mhtml"
                    ContentType.HTML -> "html"
                    ContentType.WEB -> "web"
                    else -> throw AppError.InvalidUri // InvalidContentType，无效类型直接抛异常
                }
            val cacheFile = File(cacheDir, "content.$fileExt")

            // 已存在直接100%进度
            if (cacheFile.exists() && cacheFile.length() == totalBytes) {
                emit(Result.success(CopyProgress(totalBytes, totalBytes)))
                return@withContext
            }
            // 开始流式拷贝
            val inputResult = fileSystem.openInputStream(src)
            val `in` = inputResult.getOrElse {
                throw AppError.IoError("OpenInputStream failed", it)
            }
            var out: OutputStream? = null
            try {
                out = cacheFile.outputStream()
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                var read: Int
                while (true) {
                    read = `in`.read(buf)
                    if (read == -1) break
                    out.write(buf, 0, read)
                    copied += read
                    emit(Result.success(CopyProgress(copied, totalBytes)))
                    // 协程取消支持
                    if (!Thread.currentThread().isInterrupted) continue else throw CancellationException()
                }
                out.flush()
            } catch (ce: CancellationException) {
                cacheFile.delete()
                emit(Result.failure(ce))
                throw ce
            } catch (e: Throwable) {
                cacheFile.delete()
                emit(Result.failure(e))
                throw AppError.IoError("Copy failed: $e", e)
            } finally {
                try { `in`.close() } catch (_: Throwable) {}
                try { out?.close() } catch (_: Throwable) {}
            }
            emit(Result.success(CopyProgress(totalBytes, totalBytes)))
        }
    }

    fun generateCacheKey(src: VfsPath, contentType: ContentType, size: Long): String {
        val id = src.raw
        val key = "${contentType.name}:${id}:${size}"
        return HashUtils.sha256(key)
    }

    fun resolveCacheFile(contentType: ContentType, cacheKey: String, extName: String): File =
        cacheRoot.resolve(contentType.name.lowercase()).resolve(cacheKey).resolve("content.$extName")
}
