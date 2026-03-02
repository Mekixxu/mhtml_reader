package core.cache

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * tabId与cacheKey关系仅为内存映射，不持久化
 * 若需多端/进程安全/持久化，请用Room/KV数据库抽象替换
 */
class TabCacheRegistry(
    private val cacheRoot: File
) {
    private val tabToCacheKey = ConcurrentHashMap<String, Pair<String, String>>() // Pair<contentType, cacheKey>

    fun bind(tabId: String, contentType: String, cacheKey: String) {
        tabToCacheKey[tabId] = Pair(contentType, cacheKey)
    }

    suspend fun onTabClosed(tabId: String) {
        val pair = tabToCacheKey.remove(tabId) ?: return
        val (contentType, cacheKey) = pair
        val cacheDir = cacheRoot.resolve(contentType).resolve(cacheKey)
        withContext(Dispatchers.IO) {
            cacheDir.deleteRecursively()
        }
    }
}
