package core.database.dao

import androidx.room.*
import core.database.entity.NetworkConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkConfigDao {
    @Insert
    suspend fun insert(entity: NetworkConfigEntity): Long
    @Update
    suspend fun update(entity: NetworkConfigEntity)
    @Query("DELETE FROM network_configs WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM network_configs LIMIT :limit OFFSET :offset")
    fun observeAll(limit: Int = 100, offset: Int = 0): Flow<List<NetworkConfigEntity>>
    @Query("SELECT * FROM network_configs LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int = 100, offset: Int = 0): List<NetworkConfigEntity>
    @Query("SELECT * FROM network_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NetworkConfigEntity?
    @Query("DELETE FROM network_configs")
    suspend fun clearAll()
}
