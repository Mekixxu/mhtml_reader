package core.session.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import core.database.entity.enums.SourceType

/**
 * 持久化的目录会话，每个会话独立管理其根目录与当前路径。
 */
@Entity(tableName = "folder_sessions")
data class FolderSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val rootPath: String,
    val currentPath: String,
    val sourceType: SourceType = SourceType.LOCAL,
    val networkConfigId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccess: Long = System.currentTimeMillis(),
    val sortOption: Int = 2 // 0=NameAsc, 1=NameDesc, 2=DateDesc, 3=SizeDesc. Default to DateDesc.
)
