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

    /**
     * 仅当 password 仍等于迁移读取到的旧值时更新 password，避免覆盖用户新密码或其他列。
     */
    @Query("UPDATE network_configs SET password = :encrypted WHERE id = :id AND password = :expectedStored")
    suspend fun updatePasswordIfUnchanged(id: Long, expectedStored: String, encrypted: String): Int
    @Query("SELECT * FROM network_configs ORDER BY id ASC")
    fun observeAll(): Flow<List<NetworkConfigEntity>>
    @Query("SELECT * FROM network_configs LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int = 100, offset: Int = 0): List<NetworkConfigEntity>
    @Query("SELECT * FROM network_configs WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getAfterId(afterId: Long, limit: Int): List<NetworkConfigEntity>
    @Query("SELECT * FROM network_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NetworkConfigEntity?
    @Query("DELETE FROM network_configs")
    suspend fun clearAll()
}
