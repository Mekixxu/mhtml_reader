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
            username = "alice",
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
    fun missingHost_isInvalidPath() {
        val result = FilesNetworkOpenResolver.resolve(
            path = "not-a-url",
            sourceType = SourceType.FTP,
            favoriteType = FavoriteType.FILE,
            networkConfigs = emptyList()
        )

        assertEquals(NetworkOpenIssue.INVALID_PATH, result.issue)
    }
}
