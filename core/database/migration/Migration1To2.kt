package core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room数据库迁移1->2，新增folder_sessions表。实际内容仅占位，需补上生产DDL。
 */
val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS folder_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                rootPath TEXT NOT NULL,
                currentPath TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                lastAccess INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
