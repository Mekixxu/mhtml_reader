package core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移 1->2：新增 folder_sessions 表（v2 初始表结构）。
 * 后续 v3->v4 的列变更由 AppDatabase.MIGRATION_3_4 补齐。
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
