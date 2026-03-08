package core.reader.tab

import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.cache.model.ContentType
import core.common.AppError
import core.common.DispatcherProvider
import core.data.repo.HistoryRepository
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.model.ReaderTab
import core.reader.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Tab 管理：
 * - openNewTab：统一走 CacheOpenManager.openToCache（复制到缓存后才可读）
 * - closeTab：立即触发 TabCacheRegistry 清理
 * - 进度恢复：从 history 精确读取对应 path 的记录（禁止 getRecent(1) 这种错误逻辑）
 */
class DefaultReaderTabManager(
    private val cacheOpenManager: CacheOpenManager,
    private val tabCacheRegistry: TabCacheRegistry,
    private val historyRepo: HistoryRepository,
    private val dispatcherProvider: DispatcherProvider
) : ReaderTabManager {

    private val _tabs = kotlinx.coroutines.flow.MutableStateFlow<List<ReaderTab>>(emptyList())
    override fun observeTabs(): kotlinx.coroutines.flow.StateFlow<List<ReaderTab>> = _tabs

    private val tabStates = LinkedHashMap<String, ReaderTab>()

    override fun openNewTab(request: OpenRequest): Flow<OpenState> = flow {
        // 0) 检查是否已存在相同 source path 的 tab，若有则直接复用
        val existingTab = tabStates.values.firstOrNull { it.sourcePathRaw == request.source.raw }
        if (existingTab != null) {
            emit(OpenState.Ready(existingTab))
            return@flow
        }

        emit(OpenState.Loading)

        val tabId = UUID.randomUUID().toString()
        val historyKey = request.source.raw

        val (contentType, extName) = inferContentTypeAndExt(request.fileType, request.fileName)

        val totalBytes = when (request.source) {
            is core.vfs.model.VfsPath.LocalFile -> File(request.source.filePath).takeIf { it.exists() }?.length() ?: 0L
            else -> 0L
        }
        var copyFailure: Throwable? = null

        // 1) 拷贝到缓存（并把拷贝进度转发出去）
        cacheOpenManager.openToCache(
            src = request.source,
            totalBytes = totalBytes,
            contentType = contentType,
            extName = extName
        ).collect { result ->
            result.fold(
                onSuccess = { progress ->
                    emit(OpenState.Copying(progress.copiedBytes, progress.totalBytes))
                },
                onFailure = { t ->
                    copyFailure = t
                }
            )
        }
        copyFailure?.let { t ->
            emit(OpenState.Error(AppError.IoError(t.message ?: "copy to cache failed", t)))
            return@flow
        }

        val cacheKey = cacheOpenManager.generateCacheKey(request.source, contentType, totalBytes)
        val cacheFile = cacheOpenManager.resolveCacheFile(
            contentType = contentType,
            cacheKey = cacheKey,
            extName = extName ?: contentType.defaultExt()
        )
        val cacheFilePath = cacheFile.path
        if (!cacheFile.exists()) {
            emit(OpenState.Error(AppError.IoError("cache file not resolved", null)))
            return@flow
        }

        // 2) 恢复进度（需精确按 path 查）
        val position = withContext(dispatcherProvider.io) {
            val hist = historyRepo.getByPath(historyKey) // 需要你在 repo 增加该方法（见后面补丁）
            ReadingPosition(
                progress = hist?.progress ?: 0f,
                pageIndex = hist?.pageIndex ?: -1
            )
        }

        // 3) 建 tab + 绑定缓存清理
        val tab = ReaderTab(
            tabId = tabId,
            sourcePathRaw = historyKey,
            fileType = request.fileType,
            cacheKey = cacheKey,
            cacheFilePath = cacheFilePath,
            title = request.fileName,
            lastKnownProgress = position.progress.coerceIn(0f, 1f),
            lastKnownPageIndex = position.pageIndex
        )

        tabStates[tabId] = tab
        _tabs.value = tabStates.values.toList()

        // bind：以 cacheKey 为准；若你包1 TabCacheRegistry 是 bind(tabId, cacheKey)
        tabCacheRegistry.bind(tabId, contentType.name.lowercase(), cacheKey)

        emit(OpenState.Ready(tab))

        // 4) history：recordOpen 不应强行覆盖旧进度；建议仅 upsert title/fileType/lastAccess
        historyRepo.recordOpen(historyKey, request.fileType, request.fileName)
    }.flowOn(dispatcherProvider.io)

    override suspend fun closeTab(tabId: String) {
        withContext(dispatcherProvider.io) {
            tabCacheRegistry.onTabClosed(tabId)
        }
        tabStates.remove(tabId)
        _tabs.value = tabStates.values.toList()
    }

    override suspend fun closeAll() {
        val ids = tabStates.keys.toList()
        ids.forEach { closeTab(it) }
    }

    override suspend fun switchTo(tabId: String) {
        // 仅 UI/VM 层切换当前 tab；数据层无需动作
    }

    private fun inferContentTypeAndExt(fileType: FileType, fileName: String): Pair<ContentType, String?> {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase().takeIf { it.isNotBlank() }
        val ct = when (fileType) {
            FileType.PDF -> ContentType.PDF
            FileType.MHTML -> ContentType.MHTML
            FileType.HTML -> ContentType.HTML
            else -> ContentType.WEB
        }
        return ct to ext
    }

    private fun ContentType.defaultExt(): String = when (this) {
        ContentType.PDF -> "pdf"
        ContentType.MHTML -> "mhtml"
        ContentType.HTML -> "html"
        ContentType.WEB -> "web"
        ContentType.UNKNOWN -> "tmp"
    }
}

