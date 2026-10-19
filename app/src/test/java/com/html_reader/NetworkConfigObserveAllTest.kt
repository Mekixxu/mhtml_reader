package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.database.AppDatabase
import core.database.dao.NetworkConfigDao
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkConfigObserveAllTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NetworkConfigDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.networkConfigDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeAll_returnsEveryConfigNotJustLegacyFirstPage() = runBlocking {
        repeat(101) { index ->
            dao.insert(
                NetworkConfigEntity(
                    name = "config-$index",
                    protocol = NetworkProtocol.FTP,
                    host = "host-$index",
                    port = 21,
                    username = "user-$index",
                    password = "",
                    defaultPath = "/"
                )
            )
        }

        val configs = withTimeout(5_000) { dao.observeAll().first() }

        assertEquals(101, configs.size)
    }
}
