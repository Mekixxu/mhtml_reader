package core.work.di

import android.content.Context
import core.work.scheduler.DefaultWorkScheduler
import core.work.scheduler.WorkScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 提供周期任务调度实例（WorkScheduler）。
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    @Provides @Singleton
    fun provideWorkScheduler(context: Context): WorkScheduler = DefaultWorkScheduler(context)
}
