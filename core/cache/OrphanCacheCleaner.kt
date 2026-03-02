package core.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动清理, 仅限缓存分区目录
 */
class OrphanCacheCleaner(
    private val cacheRoot: File,
    private val daysUnused: Int = 3
) {
    suspend fun clean() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoff = now - daysUnused * 86400 * 1000L
        cacheRoot.listFiles()?.forEach { typeDir ->
            if (typeDir.isDirectory) {
                typeDir.listFiles()?.forEach { dir ->
                    if (dir.isDirectory && dir.listFiles()?.isEmpty() == false && dir.lastModified() < cutoff) {
                        dir.deleteRecursively()
                    }
                }
            }
        }
    }
}
