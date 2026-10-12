package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesTransferIdentityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun transferIdentityKey_containsServerPortUserEncodingButNotPassword() {
        val config = ftpConfig(
            host = "example.com",
            port = 21,
            username = "alice",
            password = "super-secret",
            encoding = "GBK"
        )

        val key = FilesTransferGateway.transferIdentityKey("ftp", config, "/pub/doc.mhtml")

        assertTrue(key.contains("example.com"))
        assertTrue(key.contains("21"))
        assertTrue(key.contains("alice"))
        assertTrue(key.contains("GBK"))
        assertTrue(key.contains("/pub/doc.mhtml"))
        assertFalse("身份键不得包含密码", key.contains("super-secret"))
    }

    @Test
    fun transferIdentityKey_differsWhenPortUserOrEncodingChanges() {
        val base = ftpConfig(port = 21, username = "alice", encoding = "Auto")
        val otherPort = ftpConfig(port = 2121, username = "alice", encoding = "Auto")
        val otherUser = ftpConfig(port = 21, username = "bob", encoding = "Auto")
        val otherEncoding = ftpConfig(port = 21, username = "alice", encoding = "GBK")

        val baseKey = FilesTransferGateway.transferIdentityKey("ftp", base, "/pub/a.mhtml")

        assertNotEquals(baseKey, FilesTransferGateway.transferIdentityKey("ftp", otherPort, "/pub/a.mhtml"))
        assertNotEquals(baseKey, FilesTransferGateway.transferIdentityKey("ftp", otherUser, "/pub/a.mhtml"))
        assertNotEquals(baseKey, FilesTransferGateway.transferIdentityKey("ftp", otherEncoding, "/pub/a.mhtml"))
    }

    @Test
    fun createUniqueCacheFile_differentIdentityKeysDoNotShareTarget() {
        val dir = tempFolder.newFolder("ftp_open")
        val first = ftpConfig(port = 21, username = "alice")
        val second = ftpConfig(port = 2121, username = "alice")

        val firstTarget = FilesNetworkGateway.createUniqueCacheFile(
            baseDir = dir,
            displayName = "doc.mhtml",
            uniquenessKey = FilesTransferGateway.transferIdentityKey("ftp", first, "/pub/doc.mhtml")
        )
        val secondTarget = FilesNetworkGateway.createUniqueCacheFile(
            baseDir = dir,
            displayName = "doc.mhtml",
            uniquenessKey = FilesTransferGateway.transferIdentityKey("ftp", second, "/pub/doc.mhtml")
        )

        assertNotEquals(firstTarget.name, secondTarget.name)
        assertEquals(firstTarget.parentFile, dir)
    }

    private fun ftpConfig(
        host: String = "example.com",
        port: Int = 21,
        username: String = "alice",
        password: String = "secret",
        encoding: String = "Auto"
    ) = NetworkConfigEntity(
        id = 0L,
        name = "config",
        protocol = NetworkProtocol.FTP,
        host = host,
        port = port,
        username = username,
        password = password,
        defaultPath = "/",
        encoding = encoding
    )
}
