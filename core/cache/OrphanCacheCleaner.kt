package core.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动清理, 仅限缓存分区目录。
 * 覆盖 keyDir、typeDir 下的散落文件以及根目录散落文件；活跃 tab 的 keyDir 不清理。
 */
class OrphanCacheCleaner(
    private val cacheRoot: File,
    private val daysUnused: Int = 3
) {
    suspend fun clean(activeCacheKeys: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - daysUnused * 86400 * 1000L
        cacheRoot.listFiles()?.forEach { typeEntry ->
            if (!typeEntry.isDirectory) {
                if (typeEntry.lastModified() < cutoff) typeEntry.delete()
                return@forEach
            }
            typeEntry.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    // 活跃 keyDir 永不清理；其余（含失败残留的空目录）超期即删
                    if (child.name !in activeCacheKeys && child.lastModified() < cutoff) {
                        child.deleteRecursively()
                    }
                } else if (child.lastModified() < cutoff) {
                    child.delete()
                }
            }
        }
    }
}
