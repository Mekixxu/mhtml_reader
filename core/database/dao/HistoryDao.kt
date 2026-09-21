package core.database.dao

import androidx.room.*
import core.database.entity.HistoryEntity
import core.database.entity.enums.FileType
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    /**
     * 记录一次打开：原子 UPSERT，冲突时只更新标题/时间/类型，保留已有阅读进度。
     * 避免 getByPath + REPLACE 的读改写竞态覆盖 updateProgress。
     */
    @Query("""
        INSERT INTO history (path, title, lastAccess, progress, pageIndex, fileType)
        VALUES (:path, :title, :lastAccess, 0.0, -1, :fileType)
        ON CONFLICT(path) DO UPDATE SET
            title = excluded.title,
            lastAccess = excluded.lastAccess,
            fileType = excluded.fileType
    """)
    suspend fun touchOpen(path: String, title: String, lastAccess: Long, fileType: FileType)

    @Query("UPDATE history SET progress = :progress, pageIndex = :pageIndex, lastAccess = :lastAccess WHERE path = :path")
    suspend fun updateProgress(path: String, progress: Float, pageIndex: Int, lastAccess: Long)
    @Query("SELECT * FROM history ORDER BY lastAccess DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(limit: Int, offset: Int = 0): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history ORDER BY lastAccess DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int = 0): List<HistoryEntity>
    @Query("SELECT * FROM history WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): HistoryEntity?
    @Query("DELETE FROM history WHERE path = :path")
    suspend fun delete(path: String)
    @Query("DELETE FROM history")
    suspend fun clearAll()
    @Query("DELETE FROM history WHERE lastAccess < :epochMs")
    suspend fun deleteOlderThan(epochMs: Long)
    @Query("""
        DELETE FROM history WHERE path IN (
            SELECT path FROM history ORDER BY lastAccess ASC LIMIT
            CASE
                WHEN (SELECT COUNT(*) FROM history) > :keep
                THEN (SELECT COUNT(*) FROM history) - :keep
                ELSE 0
            END
        )
    """)
    suspend fun deleteOldest(keep: Int)
    @Query("SELECT * FROM history")
    suspend fun getAll(): List<HistoryEntity>
}
