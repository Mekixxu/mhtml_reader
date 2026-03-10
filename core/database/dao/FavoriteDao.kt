package core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FavoriteEntity): Long

    @Update
    suspend fun update(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FavoriteEntity?

    /**
     * Room/SQLite 对 nullable 参数的相等判断要显式写 OR 分支。
     */
    @Query(
        """
        SELECT * FROM favorites
        WHERE (:parentId IS NULL AND parentId IS NULL) OR (parentId = :parentId)
        ORDER BY createdAt ASC
        """
    )
    fun observeChildren(parentId: Long?): Flow<List<FavoriteEntity>>

    @Query(
        """
        SELECT * FROM favorites
        WHERE (:parentId IS NULL AND parentId IS NULL) OR (parentId = :parentId)
        ORDER BY createdAt ASC
        """
    )
    suspend fun getChildren(parentId: Long?): List<FavoriteEntity>

    @Query("UPDATE favorites SET parentId = :newParentId WHERE id = :id")
    suspend fun updateParent(id: Long, newParentId: Long?)

    @Query("UPDATE favorites SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Query("DELETE FROM favorites WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 迭代 BFS 收集整棵子树 id，一次性分批删除，避免：
     * 1) 递归 stack overflow
     * 2) 默认 limit 导致删不全（原实现隐含 limit=100）
     * 3) N 次 delete 造成性能问题
     */
    @Transaction
    suspend fun deleteSubtree(rootId: Long) {
        val toVisit = ArrayDeque<Long>()
        val allIds = ArrayList<Long>(128)

        toVisit.add(rootId)

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeFirst()
            allIds.add(current)

            val children = getChildren(current) // 无分页版
            children.forEach { toVisit.add(it.id) }
        }

        // Room/SQLite 对 IN 子句有长度限制，分批删除更安全
        val chunkSize = 500
        var i = 0
        while (i < allIds.size) {
            val sub = allIds.subList(i, minOf(i + chunkSize, allIds.size))
            deleteByIds(sub)
            i += chunkSize
        }
    }
}

