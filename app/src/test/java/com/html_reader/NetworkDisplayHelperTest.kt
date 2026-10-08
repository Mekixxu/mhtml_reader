package com.html_reader

import com.html_reader.files.NetworkDisplayHelper
import core.database.entity.enums.NetworkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkDisplayHelperTest {

    @Test
    fun meaningfulName_protocolDuplicateOrBlank_isNull() {
        assertNull(NetworkDisplayHelper.meaningfulName("ftp", NetworkProtocol.FTP))
        assertNull(NetworkDisplayHelper.meaningfulName("FTP", NetworkProtocol.FTP))
        assertNull(NetworkDisplayHelper.meaningfulName("  ", NetworkProtocol.FTP))
        assertNull(NetworkDisplayHelper.meaningfulName("smb", NetworkProtocol.SMB))
    }

    @Test
    fun meaningfulName_normalOrUrlNamed_isTrimmed() {
        assertEquals("My NAS", NetworkDisplayHelper.meaningfulName("My NAS", NetworkProtocol.SMB))
        assertEquals("192.168.124.1", NetworkDisplayHelper.meaningfulName("ftp://192.168.124.1", NetworkProtocol.FTP))
        assertNull(NetworkDisplayHelper.meaningfulName("ftp://", NetworkProtocol.FTP))
    }

    @Test
    fun displaySessionName_protocolDuplicateOrBlank_showsProtocolOnly() {
        assertEquals("FTP", NetworkDisplayHelper.displaySessionName("FTP: ftp"))
        assertEquals("FTP", NetworkDisplayHelper.displaySessionName("ftp: FTP"))
        assertEquals("FTP", NetworkDisplayHelper.displaySessionName("FTP:  "))
        assertEquals("SMB", NetworkDisplayHelper.displaySessionName("SMB: smb"))
    }

    @Test
    fun displaySessionName_urlNamed_stripsUrlPrefix() {
        assertEquals("FTP: 192.168.124.1", NetworkDisplayHelper.displaySessionName("FTP: ftp://192.168.124.1"))
        assertEquals("SMB: nas", NetworkDisplayHelper.displaySessionName("SMB: smb://nas"))
    }

    @Test
    fun displaySessionName_normal_keepsProtocolAndName() {
        assertEquals("FTP: 我的站点", NetworkDisplayHelper.displaySessionName("FTP: 我的站点"))
        assertEquals("SMB: My NAS", NetworkDisplayHelper.displaySessionName("SMB: My NAS"))
    }

    @Test
    fun displaySessionName_nonNetworkName_unchanged() {
        assertEquals("Default", NetworkDisplayHelper.displaySessionName("Default"))
        assertEquals("Local", NetworkDisplayHelper.displaySessionName("Local"))
    }
}
