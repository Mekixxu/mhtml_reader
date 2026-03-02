package core.database.entity

import androidx.room.*
import core.database.entity.enums.FileType

@Entity(
    tableName = "history",
    indices = [Index("lastAccess")]
)
data class HistoryEntity(
    @PrimaryKey val path: String,
    val title: String,
    val lastAccess: Long,
    val progress: Float,
    val pageIndex: Int,
    val fileType: FileType
)
