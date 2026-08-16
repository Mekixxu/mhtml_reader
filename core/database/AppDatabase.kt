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
    version = 4,
    exportSchema = false
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

        /**
         * v3 -> v4：folder_sessions 增加 sourceType/networkConfigId/sortOption 三列。
         * 必须与 MIGRATION_1_2 的 v2 表结构配合使用（1->2 建旧表，2->3 加 encoding，3->4 补列）。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE folder_sessions ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'LOCAL'")
                database.execSQL("ALTER TABLE folder_sessions ADD COLUMN networkConfigId TEXT")
                database.execSQL("ALTER TABLE folder_sessions ADD COLUMN sortOption INTEGER NOT NULL DEFAULT 2")
            }
        }
    }
}
