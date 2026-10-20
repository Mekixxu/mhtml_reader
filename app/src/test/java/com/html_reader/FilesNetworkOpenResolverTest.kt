package com.html_reader

import com.html_reader.files.FilesNetworkOpenResolver
import com.html_reader.files.NetworkOpenIssue
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilesNetworkOpenResolverTest {

    @Test
    fun favoriteWithPassword_buildsAdHocConfig() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://bob:secret@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = emptyList()
        )

        assertNotNull(result.adHocConfig)
        assertEquals("bob", result.adHocConfig!!.username)
        assertEquals("secret", result.adHocConfig!!.password)
        assertEquals("/private", result.openPath)
    }

    @Test
    fun favoriteWithoutPassword_requiresNetworkConfig() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://bob@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = emptyList()
        )

        assertNull(result.adHocConfig)
        assertEquals(NetworkOpenIssue.MISSING_CREDENTIAL, result.issue)
    }

    @Test
    fun anonymousFavorite_stillBuildsAdHocConfig() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://anonymous@host:21/public/",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FOLDER,
            networkConfigs = emptyList()
        )

        assertNull("unexpected issue=${result.issue}", result.issue)
        assertNotNull(result.adHocConfig)
        assertEquals("anonymous", result.adHocConfig!!.username)
        assertEquals("/public/", result.openPath)
    }

    @Test
    fun matchingNetworkConfig_isPreferred() {
        val existing = NetworkConfigEntity(
            id = 7L,
            name = "existing",
            protocol = NetworkProtocol.FTP,
            host = "host",
            port = 21,
            username = "bob",
            password = "",
            defaultPath = "/"
        )
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://bob@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertEquals(7L, result.configId)
        assertEquals("/private", result.openPath)
        assertNull(result.issue)
    }

    @Test
    fun matchingHostPortWithDifferentUsername_doesNotReuseConfig() {
        val existing = NetworkConfigEntity(
            id = 7L,
            name = "alice-config",
            protocol = NetworkProtocol.FTP,
            host = "host",
            port = 21,
            username = "alice",
            password = "alice-secret",
            defaultPath = "/"
        )
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://bob:secret@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertNull(result.configId)
        assertEquals("bob", result.adHocConfig?.username)
        assertEquals("/private", result.openPath)
        assertNull(result.issue)
    }

    @Test
    fun missingHost_isInvalidPath() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "not-a-url",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = emptyList()
        )

        assertEquals(NetworkOpenIssue.INVALID_PATH, result.issue)
    }

    @Test
    fun encodedPlusUsername_matchesStoredConfig() {
        val existing = ftpConfig(id = 7L, username = "a+b")
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://a%2Bb@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertEquals(7L, result.configId)
        assertNull(result.issue)
    }

    @Test
    fun encodedColonUsername_matchesStoredConfigWithoutSplittingIdentity() {
        val existing = ftpConfig(id = 7L, username = "a:b")
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://a%3Ab@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertEquals(7L, result.configId)
        assertNull(result.issue)
    }

    @Test
    fun literalPlusInAdHocUsername_isPreserved() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://a+b:secret@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = emptyList()
        )

        assertEquals("a+b", result.adHocConfig?.username)
        assertEquals("secret", result.adHocConfig?.password)
        assertNull(result.issue)
    }

    @Test
    fun ftpUsernameComparison_isCaseSensitive() {
        val configs = listOf(
            ftpConfig(id = 1L, username = "alice"),
            ftpConfig(id = 2L, username = "Alice")
        )
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://Alice@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = configs
        )

        assertEquals(2L, result.configId)
        assertNull(result.issue)
    }

    @Test
    fun smbUsernameComparison_remainsCaseInsensitive() {
        val existing = NetworkConfigEntity(
            id = 9L,
            name = "smb-config",
            protocol = NetworkProtocol.SMB,
            host = "host",
            port = 445,
            username = "alice",
            password = "",
            defaultPath = "/"
        )
        val result = FilesNetworkOpenResolver.resolve(
            path = "smb://Alice@host:445/share/a.mhtml",
            sourceType = SourceType.SMB,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertEquals(9L, result.configId)
        assertNull(result.issue)
    }


    @Test
    fun pathWithoutUserInfo_stillMatchesByEndpoint() {
        val existing = ftpConfig(id = 7L, username = "alice")
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertEquals(7L, result.configId)
        assertNull(result.issue)
    }

    @Test
    fun explicitEmptyUserInfo_doesNotFallbackToArbitraryAccount() {
        val existing = ftpConfig(id = 7L, username = "alice")
        val result = FilesNetworkOpenResolver.resolve(
            path = "ftp://@host:21/private/a.mhtml",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = listOf(existing)
        )

        assertNull(result.configId)
        assertEquals(NetworkOpenIssue.MISSING_CREDENTIAL, result.issue)
    }

    private fun ftpConfig(id: Long, username: String) = NetworkConfigEntity(
        id = id,
        name = "config-$id",
        protocol = NetworkProtocol.FTP,
        host = "host",
        port = 21,
        username = username,
        password = "",
        defaultPath = "/"
    )
}
