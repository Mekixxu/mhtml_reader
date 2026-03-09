package core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun networkConfigDao(): NetworkConfigDao
    abstract fun titleCacheDao(): TitleCacheDao
    abstract fun folderSessionDao(): core.session.dao.FolderSessionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE network_configs ADD COLUMN encoding TEXT NOT NULL DEFAULT 'Auto'")
            }
        }
    }
}
