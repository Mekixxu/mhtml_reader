package com.html_reader

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferCacheCleanerTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cleanRemovesOldTransferFilesAndKeepsFreshOnes() = runBlocking {
        val cacheDir = temporaryFolder.root
        val ftpDir = File(cacheDir, "ftp_open").apply { mkdirs() }
        val smbDir = File(cacheDir, "smb_open").apply { mkdirs() }

        val oldFtp = File(ftpDir, "old.mhtml").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - 4 * 86_400_000L)
        }
        val freshFtp = File(ftpDir, "fresh.mhtml").apply { writeText("fresh") }
        val oldSmb = File(smbDir, "old.pdf").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - 5 * 86_400_000L)
        }

        TransferCacheCleaner.clean(cacheDir = cacheDir, daysUnused = 3)

        assertFalse(oldFtp.exists())
        assertFalse(oldSmb.exists())
        assertTrue(freshFtp.exists())
    }
}
