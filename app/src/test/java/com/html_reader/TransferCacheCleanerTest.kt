package com.html_reader

import com.html_reader.files.FilesTransferGateway
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferCacheCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun clean_deletesStalePartResidueAndAgedFiles_only() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache")
        val ftpDir = File(cacheDir, "ftp_open").apply { mkdirs() }
        val smbDir = File(cacheDir, "smb_open").apply { mkdirs() }
        val now = System.currentTimeMillis()
        // 陈旧 .part 残留（2 小时前）→ 删除
        val stalePart = File(ftpDir, "doc.mhtml.part-uuid").apply {
            writeText("half")
            setLastModified(now - 2 * 60 * 60 * 1000L)
        }
        // 新近 .part（正在写的传输）→ 保留
        val freshPart = File(ftpDir, "doc2.mhtml.part-uuid").apply {
            writeText("writing")
            setLastModified(now)
        }
        // 未到期正常文件 → 保留
        val freshTarget = File(ftpDir, "doc3_abc.mhtml").apply {
            writeText("ok")
            setLastModified(now)
        }
        // 到期正常文件（N 天前）→ 删除（ftp 与 smb 两个分区均覆盖）
        val agedTarget = File(ftpDir, "doc4_abc.mhtml").apply {
            writeText("old")
            setLastModified(now - 30L * 24 * 60 * 60 * 1000)
        }
        val agedSmbTarget = File(smbDir, "doc5_abc.pdf").apply {
            writeText("old")
            setLastModified(now - 30L * 24 * 60 * 60 * 1000)
        }

        TransferCacheCleaner.clean(cacheDir, daysUnused = 7)

        assertFalse(stalePart.exists())
        assertFalse(agedTarget.exists())
        assertFalse(agedSmbTarget.exists())
        assertTrue(freshPart.exists())
        assertTrue(freshTarget.exists())
    }

    @Test
    fun findReusableTarget_matchesOnlyExistingFileWithExpectedSize() {
        val dir = tempFolder.newFolder("ftp_open")
        val target = File(dir, "doc_abc.mhtml").apply { writeText("12345") }

        assertSame(target, FilesTransferGateway.findReusableTarget(target, expectedBytes = 5L))
        assertNull("大小不一致应重新下载", FilesTransferGateway.findReusableTarget(target, expectedBytes = 6L))
        assertNull("远端大小未知应重新下载", FilesTransferGateway.findReusableTarget(target, expectedBytes = 0L))
        assertNull("目标不存在应重新下载", FilesTransferGateway.findReusableTarget(File(dir, "missing.mhtml"), 5L))
        assertEquals(5L, target.length())
    }
}
