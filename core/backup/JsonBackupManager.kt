package core.backup

import core.backup.model.*
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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private data class PreparedFavoriteImport(
    val folderPlan: FavoriteImportPlan,
    val fileDtos: List<FavoriteDto>
)

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

            // Decode, validation and CPU-only planning finish before the database transaction starts.
            val preparedFavorites = prepareFavorites(bundle)
            coroutineContext.ensureActive()
            importPreparedBundle(bundle, preparedFavorites, clearBeforeImport)

            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importPreparedBundle(
        bundle: ExportBundle,
        preparedFavorites: PreparedFavoriteImport,
        clearBeforeImport: Boolean
    ) {
        transactionRunner.run {
            if (clearBeforeImport) clearEverything()
            importFavorites(preparedFavorites)
            importHistory(bundle.history)
            importNetworkConfigs(bundle.networkConfigs)
            importTitleCache(bundle.titleCache)
        }
    }

    private suspend fun importHistory(history: List<HistoryDto>) {
        history.forEach { dto ->
            coroutineContext.ensureActive()
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
    }

    private suspend fun importNetworkConfigs(networkConfigs: List<NetworkConfigDto>) {
        networkConfigs.forEach { dto ->
            coroutineContext.ensureActive()
            networkRepo.add(
                NetworkConfigEntity(
                    id = 0L,
                    name = dto.name,
                    protocol = fromSafeNetworkProtocol(dto.protocol),
                    host = dto.host,
                    port = dto.port,
                    username = dto.username,
                    password = dto.password,
                    defaultPath = dto.defaultPath,
                    encoding = dto.encoding.ifBlank { "Auto" }
                )
            )
        }
    }

    private suspend fun importTitleCache(titleCache: List<TitleCacheDto>) {
        titleCache.forEach { dto ->
            coroutineContext.ensureActive()
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

    private suspend fun prepareFavorites(bundle: ExportBundle): PreparedFavoriteImport {
        coroutineContext.ensureActive()
        validateBundle(bundle)
        val folders = ArrayList<FavoriteDto>()
        val files = ArrayList<FavoriteDto>()
        bundle.favorites.forEach { dto ->
            coroutineContext.ensureActive()
            if (dto.type == FavoriteType.FOLDER.name) folders += dto else files += dto
        }
        return PreparedFavoriteImport(
            folderPlan = FavoriteImportPlanner().plan(folders),
            fileDtos = files
        )
    }

    private suspend fun validateBundle(bundle: ExportBundle) {
        require(bundle.favorites.size <= MAX_RECORDS_PER_TABLE) { "Too many favorites" }
        require(bundle.history.size <= MAX_RECORDS_PER_TABLE) { "Too many history records" }
        require(bundle.networkConfigs.size <= MAX_RECORDS_PER_TABLE) { "Too many network configs" }
        require(bundle.titleCache.size <= MAX_RECORDS_PER_TABLE) { "Too many title cache records" }

        validateFavorites(bundle.favorites)
        validateHistory(bundle.history)
        validateNetworkConfigs(bundle.networkConfigs)
        validateTitleCache(bundle.titleCache)
    }

    private suspend fun validateFavorites(favorites: List<FavoriteDto>) {
        val favoriteIds = mutableSetOf<Long>()
        favorites.forEach { dto ->
            coroutineContext.ensureActive()
            require(dto.type == FavoriteType.FOLDER.name || dto.type == FavoriteType.FILE.name) {
                "Unknown favorite type: ${dto.type}"
            }
            require(runCatching { core.database.entity.enums.SourceType.valueOf(dto.sourceType) }.isSuccess) {
                "Unknown sourceType: ${dto.sourceType}"
            }
            require(dto.name.length <= MAX_STRING_LENGTH) { "Favorite name too long" }
            require(dto.path.length <= MAX_STRING_LENGTH) { "Favorite path too long" }
            require(dto.createdAt >= 0L) { "Favorite createdAt invalid" }
            dto.id?.let { id ->
                require(id > 0L) { "Favorite id must be positive" }
                require(favoriteIds.add(id)) { "Duplicate favorite id: $id" }
            }
            require(dto.parentId == null || dto.parentId > 0L) { "Favorite parentId invalid" }
        }
    }

    private suspend fun validateHistory(history: List<core.backup.model.HistoryDto>) {
        history.forEach { dto ->
            coroutineContext.ensureActive()
            require(dto.progress.isFinite() && dto.progress in 0f..1f) { "Invalid history progress" }
            require(dto.pageIndex >= -1) { "Invalid history pageIndex" }
            require(runCatching { core.database.entity.enums.FileType.valueOf(dto.fileType) }.isSuccess) {
                "Unknown fileType: ${dto.fileType}"
            }
            require(dto.path.length <= MAX_STRING_LENGTH) { "History path too long" }
            require(dto.title.length <= MAX_STRING_LENGTH) { "History title too long" }
            require(dto.lastAccess >= 0L) { "History lastAccess invalid" }
        }
    }

    private suspend fun validateNetworkConfigs(networkConfigs: List<core.backup.model.NetworkConfigDto>) {
        networkConfigs.forEach { dto ->
            coroutineContext.ensureActive()
            require(dto.protocol == "SMB" || dto.protocol == "FTP") { "Unknown network protocol" }
            require(dto.port in 1..65535) { "Invalid network port" }
            require(dto.host.isNotBlank()) { "Network host is blank" }
            require(dto.name.length <= MAX_STRING_LENGTH) { "Network name too long" }
            require(dto.host.length <= MAX_STRING_LENGTH) { "Network host too long" }
            require(dto.username.length <= MAX_STRING_LENGTH) { "Network username too long" }
            require(dto.password.length <= MAX_STRING_LENGTH) { "Network password too long" }
            require(dto.defaultPath.length <= MAX_STRING_LENGTH) { "Network defaultPath too long" }
        }
    }

    private suspend fun validateTitleCache(titleCache: List<core.backup.model.TitleCacheDto>) {
        titleCache.forEach { dto ->
            coroutineContext.ensureActive()
            require(dto.path.length <= MAX_STRING_LENGTH) { "Title cache path too long" }
            require(dto.title.length <= MAX_STRING_LENGTH) { "Title cache title too long" }
        }
    }

    private suspend fun importFavorites(prepared: PreparedFavoriteImport) {
        val idMap = mutableMapOf<Long, Long>()
        prepared.folderPlan.nodes.forEach { node ->
            coroutineContext.ensureActive()
            val parentNewId = node.parentOldId?.let { idMap[it] }
            val newId = restoreFavorite(node.dto, FavoriteType.FOLDER, parentNewId)
            node.dto.id?.let { idMap[it] = newId }
        }

        prepared.fileDtos.forEach { dto ->
            coroutineContext.ensureActive()
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

    private companion object {
        const val MAX_RECORDS_PER_TABLE = 100_000
        const val MAX_STRING_LENGTH = 16_384
    }
}

