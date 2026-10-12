package core.backup

import core.backup.model.*
import core.common.AppError
import core.common.UrlCredentialSanitizer
import core.data.repo.FavoritesRepository
import core.data.repo.HistoryRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.entity.NetworkConfigEntity
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.fromSafeFileType
import core.database.entity.enums.fromSafeNetworkProtocol
import core.database.entity.enums.fromSafeSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON 导入导出（仅元数据）。
 * v1.0 默认：全量清空后导入。
 *
 * 关键修复点：
 * - Favorites 导入必须做 oldId -> newId 映射，否则 parentId 会错乱
 * - History 导入必须保留 lastAccess/progress/pageIndex，而不是当成“刚打开”
 */
class JsonBackupManager(
    private val favoritesRepo: FavoritesRepository,
    private val historyRepo: HistoryRepository,
    private val networkRepo: NetworkConfigRepository,
    private val titleCacheRepo: TitleCacheRepository,
    private val supportedSchemaVersion: Int = 1,
    private val transactionRunner: BackupTransactionRunner = BackupTransactionRunner.Direct
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun exportAll(): String = withContext(Dispatchers.IO) {
        // 直接全表导出（favorites 本身就是扁平表，parentId 关系由字段表达）
        val favorites = favoritesRepo.getAll()
        ExportBundle(
            schemaVersion = supportedSchemaVersion,
            exportedAt = System.currentTimeMillis(),
            favorites = favorites.map { it.toDto() },
            history = historyRepo.getAll().map { it.toDto() },
            networkConfigs = networkRepo.getAll().map { it.toDto() },
            titleCache = titleCacheRepo.getAll().map { it.toDto() }
        ).let { json.encodeToString(it) }
    }

    suspend fun importAll(
        jsonString: String,
        clearBeforeImport: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bundle = json.decodeFromString<ExportBundle>(jsonString)
            if (bundle.schemaVersion != supportedSchemaVersion) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "schemaVersion mismatch: expected $supportedSchemaVersion, found ${bundle.schemaVersion}"
                    )
                )
            }

            // 清库 + 导入整体在一个事务中，中途失败回滚，避免半清空
            transactionRunner.run {
                if (clearBeforeImport) {
                    clearEverything()
                }

                // 1) Favorites：先导入文件夹，再导入文件；全程使用 oldId -> newId 映射
                importFavorites(bundle.favorites)

                // 2) History：保留进度与时间戳
                bundle.history.forEach { dto ->
                    historyRepo.upsert(
                        core.database.entity.HistoryEntity(
                            path = UrlCredentialSanitizer.stripPassword(dto.path),
                            title = dto.title,
                            lastAccess = dto.lastAccess,
                            progress = dto.progress,
                            pageIndex = dto.pageIndex,
                            fileType = fromSafeFileType(dto.fileType)
                        )
                    )
                }

                // 3) Network configs
                bundle.networkConfigs.forEach { dto ->
                    networkRepo.add(
                        NetworkConfigEntity(
                            id = 0L,
                            name = dto.name,
                            protocol = fromSafeNetworkProtocol(dto.protocol),
                            host = dto.host,
                            port = dto.port,
                            username = dto.username,
                            password = dto.password, // 明文警告，仅 v1.0
                            defaultPath = dto.defaultPath,
                            encoding = dto.encoding.ifBlank { "Auto" }
                        )
                    )
                }

                // 4) Title cache：按原数据写入
                bundle.titleCache.forEach { dto ->
                    titleCacheRepo.upsert(
                        TitleCacheEntity(
                            path = dto.path,
                            title = dto.title,
                            lastModified = dto.lastModified,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importFavorites(favorites: List<FavoriteDto>) {
        val folderDtos = favorites.filter { it.type == FavoriteType.FOLDER.name }
        val fileDtos = favorites.filter { it.type == FavoriteType.FILE.name }

        // oldId -> newId
        val idMap = mutableMapOf<Long, Long>()

        // 分层导入 folders：循环多轮，直到不再插入（避免依赖排序）
        val pending = folderDtos.toMutableList()
        var progressMade: Boolean

        do {
            progressMade = false
            val iter = pending.iterator()
            while (iter.hasNext()) {
                val dto = iter.next()
                val oldId = dto.id
                if (oldId == null) {
                    restoreFavorite(dto, FavoriteType.FOLDER, parentId = null)
                    iter.remove()
                    progressMade = true
                    continue
                }

                val newParentId: Long? = when (val oldParent = dto.parentId) {
                    null -> null
                    else -> idMap[oldParent] // 若 parent 尚未创建则为 null（本轮不处理）
                }

                // 如果 dto 有 parentId，但 parent 还没导入，则跳过，等待下轮
                if (dto.parentId != null && newParentId == null) continue

                val newId = restoreFavorite(dto, FavoriteType.FOLDER, newParentId)
                idMap[oldId] = newId
                iter.remove()
                progressMade = true
            }
        } while (progressMade)

        // 若仍有 pending，说明存在断裂/循环 parentId；将它们挂到 root 以保证不丢数据
        pending.forEach { dto ->
            val oldId = dto.id
            val newId = restoreFavorite(dto, FavoriteType.FOLDER, parentId = null)
            if (oldId != null) idMap[oldId] = newId
        }
        pending.clear()

        // 导入 files：按 parentId 映射，否则挂 root；保留原 path/sourceType/createdAt
        fileDtos.forEach { dto ->
            val newParentId = dto.parentId?.let { idMap[it] }
            restoreFavorite(dto, FavoriteType.FILE, newParentId)
        }
    }

    /**
     * FOLDER 通过 path 是否为空区分“收藏夹容器”和“目录收藏”：
     * - path 为空：addFolder 语义的容器；
     * - path 非空：addDirectory 语义的目录收藏，必须保留 path/sourceType。
     */
    private suspend fun restoreFavorite(dto: FavoriteDto, type: FavoriteType, parentId: Long?): Long =
        favoritesRepo.restoreFavorite(
            core.database.entity.FavoriteEntity(
                parentId = parentId,
                name = dto.name,
                type = type,
                path = dto.path,
                sourceType = fromSafeSourceType(dto.sourceType),
                createdAt = dto.createdAt
            )
        )

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        // 直接清表是最稳定最快的方式（尤其 favorites 有外键 cascade 时）
        favoritesRepo.clearAll()
        historyRepo.clearAll()
        networkRepo.clearAll()
        titleCacheRepo.clearAll()
    }

    // --- dto mappers ---
    private fun core.database.entity.FavoriteEntity.toDto() = FavoriteDto(
        id = id,
        parentId = parentId,
        name = name,
        type = type.name,
        // 安全：导出前剥离 URL 中的密码
        path = UrlCredentialSanitizer.stripPassword(path),
        sourceType = sourceType.name,
        createdAt = createdAt
    )

    private fun core.database.entity.HistoryEntity.toDto() = HistoryDto(
        path = UrlCredentialSanitizer.stripPassword(path),
        title = title,
        lastAccess = lastAccess,
        progress = progress,
        pageIndex = pageIndex,
        fileType = fileType.name
    )

    private fun core.database.entity.NetworkConfigEntity.toDto() = NetworkConfigDto(
        id = id,
        name = name,
        protocol = protocol.name,
        host = host,
        port = port,
        username = username,
        // 安全：导出元数据时不再写入明文密码；导入侧会把缺失密码写为空。
        password = "",
        defaultPath = defaultPath,
        // 保留 GBK 等非 UTF-8 配置；旧备份缺失时由 DTO 默认值补 Auto。
        encoding = encoding
    )

    private fun core.database.entity.TitleCacheEntity.toDto() = TitleCacheDto(
        path = path,
        title = title,
        lastModified = lastModified,
        updatedAt = updatedAt
    )
}

