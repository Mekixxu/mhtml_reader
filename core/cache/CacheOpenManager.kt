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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一缓存打开、流式拷贝
 * - 缓存分区按类型/[contentType]/[cacheKey]/content.[ext]组织提高维护与调试便利
 * - cacheKey生成纳入contentType、路径、size
 * - extName按内容类型推断，未知类型返回 Result.failure，不抛异常
 * - 同一 cacheKey 使用 Mutex 单飞，避免并发写坏缓存
 */
class CacheOpenManager(
    private val context: Context,
    private val cacheRoot: File, // e.g. context.cacheDir/app_cache/
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider,
    private val cacheEvictor: CacheEvictor,
    private val activeKeysProvider: () -> Set<String> = { emptySet() }
) {
    private val copyLocks = ConcurrentHashMap<String, Mutex>()
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    suspend fun openToCache(
        src: VfsPath,
        totalBytes: Long,
        contentType: ContentType,
        versionStamp: String? = null,
        extName: String? = null
    ): Flow<Result<CopyProgress>> = flow {
        // 生成cacheKey
        val cacheKey = generateCacheKey(
            src = src,
            contentType = contentType,
            size = totalBytes,
            versionStamp = versionStamp
        )
        // 同一 cacheKey 串行拷贝，避免并发打开同一文件时互相写坏缓存
        val copyLock = copyLocks.computeIfAbsent(cacheKey) { Mutex() }
        copyLock.lock()
        inFlightKeys.add(cacheKey)
        try {
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
                    else -> null // InvalidContentType，按失败结果返回，不抛异常
                }
            if (fileExt == null) {
                emit(Result.failure(AppError.InvalidUri))
                return@flow
            }
            val cacheFile = File(cacheDir, "content.$fileExt")

            // 已存在直接100%进度
            if (cacheFile.exists() && cacheFile.length() == totalBytes) {
                emit(Result.success(CopyProgress(totalBytes, totalBytes)))
                return@flow
            }

            // Proactive eviction：保护活跃 tab 与所有在途拷贝的 cacheKey
            val eviction = cacheEvictor.makeRoomFor(totalBytes, activeKeysProvider() + inFlightKeys, cacheKey)
            if (eviction != EvictionResult.READY) {
                val message = when (eviction) {
                    EvictionResult.EXCEEDS_LIMIT -> "File exceeds cache capacity"
                    else -> "Cache is held by active tabs; close some tabs and retry"
                }
                emit(Result.failure(AppError.IoError(message, null)))
                return@flow
            }

            // 开始流式拷贝
            val inputResult = fileSystem.openInputStream(src)
            val `in` = inputResult.getOrElse { error ->
                emit(Result.failure(AppError.IoError("OpenInputStream failed", error)))
                return@flow
            }
            var out: OutputStream? = null
            try {
                out = cacheFile.outputStream()
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                var read: Int
                while (true) {
                    coroutineContext.ensureActive()
                    read = `in`.read(buf)
                    if (read == -1) break
                    out.write(buf, 0, read)
                    copied += read
                    emit(Result.success(CopyProgress(copied, totalBytes)))
                }
                out.flush()
            } catch (ce: CancellationException) {
                cacheFile.delete()
                emit(Result.failure(ce))
                throw ce
            } catch (e: Throwable) {
                cacheFile.delete()
                // 只上报失败，不再二次抛出，避免调用方 collect 崩溃；并终止，避免落入末尾的假成功
                emit(Result.failure(e))
                return@flow
            } finally {
                try { `in`.close() } catch (_: Throwable) {}
                try { out?.close() } catch (_: Throwable) {}
            }
            emit(Result.success(CopyProgress(totalBytes, totalBytes)))
        } finally {
            inFlightKeys.remove(cacheKey)
            copyLock.unlock()
        }
    }.flowOn(dispatcherProvider.io)

    fun generateCacheKey(
        src: VfsPath,
        contentType: ContentType,
        size: Long,
        versionStamp: String? = null
    ): String {
        val id = src.raw
        val versionPart = versionStamp ?: "size:$size"
        val key = "${contentType.name}:${id}:${versionPart}"
        return HashUtils.sha256(key)
    }

    fun resolveCacheFile(contentType: ContentType, cacheKey: String, extName: String): File =
        cacheRoot.resolve(contentType.name.lowercase()).resolve(cacheKey).resolve("content.$extName")
}
