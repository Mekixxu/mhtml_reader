package core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import core.database.converter.RoomConverters
import core.database.dao.*
import core.database.entity.*
import core.session.entity.FolderSessionEntity

/**
 * Room schema升级，加入folder_sessions，version+1
 */
@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        NetworkConfigEntity::class,
        TitleCacheEntity::class,
        FolderSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun networkConfigDao(): NetworkConfigDao
    abstract fun titleCacheDao(): TitleCacheDao
    abstract fun folderSessionDao(): core.session.dao.FolderSessionDao
}
