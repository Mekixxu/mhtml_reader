package core.database.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import core.database.AppDatabase
import core.database.migration.Migration1To2
import core.database.dao.FavoriteDao
import core.database.dao.HistoryDao
import core.database.dao.NetworkConfigDao
import core.database.dao.TitleCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 注意：
 * - fallbackToDestructiveMigration() 只能在 Debug/Dev 使用
 * - Release 必须提供 Migration（哪怕是空的占位，也要显式声明策略）
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).addMigrations(
            Migration1To2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )

        val isDebug = (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
    @Provides fun provideNetworkConfigDao(db: AppDatabase): NetworkConfigDao = db.networkConfigDao()
    @Provides fun provideTitleCacheDao(db: AppDatabase): TitleCacheDao = db.titleCacheDao()
}

