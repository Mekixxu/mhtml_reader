package core.reader.tab

import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.cache.model.ContentType
import core.cache.model.CopyProgress
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
import kotlinx.coroutines.withContext
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
        emit(OpenState.Loading)

        val tabId = UUID.randomUUID().toString()
        val historyKey = request.source.raw

        val (contentType, extName) = inferContentTypeAndExt(request.fileType, request.fileName)

        var cacheKey: String? = null
        var cacheFilePath: String? = null
        var totalBytes: Long = 0L

        // 1) 拷贝到缓存（并把拷贝进度转发出去）
        cacheOpenManager.openToCache(
            src = request.source,
            totalBytes = request.source.let { /* 若 OpenRequest 无 size，只能先传 0；强烈建议 OpenRequest 增加 totalBytes */ 0L },
            contentType = contentType,
            extName = extName
        ).collect { result ->
            result.fold(
                onSuccess = { progress ->
                    totalBytes = progress.totalBytes
                    emit(OpenState.Copying(progress.copiedBytes, progress.totalBytes))

                    // 从 progress 中抽取 cacheKey/cacheFilePath（你需要按 CopyProgress 实际字段实现）
                    val info = extractCacheInfo(progress)
                    cacheKey = info.cacheKey ?: cacheKey
                    cacheFilePath = info.cacheFilePath ?: cacheFilePath
                },
                onFailure = { t ->
                    emit(OpenState.Error(AppError.IoError(t.message ?: "copy to cache failed", t)))
                    return@collect
                }
            )
        }

        // 必须拿到 cacheFilePath 才能读（否则 Reader 无法加载）
        if (cacheFilePath.isNullOrBlank()) {
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
        if (!cacheKey.isNullOrBlank()) {
            tabCacheRegistry.bind(tabId, cacheKey!!)
        }

        emit(OpenState.Ready(tab))

        // 4) history：recordOpen 不应强行覆盖旧进度；建议仅 upsert title/fileType/lastAccess
        historyRepo.recordOpen(historyKey, request.fileType, request.fileName)
    }

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

    private data class CacheInfo(val cacheKey: String?, val cacheFilePath: String?)

    /**
     * 你必须按 CopyProgress 的真实字段实现：
     * - 若 progress 携带 cacheKey/cacheFile/cachePath，直接提取
     * - 若仅携带 cacheKey，需要你们提供从 key -> filePath 的解析函数
     */
    private fun extractCacheInfo(progress: CopyProgress): CacheInfo {
        // TODO: 根据你们 core/cache/model/CopyProgress 实际字段填写
        // 示例：
        // return CacheInfo(cacheKey = progress.cacheKey, cacheFilePath = progress.cacheFile?.path)
        return CacheInfo(cacheKey = null, cacheFilePath = null)
    }

    private fun inferContentTypeAndExt(fileType: FileType, fileName: String): Pair<ContentType, String?> {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase().takeIf { it.isNotBlank() }
        val ct = when (fileType) {
            FileType.PDF -> ContentType.PDF
            FileType.HTML, FileType.MHTML -> ContentType.WEB
            else -> ContentType.WEB
        }
        return ct to ext
    }
}

