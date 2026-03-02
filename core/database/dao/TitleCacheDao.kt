package core.database.dao

import androidx.room.*
import core.database.entity.TitleCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TitleCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TitleCacheEntity)
    @Query("SELECT * FROM title_cache WHERE path = :path LIMIT 1")
    suspend fun get(path: String): TitleCacheEntity?
    @Query("SELECT * FROM title_cache WHERE path = :path LIMIT 1")
    fun observe(path: String): Flow<TitleCacheEntity?>
    @Query("DELETE FROM title_cache WHERE path = :path")
    suspend fun delete(path: String)
    @Query("DELETE FROM title_cache")
    suspend fun clearAll()
    @Query("DELETE FROM title_cache WHERE updatedAt < :updatedAtEpochMs")
    suspend fun deleteOlderThan(updatedAtEpochMs: Long)
    @Query("SELECT * FROM title_cache")
    suspend fun getAll(): List<TitleCacheEntity>
}
