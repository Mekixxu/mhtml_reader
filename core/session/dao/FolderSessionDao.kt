package core.session.dao

import androidx.room.*
import core.session.entity.FolderSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderSessionDao {
    @Insert
    suspend fun insert(entity: FolderSessionEntity): Long

    @Update
    suspend fun update(entity: FolderSessionEntity)

    @Query("SELECT * FROM folder_sessions ORDER BY lastAccess DESC")
    fun observeAll(): Flow<List<FolderSessionEntity>>

    @Query("SELECT * FROM folder_sessions ORDER BY lastAccess DESC")
    suspend fun getAll(): List<FolderSessionEntity>

    @Query("SELECT * FROM folder_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FolderSessionEntity?

    @Query("DELETE FROM folder_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE folder_sessions SET sortOption = :sortOption WHERE id = :id")
    suspend fun updateSortOption(id: Long, sortOption: Int)

    @Query("UPDATE folder_sessions SET currentPath = :currentPath, lastAccess = :lastAccess WHERE id = :id")
    suspend fun updateCurrentDir(id: Long, currentPath: String, lastAccess: Long)

    @Query("UPDATE folder_sessions SET lastAccess = :lastAccess WHERE id = :id")
    suspend fun touchLastAccess(id: Long, lastAccess: Long)

    @Query("DELETE FROM folder_sessions")
    suspend fun deleteAll()
}
