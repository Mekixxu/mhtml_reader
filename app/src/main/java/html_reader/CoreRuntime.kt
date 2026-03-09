package com.html_reader

import android.content.Context
import androidx.room.Room
import core.common.DefaultDispatcherProvider
import core.common.DispatcherProvider
import core.database.AppDatabase

object CoreRuntime {
    @Volatile
    private var holder: Holder? = null

    val database: AppDatabase
        get() = holder?.database ?: error("CoreRuntime not initialized. Call ensure() first.")

    val dispatcherProvider: DispatcherProvider
        get() = holder?.dispatcherProvider ?: error("CoreRuntime not initialized. Call ensure() first.")

    fun ensure(context: Context) {
        if (holder != null) return
        synchronized(this) {
            if (holder != null) return
            val appContext = context.applicationContext
            val dispatchers = DefaultDispatcherProvider()
            val db = Room.databaseBuilder(appContext, AppDatabase::class.java, "app_database")
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
            holder = Holder(db, dispatchers)
        }
    }

    private data class Holder(
        val database: AppDatabase,
        val dispatcherProvider: DispatcherProvider
    )
}
