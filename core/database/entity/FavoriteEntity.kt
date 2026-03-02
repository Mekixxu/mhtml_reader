package core.database.entity

import androidx.room.*
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.SourceType

@Entity(
    tableName = "favorites",
    indices = [Index("parentId"), Index(value = ["parentId", "name"], unique = false)],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val type: FavoriteType,
    val path: String,
    val sourceType: SourceType,
    val createdAt: Long
)
