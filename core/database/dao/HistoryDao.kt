package core.database.dao

import androidx.room.*
import core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)
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
            SELECT path FROM history ORDER BY lastAccess ASC LIMIT (SELECT COUNT(*) FROM history) - :keep
        )
    """)
    suspend fun deleteOldest(keep: Int)
    @Query("SELECT * FROM history")
    suspend fun getAll(): List<HistoryEntity>
}
