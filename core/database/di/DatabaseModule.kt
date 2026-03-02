package core.database.di

import android.content.Context
import androidx.room.Room
import core.database.AppDatabase
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
        )

        // 这里用最通用的方式：通过 BuildConfig.DEBUG 控制。
        // 若你的 core 模块拿不到 BuildConfig.DEBUG，请改为注入一个 AppConfig/BuildTypeProvider。
        val isDebug = try {
            // 通过反射避免模块拿不到 BuildConfig 的编译问题（可按项目情况替换）
            val clazz = Class.forName(context.packageName + ".BuildConfig")
            clazz.getField("DEBUG").getBoolean(null)
        } catch (_: Throwable) {
            false
        }

        if (isDebug) {
            builder.fallbackToDestructiveMigration()
        } else {
            // Release：不启用 destructive，等待你们后续补 Migration
            // builder.addMigrations(MIGRATION_1_2, ...)
        }

        return builder.build()
    }

    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
    @Provides fun provideNetworkConfigDao(db: AppDatabase): NetworkConfigDao = db.networkConfigDao()
    @Provides fun provideTitleCacheDao(db: AppDatabase): TitleCacheDao = db.titleCacheDao()
}

