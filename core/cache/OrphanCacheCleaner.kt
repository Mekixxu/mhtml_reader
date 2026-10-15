package core.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动清理, 仅限缓存分区目录。
 * 覆盖 keyDir、typeDir 下的散落文件以及根目录散落文件；活跃 tab 的 keyDir 不清理。
 *
 * 保护检查与删除在同一保护锁内完成（经 [protection]），避免「先取 activeKeys 快照 →
 * 随后建立新保护 → 仍按过期快照删除」的竞态（Q2/Q3 维护竞态）。
 */
class OrphanCacheCleaner(
    private val cacheRoot: File,
    private val daysUnused: Int = 3,
    private val protection: CacheProtection = CacheProtection.NONE
) {
    suspend fun clean(activeCacheKeys: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - daysUnused * 86400 * 1000L
        protection.withProtectionLock {
            val protectedKeys = activeCacheKeys + protection.protectedKeys()
            cacheRoot.listFiles()?.forEach { typeEntry ->
                if (!typeEntry.isDirectory) {
                    if (typeEntry.lastModified() < cutoff) typeEntry.delete()
                    return@forEach
                }
                typeEntry.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        // 活跃 keyDir 永不清理；其余（含失败残留的空目录）超期即删
                        if (child.name !in protectedKeys && child.lastModified() < cutoff) {
                            // 保护锁内二次判定，删除前再确认无保护
                            protection.deleteCacheDirIfUnprotected(child.name, child)
                        }
                    } else if (child.lastModified() < cutoff) {
                        child.delete()
                    }
                }
            }
        }
    }
}
