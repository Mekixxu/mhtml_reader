package core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "title_cache",
    indices = [Index("updatedAt")]
)
data class TitleCacheEntity(
    @PrimaryKey val path: String,
    val title: String,
    val lastModified: Long,
    val updatedAt: Long
)
