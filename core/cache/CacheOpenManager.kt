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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.OutputStream

/**
 * 统一缓存打开、流式拷贝
 * - 缓存分区按类型/[contentType]/[cacheKey]/content.[ext]组织提高维护与调试便利
 * - cacheKey生成纳入contentType、路径、size
 * - extName按内容类型推断，未知类型返回 Result.failure，不抛异常
 * - 同一 cacheKey 使用 Mutex 单飞，避免并发写坏缓存
 *
 * 完整性约定（Q4）：
 * - 长度用 `Long?` 表达：`null` 未知、`0` 为已知空文件。
 * - 先写本次专属 `.part` 临时文件，关闭输出后校验实际字节数；已知长度不一致即失败。
 * - 只有校验通过才提交为最终 `content.[ext]`；失败/取消只清理本次 `.part`。
 * - 上报真实 copied，不再伪造 total/total；缓存命中只命中已提交成品。
 *
 * 保护与预算约定（Q2/Q3）：
 * - 拷贝开始/缓存命中即经 [leaseRegistry] 取得保护租约；Flow 结束不自动撤销「尚待交给标签」的保护，
 *   由调用方 `transferToTab` 或 `release`（去重/失败/取消）。
 * - 预算经 [cacheEvictor] 统一协调：已知长度首块前一次性 `reserve`；未知长度按块 `growReservation`。
 * - 失败/取消在 `NonCancellable` 中释放预约与未移交租约，避免悬挂。
 */
