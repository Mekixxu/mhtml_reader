package core.session.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 持久化的目录会话，每个会话独立管理其根目录与当前路径。
 */
@Entity(tableName = "folder_sessions")
data class FolderSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val rootPath: String,
    val currentPath: String,
    val createdAt: Long,
    val lastAccess: Long
)
