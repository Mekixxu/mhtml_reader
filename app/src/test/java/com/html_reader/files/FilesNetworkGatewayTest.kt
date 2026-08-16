package com.html_reader.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FilesNetworkGatewayTest {

    @Test
    fun normalizeFtpPath_handlesMissingLeadingSlashAndDuplicateSlashes() {
        assertEquals("/docs/a.txt", FilesNetworkGateway.normalizeFtpPath("docs//a.txt"))
        assertEquals("/", FilesNetworkGateway.normalizeFtpPath("  "))
    }

    @Test
    fun joinFtpPath_handlesParentAndChildSlashCombinations() {
        assertEquals("/docs/a.txt", FilesNetworkGateway.joinFtpPath("/docs/", "/a.txt"))
        assertEquals("/docs/a.txt", FilesNetworkGateway.joinFtpPath("/docs", "a.txt"))
    }

    @Test
    fun ftpParentPath_handlesRootAndLeaf() {
        assertEquals("/docs", FilesNetworkGateway.ftpParentPath("/docs/a.txt"))
        assertEquals("/", FilesNetworkGateway.ftpParentPath("/a.txt"))
        assertEquals("/", FilesNetworkGateway.ftpParentPath("/"))
    }

    @Test
    fun joinSmbPath_handlesParentAndChildSlashCombinations() {
        assertEquals("/share/a.txt", FilesNetworkGateway.joinSmbPath("/share/", "/a.txt"))
        assertEquals("/share/a.txt", FilesNetworkGateway.joinSmbPath("/share", "a.txt"))
    }

    @Test
    fun smbParentPath_handlesRootAndLeaf() {
        assertEquals("/share", FilesNetworkGateway.smbParentPath("/share/a.txt"))
        assertEquals("/", FilesNetworkGateway.smbParentPath("/a.txt"))
        assertEquals("/", FilesNetworkGateway.smbParentPath("/"))
    }
}