class CacheOpenManager(
    private val context: Context,
    private val cacheRoot: File, // e.g. context.cacheDir/app_cache/
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider,
    private val cacheEvictor: CacheEvictor,
    private val leaseRegistry: CacheLeaseRegistry,
    private val activeKeysProvider: () -> Set<String> = { emptySet() }
) {
    private val copyLocks = Array(COPY_LOCK_STRIPES) { Mutex() }

    suspend fun openToCache(
        src: VfsPath,
        totalBytes: Long?,
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
        val copyLock = copyLockFor(cacheKey)
        copyLock.lock()
        // 连续保护：拷贝开始/缓存命中即持有租约，直到移交标签或调用方显式释放
        val lease = leaseRegistry.acquire(cacheKey, contentType.name.lowercase())
        var handedOff = false
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
            val partFile = File(cacheDir, "content.$fileExt.part")

            // 只命中已提交成品；成品仅在「关闭输出并通过字节校验」后提交，故存在即完整。
            // 未知长度无法比对字节数，退化为「成品是否存在」。
            if (cacheFile.exists() && (totalBytes == null || cacheFile.length() == totalBytes)) {
                emit(Result.success(CopyProgress(cacheFile.length(), totalBytes, lease)))
                handedOff = true
                return@flow
            }

            // 预算协调：已知长度在首块前一次性预约；未知长度在写入循环中按块扩容
            val protectedKeys = activeKeysProvider()
            if (totalBytes != null) {
                val reserved = cacheEvictor.reserve(cacheKey, totalBytes, protectedKeys)
                if (reserved != EvictionResult.READY) {
                    emit(Result.failure(reserved.toAppError()))
                    return@flow
                }
            }

            // 开始流式拷贝
            val inputResult = fileSystem.openInputStream(src)
            val `in` = inputResult.getOrElse { error ->
                emit(Result.failure(AppError.IoError("OpenInputStream failed", error)))
                return@flow
            }
            var out: OutputStream? = null
            try {
                out = partFile.outputStream()
                val buf = ByteArray(COPY_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = `in`.read(buf)
                    if (read == -1) break
                    // 已知长度的源增长视为变化，避免无限写入
                    if (totalBytes != null && copied + read > totalBytes) {
                        throw EOFException("Source length changed while copying")
                    }
                    // 未知长度：写入前按块扩容预约，超预算即失败，避免撑爆磁盘
                    if (totalBytes == null) {
                        val grown = cacheEvictor.growReservation(cacheKey, copied + read, protectedKeys)
                        if (grown != EvictionResult.READY) {
                            throw CacheBudgetException(grown)
                        }
                    }
                    out.write(buf, 0, read)
                    copied += read
                    emit(Result.success(CopyProgress(copied, totalBytes, lease)))
                }
                // flush/close 失败必须上报失败，不能被 finally 吞掉后仍宣布成功
                out.flush()
                out.close()
                out = null
            } catch (ce: CancellationException) {
                // 取消时关闭句柄并只清理本次临时文件（Windows 下必须先关闭再删）；取消状态下不再 emit
                closeQuietly(out)
                partFile.delete()
                throw ce
            } catch (e: Throwable) {
                // 先关闭句柄再删临时文件，避免句柄占用导致删除失败；
                // 只上报失败，不再二次抛出，避免调用方 collect 崩溃；并终止，避免落入末尾的假成功
                closeQuietly(out)
                partFile.delete()
                emit(Result.failure(e.asAppError()))
                return@flow
            } finally {
                closeQuietly(`in`)
            }

            // 关闭输出后校验实际字节数：已知长度必须完全一致
            val committedBytes = partFile.length()
            if (totalBytes != null && committedBytes != totalBytes) {
                partFile.delete()
                emit(Result.failure(AppError.IoError("Incomplete copy: expected=$totalBytes actual=$committedBytes")))
                return@flow
            }
            // 校验通过才提交成品（替换可能存在的旧成品）
            if (cacheFile.exists() && !cacheFile.delete()) {
                partFile.delete()
                emit(Result.failure(AppError.IoError("Cannot replace stale cache file")))
                return@flow
            }
            if (!partFile.renameTo(cacheFile)) {
                partFile.delete()
                emit(Result.failure(AppError.IoError("Cannot commit cache file")))
                return@flow
            }
            // 提交成功：成品已落盘，注销预约后由「盘上字节」接管计量（口径无缝切换，不双计）
            emit(Result.success(CopyProgress(committedBytes, totalBytes, lease)))
            handedOff = true
        } finally {
            try {
                // 取消/失败也要释放预约与未移交的保护租约（NonCancellable 保证在取消态仍执行）
                withContext(NonCancellable) {
                    cacheEvictor.releaseReservation(cacheKey)
                    if (!handedOff) lease.release()
                }
            } finally {
                copyLock.unlock()
            }
        }
    }.flowOn(dispatcherProvider.io)

    fun generateCacheKey(
        src: VfsPath,
        contentType: ContentType,
        size: Long?,
        versionStamp: String? = null
    ): String {
        val id = src.raw
        val versionPart = versionStamp ?: "size:$size"
        val key = "${contentType.name}:${id}:${versionPart}"
        return HashUtils.sha256(key)
    }

    fun resolveCacheFile(contentType: ContentType, cacheKey: String, extName: String): File =
        cacheRoot.resolve(contentType.name.lowercase()).resolve(cacheKey).resolve("content.$extName")

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Throwable) {
        }
    }

    /** 未知长度按块扩容超预算的内部信号；在 catch 中映射为既有类型化 AppError。 */
    private class CacheBudgetException(val result: EvictionResult) :
        Exception("cache budget exceeded: $result")

    private fun Throwable.asAppError(): Throwable = when (this) {
        is CacheBudgetException -> result.toAppError()
        else -> this
    }

    private fun EvictionResult.toAppError(): AppError = when (this) {
        EvictionResult.EXCEEDS_LIMIT -> AppError.CacheLimitExceeded
        EvictionResult.CANNOT_FREE -> AppError.CacheBusy
        // 调用方仅在非 READY 时映射，此处仅为穷尽分支
        EvictionResult.READY -> AppError.IoError("unexpected eviction result READY", null)
    }

    private fun copyLockFor(cacheKey: String): Mutex =
        copyLocks[(cacheKey.hashCode() and Int.MAX_VALUE) % copyLocks.size]

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val COPY_LOCK_STRIPES = 64
    }
}
