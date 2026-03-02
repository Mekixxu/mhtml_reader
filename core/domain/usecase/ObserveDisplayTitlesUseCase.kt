package core.domain.usecase

import core.cache.CacheOpenManager
import core.common.DispatcherProvider
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FileType
import core.data.repo.TitleCacheRepository
import core.domain.model.DisplayEntry
import core.title.TitleExtractor
import core.vfs.model.VfsEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * 把文件列表转换为“可展示标题”的列表：
 * - 首次立刻返回 displayTitle = fileName
 * - 后台提取真实 title，写入 title_cache，并持续推送更新后的列表
 *
 * 设计要点：
 * - 并发受控（Semaphore）
 * - 去重（同一路径同时只允许一个提取任务 in-flight）
 * - 可取消（上游取消 => 本 channelFlow scope 取消 => 任务停止）
 */
class ObserveDisplayTitlesUseCase(
    private val cacheOpenManager: CacheOpenManager,
    private val titleCacheRepo: TitleCacheRepository,
    private val titleExtractor: TitleExtractor,
    private val dispatcherProvider: DispatcherProvider
) {

    private val inFlight = ConcurrentHashMap<String, Boolean>() // pathRaw -> true

    fun execute(
        entries: Flow<List<VfsEntry>>,
        parallelism: Int = 2,
        maxBytesToRead: Long = 256L * 1024L
    ): Flow<List<DisplayEntry>> = channelFlow {
        val semaphore = Semaphore(parallelism.coerceAtLeast(1))
        val updates = Channel<Pair<String, String>>(capacity = Channel.BUFFERED) // (pathRaw, newTitle)

        // 汇总态：当前列表（pathRaw -> DisplayEntry）
        val current = LinkedHashMap<String, DisplayEntry>()

        // 消费更新并重发整表
        val updateJob = launch(dispatcherProvider.default) {
            for ((pathRaw, newTitle) in updates) {
                val old = current[pathRaw] ?: continue
                if (old.displayTitle == newTitle) continue
                current[pathRaw] = old.copy(displayTitle = newTitle)
                send(current.values.toList())
            }
        }

        try {
            entries.collectLatest { list ->
                // 1) 初始化 current，立刻 send（filename）
                current.clear()
                list.forEach { e ->
                    val display = DisplayEntry(
                        path = e.path,
                        fileName = e.name,
                        displayTitle = e.name,
                        lastModified = e.lastModifiedEpochMs,
                        sizeBytes = e.sizeBytes
                    )
                    current[e.path.raw] = display
                }
                send(current.values.toList())

                // 2) 针对每个 entry 启动后台任务（并发受控+去重）
                list.forEach { e ->
                    val pathRaw = e.path.raw

                    launch(dispatcherProvider.io) {
                        // 去重：同一路径只跑一个
                        if (inFlight.putIfAbsent(pathRaw, true) != null) return@launch
                        try {
                            semaphore.withPermit {
                                // 2.1 先读缓存
                                val cached = titleCacheRepo.get(pathRaw)
                                if (cached != null && cached.lastModified == e.lastModifiedEpochMs) {
                                    updates.trySend(pathRaw to cached.title)
                                    return@withPermit
                                }

                                // 2.2 打开到缓存（包1统一链路）
                                val openResult = cacheOpenManager.openToCache(e.path)
                                val fileType = guessFileType(e.name)
                                val title = titleExtractor.extractTitle(
                                    source = e.path,
                                    cacheFile = openResult.cacheFile,
                                    fileType = fileType,
                                    maxBytesToRead = maxBytesToRead
                                )?.trim()

                                if (!title.isNullOrBlank()) {
                                    titleCacheRepo.upsert(
                                        TitleCacheEntity(
                                            path = pathRaw,
                                            title = title,
                                            lastModified = e.lastModifiedEpochMs,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    updates.trySend(pathRaw to title)
                                }
                                // 2.3 可选：若 openToCache 为“标题提取”产生了临时缓存，应在此处释放
                                // 取决于你们包1的缓存生命周期 API：可在这里增加 cacheOpenManager.release(openResult.cacheKey)
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            // 标题提取失败不影响列表；继续显示文件名
                        } finally {
                            inFlight.remove(pathRaw)
                        }
                    }
                }
            }
        } finally {
            updates.close()
            updateJob.cancel()
        }
    }

    private fun guessFileType(fileName: String): FileType = when {
        fileName.endsWith(".pdf", true) -> FileType.PDF
        fileName.endsWith(".mhtml", true) || fileName.endsWith(".mht", true) -> FileType.MHTML
        fileName.endsWith(".html", true) || fileName.endsWith(".htm", true) -> FileType.HTML
        else -> FileType.WEB
    }
}

